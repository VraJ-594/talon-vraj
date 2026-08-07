package com.talon.ats.platform.health;

import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

  @GetMapping
  HealthResponse health() {
    return new HealthResponse("UP", "talon-api", Instant.now());
  }

  record HealthResponse(String status, String service, Instant timestamp) {}
}
