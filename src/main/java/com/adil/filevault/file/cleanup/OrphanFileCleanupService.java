package com.adil.filevault.file.cleanup;

import com.adil.filevault.config.FileCleanupProperties;
import com.adil.filevault.file.repository.StoredFileRepository;
import com.adil.filevault.file.storage.FileStorageService;
import com.adil.filevault.file.storage.StorageFileEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrphanFileCleanupService {

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final FileCleanupProperties cleanupProperties;

    public CleanupSummary cleanup() {
        Instant now = Instant.now();

        Instant orphanCutoff = now.minusMillis(
                cleanupProperties
                        .getOrphanGracePeriodMs()
        );

        Instant temporaryCutoff = now.minusMillis(
                cleanupProperties
                        .getTemporaryGracePeriodMs()
        );

        List<StorageFileEntry> permanentCandidates =
                fileStorageService
                        .findPermanentFilesOlderThan(
                                orphanCutoff
                        );

        CleanupCounters counters =
                deletePermanentOrphans(
                        permanentCandidates
                );

        int temporaryFilesDeleted =
                fileStorageService
                        .deleteTemporaryFilesOlderThan(
                                temporaryCutoff
                        );

        return new CleanupSummary(
                permanentCandidates.size(),
                counters.deleted(),
                temporaryFilesDeleted,
                counters.failures()
        );
    }

    private CleanupCounters deletePermanentOrphans(
            List<StorageFileEntry> candidates
    ) {
        int deleted = 0;
        int failures = 0;

        int batchSize =
                cleanupProperties.getBatchSize();

        for (
                int start = 0;
                start < candidates.size();
                start += batchSize
        ) {
            int end = Math.min(
                    start + batchSize,
                    candidates.size()
            );

            List<StorageFileEntry> batch =
                    candidates.subList(start, end);

            List<String> candidatePaths = batch
                    .stream()
                    .map(StorageFileEntry::relativePath)
                    .toList();

            Set<String> existingPaths =
                    new HashSet<>(
                            storedFileRepository
                                    .findExistingRelativePaths(
                                            candidatePaths
                                    )
                    );

            for (StorageFileEntry entry : batch) {
                if (existingPaths.contains(
                        entry.relativePath()
                )) {
                    continue;
                }

                try {
                    fileStorageService.delete(
                            entry.relativePath()
                    );

                    deleted++;

                    log.info(
                            "Deleted orphan file: {}",
                            entry.relativePath()
                    );

                } catch (RuntimeException exception) {
                    failures++;

                    log.error(
                            "Could not delete orphan file: {}",
                            entry.relativePath(),
                            exception
                    );
                }
            }
        }

        return new CleanupCounters(
                deleted,
                failures
        );
    }

    private record CleanupCounters(
            int deleted,
            int failures
    ) {
    }
}