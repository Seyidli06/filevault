package com.adil.filevault.file.storage;

import java.time.Instant;

public record StorageFileEntry(
        String relativePath,
        Instant lastModifiedAt
) {
}