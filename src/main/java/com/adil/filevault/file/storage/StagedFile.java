package com.adil.filevault.file.storage;

import java.nio.file.Path;

public record StagedFile(
        Path path,
        long sizeBytes,
        String sha256
) {
}