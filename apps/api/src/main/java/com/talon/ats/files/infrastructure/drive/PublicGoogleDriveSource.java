package com.talon.ats.files.infrastructure.drive;

import com.talon.ats.files.application.BoundedObjectSink;
import com.talon.ats.files.application.ExternalFileFetchException;
import com.talon.ats.files.application.ExternalFileMetadata;
import com.talon.ats.files.application.ExternalFileSource;
import com.talon.ats.files.application.FetchPolicy;
import com.talon.ats.files.application.SourceReference;
import com.talon.ats.platform.ratelimit.RateLimitPermit;
import com.talon.ats.platform.ratelimit.RateLimiter;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PublicGoogleDriveSource implements ExternalFileSource {

  private static final Pattern FILE_PATH =
      Pattern.compile("^/file/d/([A-Za-z0-9_-]{10,200})(?:/.*)?$");
  private static final Pattern FILE_ID = Pattern.compile("^[A-Za-z0-9_-]{10,200}$");
  private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final String DRIVE_HOST = "drive.google.com";

  private final DriveHttpTransport transport;
  private final HostAddressResolver resolver;
  private final FetchPolicy policy;
  private final RateLimiter limiter;
  private final Clock clock;

  public PublicGoogleDriveSource(FetchPolicy policy, RateLimiter limiter) {
    this(
        new JavaDriveHttpTransport(policy.responseTimeout()),
        host -> List.of(InetAddress.getAllByName(host)),
        policy,
        limiter,
        Clock.systemUTC());
  }

  PublicGoogleDriveSource(
      DriveHttpTransport transport,
      HostAddressResolver resolver,
      FetchPolicy policy,
      RateLimiter limiter,
      Clock clock) {
    this.transport = Objects.requireNonNull(transport);
    this.resolver = Objects.requireNonNull(resolver);
    this.policy = Objects.requireNonNull(policy);
    this.limiter = Objects.requireNonNull(limiter);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public ExternalFileMetadata fetch(SourceReference source, BoundedObjectSink sink) {
    Objects.requireNonNull(source, "source is required");
    Objects.requireNonNull(sink, "sink is required");
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      Future<ExternalFileMetadata> future = executor.submit(() -> fetchRateLimited(source, sink));
      try {
        return future.get(policy.totalTimeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException exception) {
        future.cancel(true);
        throw failure("SOURCE_TIMEOUT", "Drive download timed out", true, null, exception);
      } catch (InterruptedException exception) {
        future.cancel(true);
        Thread.currentThread().interrupt();
        throw failure(
            "SOURCE_INTERRUPTED", "Drive download was interrupted", true, null, exception);
      } catch (ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ExternalFileFetchException fetchException) {
          throw fetchException;
        }
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw failure("SOURCE_UNAVAILABLE", "Drive download failed", true, null, cause);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private ExternalFileMetadata fetchRateLimited(SourceReference source, BoundedObjectSink sink) {
    try (RateLimitPermit ignored = limiter.acquire()) {
      return fetchFollowingRedirects(downloadUri(source.uri()), sink);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("SOURCE_INTERRUPTED", "Drive download was interrupted", true, null, exception);
    }
  }

  private ExternalFileMetadata fetchFollowingRedirects(URI initial, BoundedObjectSink sink) {
    URI current = initial;
    for (int redirects = 0; redirects <= policy.maximumRedirects(); redirects++) {
      validateDestination(current);
      try (DriveHttpResponse response = transport.get(current, policy.responseTimeout())) {
        if (isRedirect(response.status())) {
          if (redirects == policy.maximumRedirects()) {
            throw failure("TOO_MANY_REDIRECTS", "Drive returned too many redirects", true);
          }
          String location =
              response
                  .firstHeader("location")
                  .orElseThrow(
                      () ->
                          failure(
                              "INVALID_SOURCE_RESPONSE",
                              "Drive redirect did not include a destination",
                              true));
          current = current.resolve(location);
          validateRedirect(current);
          continue;
        }
        return handleResponse(response, sink);
      } catch (ExternalFileFetchException exception) {
        throw exception;
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw failure(
            "SOURCE_INTERRUPTED", "Drive download was interrupted", true, null, exception);
      } catch (IOException exception) {
        throw failure("SOURCE_UNAVAILABLE", "Drive download failed", true, null, exception);
      }
    }
    throw failure("TOO_MANY_REDIRECTS", "Drive returned too many redirects", true);
  }

  private ExternalFileMetadata handleResponse(DriveHttpResponse response, BoundedObjectSink sink)
      throws IOException {
    int status = response.status();
    if (status == 401 || status == 403) {
      throw failure("SOURCE_AUTH_REQUIRED", "Drive file is not anonymously downloadable", false);
    }
    if (status == 404 || status == 410) {
      throw failure("SOURCE_NOT_FOUND", "Drive file was not found", false);
    }
    if (status == 429) {
      throw failure(
          "SOURCE_RATE_LIMITED",
          "Drive rate limited the download",
          true,
          retryAfter(response).orElse(null),
          null);
    }
    if (status == 408 || status == 425 || status >= 500) {
      throw failure("SOURCE_UNAVAILABLE", "Drive is temporarily unavailable", true);
    }
    if (status >= 400) {
      throw failure("SOURCE_REJECTED", "Drive rejected the download", false);
    }
    if (status != 200) {
      throw failure("INVALID_SOURCE_RESPONSE", "Drive returned an unsupported response", true);
    }

    String contentType =
        response
            .firstHeader("content-type")
            .orElse("")
            .split(";", 2)[0]
            .trim()
            .toLowerCase(Locale.ROOT);
    if (contentType.equals("text/html") || contentType.equals("application/xhtml+xml")) {
      throw failure(
          "SOURCE_AUTH_REQUIRED", "Drive returned an authentication or confirmation page", false);
    }
    if (!contentType.equals("application/pdf")
        && !contentType.equals("application/octet-stream")
        && !contentType.equals("application/binary")) {
      throw failure("INVALID_FILE_TYPE", "Resume must be a PDF", false);
    }
    response
        .firstHeader("content-length")
        .flatMap(PublicGoogleDriveSource::positiveLong)
        .filter(length -> length > policy.maximumBytes())
        .ifPresent(
            ignored -> {
              throw failure("FILE_TOO_LARGE", "Resume PDF cannot exceed 10 MB", false);
            });

    MaximumInputStream bounded = new MaximumInputStream(response.body(), policy.maximumBytes());
    byte[] signature = bounded.readNBytes(PDF_SIGNATURE.length);
    if (!Arrays.equals(signature, PDF_SIGNATURE)) {
      throw failure("INVALID_FILE_TYPE", "Resume content is not a PDF", false);
    }
    try (InputStream complete =
        new SequenceInputStream(new ByteArrayInputStream(signature), bounded)) {
      long copied = sink.copyFrom(complete, policy.maximumBytes());
      if (copied != bounded.count()) {
        throw failure("INVALID_SINK", "Object sink reported an invalid byte count", true);
      }
      return new ExternalFileMetadata(copied, "application/pdf");
    } catch (MaximumSizeExceededException exception) {
      throw failure("FILE_TOO_LARGE", "Resume PDF cannot exceed 10 MB", false, null, exception);
    }
  }

  private URI downloadUri(URI source) {
    if (!isHttps(source)
        || source.getUserInfo() != null
        || source.getPort() != -1
        || source.getFragment() != null
        || !DRIVE_HOST.equalsIgnoreCase(source.getHost())) {
      throw failure("INVALID_SOURCE_URL", "Source must be a recognized HTTPS Drive link", false);
    }
    String id = extractFileId(source);
    if (id == null || !FILE_ID.matcher(id).matches()) {
      throw failure("INVALID_SOURCE_URL", "Source must be a recognized HTTPS Drive link", false);
    }
    return URI.create(
        "https://drive.google.com/uc?export=download&id="
            + URLEncoder.encode(id, StandardCharsets.UTF_8));
  }

  private static String extractFileId(URI source) {
    Matcher path = FILE_PATH.matcher(Optional.ofNullable(source.getPath()).orElse(""));
    if (path.matches()) {
      return path.group(1);
    }
    if ("/open".equals(source.getPath()) || "/uc".equals(source.getPath())) {
      return queryParameters(source).get("id");
    }
    return null;
  }

  private void validateRedirect(URI destination) {
    if (!isHttps(destination)
        || destination.getUserInfo() != null
        || destination.getPort() != -1
        || destination.getFragment() != null
        || !isAllowedGoogleHost(destination.getHost())) {
      throw failure("SOURCE_NETWORK_REJECTED", "Drive redirect destination was rejected", false);
    }
  }

  private void validateDestination(URI destination) {
    validateRedirect(destination);
    List<InetAddress> addresses;
    try {
      addresses = resolver.resolve(destination.getHost());
    } catch (IOException exception) {
      throw failure(
          "SOURCE_UNAVAILABLE", "Drive host could not be resolved", true, null, exception);
    }
    if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublic(address))) {
      throw failure(
          "SOURCE_NETWORK_REJECTED", "Drive destination resolved to a blocked address", false);
    }
  }

  private static boolean isAllowedGoogleHost(String host) {
    if (host == null) {
      return false;
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    return normalized.equals(DRIVE_HOST)
        || normalized.equals("drive.usercontent.google.com")
        || normalized.endsWith(".googleusercontent.com")
        || normalized.endsWith(".usercontent.google.com");
  }

  private static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      return first != 0
          && !(first == 100 && second >= 64 && second <= 127)
          && !(first == 192 && second == 0)
          && !(first == 198 && (second == 18 || second == 19))
          && !(first == 198 && second == 51 && Byte.toUnsignedInt(bytes[2]) == 100)
          && !(first == 203 && second == 0 && Byte.toUnsignedInt(bytes[2]) == 113)
          && first < 224;
    }
    if (address instanceof Inet6Address) {
      int first = Byte.toUnsignedInt(bytes[0]);
      int second = Byte.toUnsignedInt(bytes[1]);
      boolean uniqueLocal = (first & 0xFE) == 0xFC;
      boolean documentation =
          first == 0x20
              && second == 0x01
              && Byte.toUnsignedInt(bytes[2]) == 0x0D
              && Byte.toUnsignedInt(bytes[3]) == 0xB8;
      return !uniqueLocal && !documentation;
    }
    return false;
  }

  private Optional<Duration> retryAfter(DriveHttpResponse response) {
    return response.firstHeader("retry-after").flatMap(this::parseRetryAfter);
  }

  private Optional<Duration> parseRetryAfter(String value) {
    try {
      long seconds = Long.parseLong(value.trim());
      return Optional.of(Duration.ofSeconds(Math.max(0, seconds)));
    } catch (NumberFormatException ignored) {
      try {
        Instant retryAt =
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        return Optional.of(
            Duration.between(clock.instant(), retryAt).isNegative()
                ? Duration.ZERO
                : Duration.between(clock.instant(), retryAt));
      } catch (DateTimeParseException invalidDate) {
        return Optional.empty();
      }
    }
  }

  private static Map<String, String> queryParameters(URI uri) {
    String query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return Map.of();
    }
    Map<String, String> values = new java.util.LinkedHashMap<>();
    for (String pair : query.split("&")) {
      String[] parts = pair.split("=", 2);
      String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
      values.putIfAbsent(key, value);
    }
    return values;
  }

  private static Optional<Long> positiveLong(String value) {
    try {
      long parsed = Long.parseLong(value);
      return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private static boolean isHttps(URI uri) {
    return "https".equalsIgnoreCase(uri.getScheme());
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }

  private static ExternalFileFetchException failure(
      String code, String message, boolean retryable) {
    return new ExternalFileFetchException(code, message, retryable);
  }

  private static ExternalFileFetchException failure(
      String code, String message, boolean retryable, Duration retryAfter, Throwable cause) {
    return new ExternalFileFetchException(code, message, retryable, retryAfter, cause);
  }

  private static final class MaximumInputStream extends FilterInputStream {

    private final long maximum;
    private long count;

    private MaximumInputStream(InputStream input, long maximum) {
      super(input);
      this.maximum = maximum;
    }

    long count() {
      return count;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value != -1 && ++count > maximum) {
        throw new MaximumSizeExceededException();
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      long remainingIncludingProbe = maximum - count + 1;
      int allowed = (int) Math.min(length, Math.max(1L, remainingIncludingProbe));
      int read = super.read(buffer, offset, allowed);
      if (read > 0 && (count += read) > maximum) {
        throw new MaximumSizeExceededException();
      }
      return read;
    }
  }

  private static final class MaximumSizeExceededException extends IOException {}
}
