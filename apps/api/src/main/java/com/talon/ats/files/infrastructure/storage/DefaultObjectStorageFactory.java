package com.talon.ats.files.infrastructure.storage;

import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ObjectStorageFactory;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class DefaultObjectStorageFactory implements ObjectStorageFactory {

  @Override
  public ObjectStorage local(Path root) {
    return new LocalObjectStorage(root);
  }

  @Override
  public ObjectStorage s3(String bucket, String region) {
    Region awsRegion = Region.of(region);
    S3Client client = S3Client.builder().region(awsRegion).build();
    S3Presigner presigner = S3Presigner.builder().region(awsRegion).build();
    return new S3ObjectStorage(bucket, client, presigner);
  }
}
