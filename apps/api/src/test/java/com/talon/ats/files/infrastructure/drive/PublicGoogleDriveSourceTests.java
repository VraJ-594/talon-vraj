package com.talon.ats.files.infrastructure.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talon.ats.files.application.BoundedObjectSink;
import com.talon.ats.files.application.ExternalFileFetchException;
import com.talon.ats.files.application.ExternalFileMetadata;
import com.talon.ats.files.application.FetchPolicy;
import com.talon.ats.files.application.SourceReference;
import com.talon.ats.platform.ratelimit.RateLimitPermit;
import com.talon.ats.platform.ratelimit.RateLimiter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicGoogleDriveSourceTests {

  private static final String FILE_ID = "1AbCdEfGhIjKlMnOpQrStUvWxYz";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void acceptsRecognizedPublicShareLinksAndStreamsPdfWithoutLeakingSourceUrl() throws Exception {
    for (String source :
        List.of(
            "https://drive.google.com/file/d/" + FILE_ID + "/view?usp=drive_link",
            "https://drive.google.com/open?id=" + FILE_ID,
            "https://drive.google.com/uc?export=download&id=" + FILE_ID)) {
      StubTransport transport = new StubTransport(pdfResponse("%PDF-demo"));
      ByteArrayOutputStream stored = new ByteArrayOutputStream();
      PublicGoogleDriveSource drive = source(transport, publicResolver(), defaults());

      ExternalFileMetadata metadata =
          drive.fetch(new SourceReference(URI.create(source)), copyingSink(stored));

      assertThat(stored.toString(StandardCharsets.US_ASCII)).isEqualTo("%PDF-demo");
      assertThat(metadata.sizeBytes()).isEqualTo(9);
      assertThat(metadata.contentType()).isEqualTo("application/pdf");
      assertThat(metadata)
          .hasToString("ExternalFileMetadata[sizeBytes=9, contentType=application/pdf]");
      assertThat(transport.requested())
          .singleElement()
          .satisfies(
              request -> {
                assertThat(request.getScheme()).isEqualTo("https");
                assertThat(request.getHost()).isEqualTo("drive.google.com");
                assertThat(request.getPath()).isEqualTo("/uc");
                assertThat(request.getQuery()).contains("export=download", "id=" + FILE_ID);
              });
    }
  }

  @Test
  void rejectsNonHttpsUnknownHostsAndUnrecognizedDrivePathsBeforeNetworkAccess() {
    for (String source :
        List.of(
            "http://drive.google.com/file/d/" + FILE_ID + "/view",
            "https://evil.example/file/d/" + FILE_ID + "/view",
            "https://drive.google.com/drive/folders/" + FILE_ID)) {
      StubTransport transport = new StubTransport();
      PublicGoogleDriveSource drive = source(transport, publicResolver(), defaults());

      assertFailure(drive, source, "INVALID_SOURCE_URL", false);
      assertThat(transport.requested()).isEmpty();
    }
  }

  @Test
  void validatesRedirectDestinationsAndResolvedAddressesBeforeEveryRequest() throws Exception {
    StubTransport redirected =
        new StubTransport(
            response(302, Map.of("location", List.of("https://evil.example/resume.pdf")), ""));
    PublicGoogleDriveSource redirectSource = source(redirected, publicResolver(), defaults());
    assertFailure(
        redirectSource,
        "https://drive.google.com/file/d/" + FILE_ID + "/view",
        "SOURCE_NETWORK_REJECTED",
        false);
    assertThat(redirected.requested()).hasSize(1);

    StubTransport privateTarget = new StubTransport(pdfResponse("%PDF-demo"));
    PublicGoogleDriveSource privateSource =
        source(
            privateTarget,
            host -> List.of(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})),
            defaults());
    assertFailure(
        privateSource,
        "https://drive.google.com/file/d/" + FILE_ID + "/view",
        "SOURCE_NETWORK_REJECTED",
        false);
    assertThat(privateTarget.requested()).isEmpty();
  }

  @Test
  void followsOnlyAllowlistedGoogleRedirects() throws Exception {
    StubTransport transport =
        new StubTransport(
            response(
                302,
                Map.of(
                    "location",
                    List.of("https://drive.usercontent.google.com/download?id=" + FILE_ID)),
                ""),
            pdfResponse("%PDF-demo"));

    ExternalFileMetadata result =
        source(transport, publicResolver(), defaults())
            .fetch(
                new SourceReference(
                    URI.create("https://drive.google.com/file/d/" + FILE_ID + "/view")),
                copyingSink(new ByteArrayOutputStream()));

    assertThat(result.sizeBytes()).isEqualTo(9);
    assertThat(transport.requested()).hasSize(2);
  }

  @Test
  void treatsHtmlAndPermissionResponsesAsNonRetryableAuthenticationFailures() {
    for (DriveHttpResponse response :
        List.of(
            response(200, Map.of("content-type", List.of("text/html")), "<html>login</html>"),
            response(401, Map.of(), ""),
            response(403, Map.of(), ""))) {
      PublicGoogleDriveSource drive =
          source(new StubTransport(response), publicResolver(), defaults());
      assertFailure(
          drive,
          "https://drive.google.com/file/d/" + FILE_ID + "/view",
          "SOURCE_AUTH_REQUIRED",
          false);
    }
  }

  @Test
  void classifiesProviderFailuresAndHonorsRetryAfter() {
    PublicGoogleDriveSource limited =
        source(
            new StubTransport(response(429, Map.of("retry-after", List.of("7")), "")),
            publicResolver(),
            defaults());
    assertThatThrownBy(() -> limited.fetch(reference(), copyingSink(new ByteArrayOutputStream())))
        .isInstanceOfSatisfying(
            ExternalFileFetchException.class,
            failure -> {
              assertThat(failure.code()).isEqualTo("SOURCE_RATE_LIMITED");
              assertThat(failure.retryable()).isTrue();
              assertThat(failure.retryAfter()).contains(Duration.ofSeconds(7));
            });

    assertFailure(
        source(new StubTransport(response(404, Map.of(), "")), publicResolver(), defaults()),
        reference().uri().toString(),
        "SOURCE_NOT_FOUND",
        false);
    assertFailure(
        source(new StubTransport(response(503, Map.of(), "")), publicResolver(), defaults()),
        reference().uri().toString(),
        "SOURCE_UNAVAILABLE",
        true);
  }

  @Test
  void enforcesContentLengthStreamingLimitMediaTypeAndPdfSignature() {
    FetchPolicy sixteenBytes = new FetchPolicy(16, 5, Duration.ofSeconds(2), Duration.ofSeconds(5));
    assertFailure(
        source(
            new StubTransport(
                response(
                    200,
                    Map.of(
                        "content-type", List.of("application/pdf"),
                        "content-length", List.of("17")),
                    "%PDF-short")),
            publicResolver(),
            sixteenBytes),
        reference().uri().toString(),
        "FILE_TOO_LARGE",
        false);

    assertFailure(
        source(
            new StubTransport(pdfResponse("%PDF-0123456789012")), publicResolver(), sixteenBytes),
        reference().uri().toString(),
        "FILE_TOO_LARGE",
        false);

    assertFailure(
        source(
            new StubTransport(
                response(200, Map.of("content-type", List.of("image/png")), "%PDF-demo")),
            publicResolver(),
            defaults()),
        reference().uri().toString(),
        "INVALID_FILE_TYPE",
        false);

    assertFailure(
        source(new StubTransport(pdfResponse("not-a-pdf")), publicResolver(), defaults()),
        reference().uri().toString(),
        "INVALID_FILE_TYPE",
        false);
  }

  @Test
  void acquiresAndReleasesOneLimiterPermitForTheWholeFetch() throws Exception {
    RecordingLimiter limiter = new RecordingLimiter();
    PublicGoogleDriveSource drive =
        new PublicGoogleDriveSource(
            new StubTransport(pdfResponse("%PDF-demo")),
            publicResolver(),
            defaults(),
            limiter,
            CLOCK);

    drive.fetch(reference(), copyingSink(new ByteArrayOutputStream()));

    assertThat(limiter.acquired).isEqualTo(1);
    assertThat(limiter.released).isEqualTo(1);
  }

  @Test
  void doesNotReadTheHttpBodyAgainAfterTheSinkConsumesAndClosesIt() {
    InputStream closeStrict =
        new FilterInputStream(
            new ByteArrayInputStream("%PDF-demo".getBytes(StandardCharsets.US_ASCII))) {
          private boolean closed;

          @Override
          public int read() throws IOException {
            if (closed) {
              throw new IOException("read after close");
            }
            return super.read();
          }

          @Override
          public int read(byte[] buffer, int offset, int length) throws IOException {
            if (closed) {
              throw new IOException("read after close");
            }
            return super.read(buffer, offset, length);
          }

          @Override
          public void close() throws IOException {
            closed = true;
            super.close();
          }
        };
    DriveHttpResponse response =
        new DriveHttpResponse(200, Map.of("content-type", List.of("application/pdf")), closeStrict);

    ExternalFileMetadata metadata =
        source(new StubTransport(response), publicResolver(), defaults())
            .fetch(reference(), copyingSink(new ByteArrayOutputStream()));

    assertThat(metadata.sizeBytes()).isEqualTo(9);
  }

  @Test
  void cancelsAStalledDownloadAtTheTotalOperationDeadline() {
    DriveHttpTransport stalled =
        (uri, timeout) -> {
          Thread.sleep(Duration.ofSeconds(10));
          throw new IOException("unreachable");
        };
    FetchPolicy shortDeadline =
        new FetchPolicy(1024, 1, Duration.ofMillis(50), Duration.ofMillis(50));
    PublicGoogleDriveSource drive =
        new PublicGoogleDriveSource(
            stalled, publicResolver(), shortDeadline, () -> RateLimitPermit.noop(), CLOCK);

    assertFailure(drive, reference().uri().toString(), "SOURCE_TIMEOUT", true);
  }

  private static PublicGoogleDriveSource source(
      StubTransport transport, HostAddressResolver resolver, FetchPolicy policy) {
    return new PublicGoogleDriveSource(
        transport, resolver, policy, () -> RateLimitPermit.noop(), CLOCK);
  }

  private static SourceReference reference() {
    return new SourceReference(URI.create("https://drive.google.com/file/d/" + FILE_ID + "/view"));
  }

  private static FetchPolicy defaults() {
    return FetchPolicy.publicDriveDefaults();
  }

  private static HostAddressResolver publicResolver() {
    return host -> List.of(InetAddress.getByAddress(new byte[] {8, 8, 8, 8}));
  }

  private static BoundedObjectSink copyingSink(ByteArrayOutputStream target) {
    return (input, maximumBytes) -> {
      byte[] buffer = new byte[4];
      long copied = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        target.write(buffer, 0, read);
        copied += read;
      }
      return copied;
    };
  }

  private static DriveHttpResponse pdfResponse(String body) {
    return response(200, Map.of("content-type", List.of("application/pdf")), body);
  }

  private static DriveHttpResponse response(
      int status, Map<String, List<String>> headers, String body) {
    return new DriveHttpResponse(
        status, headers, new ByteArrayInputStream(body.getBytes(StandardCharsets.US_ASCII)));
  }

  private static void assertFailure(
      PublicGoogleDriveSource source, String url, String code, boolean retryable) {
    assertThatThrownBy(
            () ->
                source.fetch(
                    new SourceReference(URI.create(url)), copyingSink(new ByteArrayOutputStream())))
        .isInstanceOfSatisfying(
            ExternalFileFetchException.class,
            failure -> {
              assertThat(failure.code()).isEqualTo(code);
              assertThat(failure.retryable()).isEqualTo(retryable);
            });
  }

  private static final class StubTransport implements DriveHttpTransport {

    private final ArrayDeque<DriveHttpResponse> responses = new ArrayDeque<>();
    private final List<URI> requested = new ArrayList<>();

    private StubTransport(DriveHttpResponse... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public DriveHttpResponse get(URI uri, Duration timeout) throws IOException {
      requested.add(uri);
      if (responses.isEmpty()) {
        throw new IOException("unexpected request");
      }
      return responses.removeFirst();
    }

    private List<URI> requested() {
      return List.copyOf(requested);
    }
  }

  private static final class RecordingLimiter implements RateLimiter {

    private int acquired;
    private int released;

    @Override
    public RateLimitPermit acquire() {
      acquired++;
      return () -> released++;
    }
  }
}
