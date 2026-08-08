package com.talon.ats.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talon.ats.files.application.ObjectSizeLimitException;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import com.talon.ats.files.infrastructure.storage.S3ObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStorageTests {

  private final S3Client client = mock(S3Client.class);
  private final S3Presigner presigner = mock(S3Presigner.class);
  private final S3ObjectStorage storage =
      new S3ObjectStorage("talon-resumes-demo-vraj", client, presigner);

  @Test
  void uploadsOnlyBoundedObjectsWithServerSideEncryption() {
    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    PrivateObjectKey key = PrivateObjectKey.importSource(UUID.randomUUID(), UUID.randomUUID());
    byte[] payload = "first,last".getBytes(StandardCharsets.UTF_8);

    StoredObject stored = storage.put(key, new ByteArrayInputStream(payload), payload.length);

    assertThat(stored.sizeBytes()).isEqualTo(payload.length);
    assertThat(stored.sha256Hex()).hasSize(64);
    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("talon-resumes-demo-vraj");
    assertThat(request.getValue().key()).isEqualTo(key.value());
    assertThat(request.getValue().serverSideEncryptionAsString()).isEqualTo("AES256");
    assertThat(request.getValue().contentType()).isEqualTo("text/csv");
  }

  @Test
  void rejectsOversizedObjectsBeforeCallingS3() {
    PrivateObjectKey key = PrivateObjectKey.importSource(UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> storage.put(key, new ByteArrayInputStream(new byte[] {1, 2}), 1))
        .isInstanceOf(ObjectSizeLimitException.class);
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void promotesOnlyTheExactQuarantineObjectThenDeletesTheSource() {
    UUID workspaceId = UUID.randomUUID();
    PrivateObjectKey quarantine =
        PrivateObjectKey.quarantineResume(workspaceId, UUID.randomUUID(), UUID.randomUUID());

    storage.promote(quarantine, quarantine.cleanResumeKey());

    ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
    ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    InOrder order = inOrder(client);
    order.verify(client).copyObject(copy.capture());
    order.verify(client).deleteObject(delete.capture());
    assertThat(copy.getValue().bucket()).isEqualTo("talon-resumes-demo-vraj");
    assertThat(copy.getValue().copySource())
        .isEqualTo("talon-resumes-demo-vraj/" + quarantine.value());
    assertThat(copy.getValue().key()).isEqualTo(quarantine.cleanResumeKey().value());
    assertThat(copy.getValue().serverSideEncryptionAsString()).isEqualTo("AES256");
    assertThat(delete.getValue().bucket()).isEqualTo("talon-resumes-demo-vraj");
    assertThat(delete.getValue().key()).isEqualTo(quarantine.value());
  }

  @Test
  void presignsOnlyAnExactCleanResumeForFiveMinuteBrowserAccess() {
    UUID workspaceId = UUID.randomUUID();
    PrivateObjectKey clean =
        PrivateObjectKey.cleanResume(workspaceId, UUID.randomUUID(), UUID.randomUUID());
    try (S3Presigner realPresigner =
        S3Presigner.builder()
            .region(Region.AP_SOUTH_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build()) {
      S3ObjectStorage realStorage =
          new S3ObjectStorage("talon-resumes-demo-vraj", client, realPresigner);

      var signed =
          realStorage
              .presignCleanDownload(clean, Duration.ofMinutes(5), "candidate-resume.pdf")
              .orElseThrow();

      assertThat(signed.getScheme()).isEqualTo("https");
      assertThat(signed.getPath()).endsWith(clean.value());
      assertThat(signed.getRawQuery()).contains("X-Amz-Signature");
      assertThat(signed.toString()).doesNotContain("quarantine/");
    }
  }

  @Test
  void refusesToPresignQuarantineObjects() {
    PrivateObjectKey quarantine =
        PrivateObjectKey.quarantineResume(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(
            () ->
                storage.presignCleanDownload(
                    quarantine, Duration.ofMinutes(5), "candidate-resume.pdf"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
