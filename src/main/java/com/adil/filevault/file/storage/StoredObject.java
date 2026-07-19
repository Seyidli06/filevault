package com.adil.filevault.file.storage;

public record StoredObject(
        String storedFilename,
        String relativePath,
        long sizeBytes,
        String sha256
) {
}