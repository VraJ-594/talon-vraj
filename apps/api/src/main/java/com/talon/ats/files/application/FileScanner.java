package com.talon.ats.files.application;

import java.io.InputStream;

@FunctionalInterface
public interface FileScanner {

  FileScanVerdict scan(InputStream input);
}
