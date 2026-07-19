package com.adil.filevault.file.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "filevault.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrphanFileCleanupScheduler {

    private final OrphanFileCleanupService cleanupService;

    @Scheduled(
            initialDelayString =
                    "${filevault.cleanup.initial-delay-ms:60000}",

            fixedDelayString =
                    "${filevault.cleanup.fixed-delay-ms:21600000}"
    )
    public void runCleanup() {
        log.info("Starting orphan file cleanup");

        try {
            CleanupSummary summary =
                    cleanupService.cleanup();

            log.info(
                    """
                    Orphan cleanup finished. \
                    Candidates: {}, \
                    orphan files deleted: {}, \
                    temporary files deleted: {}, \
                    failures: {}
                    """,
                    summary.permanentCandidates(),
                    summary.orphanFilesDeleted(),
                    summary.temporaryFilesDeleted(),
                    summary.failures()
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Orphan file cleanup failed",
                    exception
            );
        }
    }
}