package com.talon.ats.files.application;

public interface ExternalFileSource {

  ExternalFileMetadata fetch(SourceReference source, BoundedObjectSink sink);
}
