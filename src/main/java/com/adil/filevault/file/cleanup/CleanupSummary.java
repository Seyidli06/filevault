package com.adil.filevault.file.cleanup;

public record CleanupSummary(
        int permanentCandidates,
        int orphanFilesDeleted,
        int temporaryFilesDeleted,
        int failures
) {
}