package com.talon.ats.files.infrastructure.drive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JavaDriveHttpTransport implements DriveHttpTransport {

  private final HttpClient client;

  JavaDriveHttpTransport(Duration connectTimeout) {
    client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public DriveHttpResponse get(URI uri, Duration timeout) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .GET()
            .timeout(timeout)
            .header("Accept", "application/pdf")
            .header("User-Agent", "Talon-ATS-Resume-Importer/1.0")
            .build();
    HttpResponse<java.io.InputStream> response =
        client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    return new DriveHttpResponse(response.statusCode(), response.headers().map(), response.body());
  }
}
