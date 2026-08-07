package com.talon.ats.files.application;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface BoundedObjectSink {

  long copyFrom(InputStream input, long maximumBytes) throws IOException;
}
