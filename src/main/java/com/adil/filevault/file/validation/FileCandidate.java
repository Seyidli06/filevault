package com.adil.filevault.file.validation;

public record FileCandidate(
        String originalFilename,
        String extension,
        String expectedMediaType,
        String clientMediaType
) {
}