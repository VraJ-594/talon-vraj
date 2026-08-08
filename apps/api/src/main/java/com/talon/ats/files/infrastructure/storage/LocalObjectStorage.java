package com.talon.ats.files.infrastructure.storage;

import com.talon.ats.files.application.ObjectSizeLimitException;
import com.talon.ats.files.application.ObjectStorage;
import com.talon.ats.files.application.PrivateObjectKey;
import com.talon.ats.files.application.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class LocalObjectStorage implements ObjectStorage {

  private static final int COPY_BUFFER_BYTES = 64 * 1024;

  private final Path root;

  public LocalObjectStorage(Path root) {
    this.root = Objects.requireNonNull(root, "root is required").toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException exception) {
      throw new UncheckedIOException("could not create private object root", exception);
    }
  }

  @Override
  public StoredObject put(PrivateObjectKey key, InputStream input, long maximumBytes) {
    Objects.requireNonNull(input, "input is required");
    if (maximumBytes <= 0) {
      throw new IllegalArgumentException("maximumBytes must be positive");
    }
    Path target = target(key);
    Path temporary = null;
    try {
      Files.createDirectories(target.getParent());
      temporary = Files.createTempFile(target.getParent(), ".talon-upload-", ".tmp");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long size = copy(input, temporary, maximumBytes, digest);
      move(temporary, target);
      temporary = null;
      return new StoredObject(key, size, HexFormat.of().formatHex(digest.digest()));
    } catch (ObjectSizeLimitException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new UncheckedIOException("private object write failed", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    } finally {
      deleteTemporary(temporary);
    }
  }

  @Override
  public InputStream open(PrivateObjectKey key) {
    try {
      return Files.newInputStream(target(key));
    } catch (IOException exception) {
      throw new UncheckedIOException("private object read failed", exception);
    }
  }

  @Override
  public boolean exists(PrivateObjectKey key) {
    return Files.isRegularFile(target(key));
  }

  @Override
  public void delete(PrivateObjectKey key) {
    try {
      Files.deleteIfExists(target(key));
    } catch (IOException exception) {
      throw new UncheckedIOException("private object deletion failed", exception);
    }
  }

  @Override
  public void promote(PrivateObjectKey quarantine, PrivateObjectKey clean) {
    Objects.requireNonNull(quarantine, "quarantine key is required");
    Objects.requireNonNull(clean, "clean key is required");
    if (!quarantine.isQuarantineResume() || !quarantine.cleanResumeKey().equals(clean)) {
      throw new IllegalArgumentException(
          "promotion requires matching quarantine and clean resume keys");
    }
    Path source = target(quarantine);
    Path destination = target(clean);
    try {
      Files.createDirectories(destination.getParent());
      move(source, destination);
    } catch (IOException exception) {
      throw new UncheckedIOException("private object promotion failed", exception);
    }
  }

  private long copy(InputStream input, Path temporary, long maximumBytes, MessageDigest digest)
      throws IOException {
    long count = 0;
    byte[] buffer = new byte[COPY_BUFFER_BYTES];
    try (OutputStream output = Files.newOutputStream(temporary)) {
      int read;
      while ((read = input.read(buffer)) != -1) {
        if (count + read > maximumBytes) {
          throw new ObjectSizeLimitException("private object exceeded its maximum size");
        }
        output.write(buffer, 0, read);
        digest.update(buffer, 0, read);
        count += read;
      }
    }
    return count;
  }

  private Path target(PrivateObjectKey key) {
    Objects.requireNonNull(key, "key is required");
    Path target = root.resolve(key.value().replace('/', java.io.File.separatorChar)).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException("object key escaped the private root");
    }
    return target;
  }

  private static void move(Path source, Path destination) throws IOException {
    try {
      Files.move(
          source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteTemporary(Path temporary) {
    if (temporary == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException ignored) {
      // Lifecycle cleanup can remove an exceptional local-development orphan.
    }
  }
}
