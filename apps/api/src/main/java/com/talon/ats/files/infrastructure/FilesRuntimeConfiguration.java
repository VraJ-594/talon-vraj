package com.talon.ats.files.infrastructure;

import com.talon.ats.files.application.ExternalFileSource;
import com.talon.ats.files.application.FetchPolicy;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.ResumeTransferService;
import com.talon.ats.files.infrastructure.drive.PublicGoogleDriveSource;
import com.talon.ats.platform.ratelimit.LeakyBucket;
import com.talon.ats.platform.ratelimit.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "talon.security.enabled", havingValue = "true")
public class FilesRuntimeConfiguration {

  @Bean
  RateLimiter publicDriveRateLimiter() {
    return new LeakyBucket(5, 5, 5);
  }

  @Bean
  ExternalFileSource publicDriveSource(RateLimiter publicDriveRateLimiter) {
    return new PublicGoogleDriveSource(FetchPolicy.publicDriveDefaults(), publicDriveRateLimiter);
  }

  @Bean
  ResumeTransferService resumeTransferService(
      ExternalFileSource publicDriveSource, ObjectStorage storage) {
    return new ResumeTransferService(publicDriveSource, storage);
  }
}
