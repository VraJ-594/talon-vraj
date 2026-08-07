package com.talon.ats.files.infrastructure.drive;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record DriveHttpResponse(int status, Map<String, List<String>> headers, InputStream body)
    implements AutoCloseable {

  DriveHttpResponse {
    headers = Map.copyOf(headers);
  }

  Optional<String> firstHeader(String name) {
    return headers.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst();
  }

  @Override
  public void close() throws IOException {
    body.close();
  }
}
