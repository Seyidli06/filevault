package com.adil.filevault.file.cleanup;

import com.adil.filevault.config.FileCleanupProperties;
import com.adil.filevault.file.repository.StoredFileRepository;
import com.adil.filevault.file.storage.FileStorageService;
import com.adil.filevault.file.storage.StorageFileEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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

        int batchSize =
                cleanupProperties.getBatchSize();

        CleanupAccumulator accumulator =
                new CleanupAccumulator();

        List<StorageFileEntry> batch =
                new ArrayList<>(batchSize);

        /*
         * Filesystem stream olunur.
         *
         * Bütün namizədlər RAM-a yığılmır.
         * Yalnız konfiqurasiya edilmiş batch ölçüsü
         * qədər entry yaddaşda saxlanılır.
         */
        fileStorageService
                .forEachPermanentFileOlderThan(
                        orphanCutoff,
                        entry -> {
                            accumulator.permanentCandidates++;

                            batch.add(entry);

                            if (batch.size() >= batchSize) {
                                processBatch(
                                        batch,
                                        accumulator
                                );

                                batch.clear();
                            }
                        }
                );


        if (!batch.isEmpty()) {
            processBatch(
                    batch,
                    accumulator
            );

            batch.clear();
        }

        int temporaryFilesDeleted =
                fileStorageService
                        .deleteTemporaryFilesOlderThan(
                                temporaryCutoff
                        );

        return new CleanupSummary(
                accumulator.permanentCandidates,
                accumulator.deleted,
                temporaryFilesDeleted,
                accumulator.failures
        );
    }

    private void processBatch(
            List<StorageFileEntry> batch,
            CleanupAccumulator accumulator
    ) {
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

                accumulator.deleted++;


                log.debug(
                        "Deleted orphan file: {}",
                        entry.relativePath()
                );

            } catch (RuntimeException exception) {
                accumulator.failures++;

                log.error(
                        "Could not delete orphan file: {}",
                        entry.relativePath(),
                        exception
                );
            }
        }
    }

    private static final class CleanupAccumulator {

        private int permanentCandidates;
        private int deleted;
        private int failures;
    }
}