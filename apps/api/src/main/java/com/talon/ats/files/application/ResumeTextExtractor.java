package com.talon.ats.files.application;

import java.io.InputStream;

@FunctionalInterface
public interface ResumeTextExtractor {

  ExtractedResumeText extract(InputStream input);
}
