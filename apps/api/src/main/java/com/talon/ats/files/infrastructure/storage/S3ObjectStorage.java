package com.talon.ats.files.infrastructure.storage;

import com.talon.ats.files.application.ObjectSizeLimitException;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public final class S3ObjectStorage implements ObjectStorage, AutoCloseable {

  private static final int COPY_BUFFER_BYTES = 64 * 1024;
  private static final Logger LOGGER = LoggerFactory.getLogger(S3ObjectStorage.class);

  private final String bucket;
  private final S3Client client;
  private final S3Presigner presigner;

  public S3ObjectStorage(String bucket, S3Client client, S3Presigner presigner) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("private S3 bucket is required");
    }
    this.bucket = bucket.trim();
    this.client = Objects.requireNonNull(client);
    this.presigner = Objects.requireNonNull(presigner);
  }

  @Override
  public StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(input);
    if (maximumBytes <= 0) {
      throw new IllegalArgumentException("maximumBytes must be positive");
    }
    Path temporary = null;
    try {
      temporary = Files.createTempFile("talon-s3-upload-", ".tmp");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long size = copy(input, temporary, maximumBytes, digest);
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key.value())
              .contentType(contentType(key))
              .serverSideEncryption(ServerSideEncryption.AES256)
              .build(),
          RequestBody.fromFile(temporary));
      return new StoredObject(key, size, HexFormat.of().formatHex(digest.digest()));
    } catch (ObjectSizeLimitException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new UncheckedIOException("private S3 staging failed", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    } finally {
      deleteTemporary(temporary);
    }
  }

  @Override
  public InputStream open(PrivateObjectKey key) {
    Objects.requireNonNull(key);
    return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key.value()).build());
  }

  @Override
  public boolean exists(PrivateObjectKey key) {
    Objects.requireNonNull(key);
    try {
      client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key.value()).build());
      return true;
    } catch (NoSuchKeyException exception) {
      return false;
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) return false;
      throw exception;
    }
  }

  @Override
  public void delete(PrivateObjectKey key) {
    Objects.requireNonNull(key);
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key.value()).build());
  }

  @Override
  public void promote(PrivateObjectKey quarantine, PrivateObjectKey clean) {
    Objects.requireNonNull(quarantine);
    Objects.requireNonNull(clean);
    if (!quarantine.isQuarantineResume() || !quarantine.cleanResumeKey().equals(clean)) {
      throw new IllegalArgumentException(
          "promotion requires matching quarantine and clean resume keys");
    }
    client.copyObject(
        CopyObjectRequest.builder()
            .bucket(bucket)
            .copySource(bucket + "/" + quarantine.value())
            .key(clean.value())
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build());
    delete(quarantine);
  }

  @Override
  public Optional<URI> presignCleanDownload(
      PrivateObjectKey clean, Duration lifetime, String downloadFileName) {
    Objects.requireNonNull(clean);
    Objects.requireNonNull(lifetime);
    if (!clean.isCleanResume()) {
      throw new IllegalArgumentException("only clean resumes can be presigned");
    }
    if (lifetime.isZero()
        || lifetime.isNegative()
        || lifetime.compareTo(Duration.ofMinutes(5)) > 0) {
      throw new IllegalArgumentException("clean resume access must expire within five minutes");
    }
    if (downloadFileName == null || !downloadFileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")) {
      throw new IllegalArgumentException("download file name is invalid");
    }
    GetObjectRequest request =
        GetObjectRequest.builder()
            .bucket(bucket)
            .key(clean.value())
            .responseContentType("application/pdf")
            .responseContentDisposition("inline; filename=\"" + downloadFileName + "\"")
            .build();
    return Optional.of(
        URI.create(
            presigner
                .presignGetObject(
                    GetObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .getObjectRequest(request)
                        .build())
                .url()
                .toString()));
  }

  @Override
  public void close() {
    presigner.close();
    client.close();
  }

  private static long copy(
      InputStream input, Path temporary, long maximumBytes, MessageDigest digest)
      throws IOException {
    long count = 0;
    byte[] buffer = new byte[COPY_BUFFER_BYTES];
    try (OutputStream output = Files.newOutputStream(temporary)) {
      int read;
      while ((read = input.read(buffer)) != -1) {
        if (count + read > maximumBytes) {
          throw new ObjectSizeLimitException("private object exceeded its maximum size");
        }
        output.write(buffer, 0, read);
        digest.update(buffer, 0, read);
        count += read;
      }
    }
    return count;
  }

  private static String contentType(PrivateObjectKey key) {
    return key.isQuarantineResume() || key.isCleanResume() ? "application/pdf" : "text/csv";
  }

  private static void deleteTemporary(Path temporary) {
    if (temporary == null) return;
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException exception) {
      LOGGER.warn(
          "Private S3 staging-file cleanup failed ({})", exception.getClass().getSimpleName());
    }
  }
}
