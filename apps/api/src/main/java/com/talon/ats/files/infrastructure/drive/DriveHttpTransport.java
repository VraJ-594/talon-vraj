package com.talon.ats.files.infrastructure.drive;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

interface DriveHttpTransport {

  DriveHttpResponse get(URI uri, Duration timeout) throws IOException, InterruptedException;
}
