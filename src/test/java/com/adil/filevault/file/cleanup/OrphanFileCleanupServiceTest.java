package com.adil.filevault.file.cleanup;

import com.adil.filevault.config.FileCleanupProperties;
import com.adil.filevault.file.repository.StoredFileRepository;
import com.adil.filevault.file.storage.FileStorageService;
import com.adil.filevault.file.storage.StorageFileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileCleanupProperties cleanupProperties;

    @InjectMocks
    private OrphanFileCleanupService cleanupService;

    @Test
    void cleanupShouldProcessPermanentFilesInBatches() {

        when(
                cleanupProperties
                        .getOrphanGracePeriodMs()
        ).thenReturn(0L);

        when(
                cleanupProperties
                        .getTemporaryGracePeriodMs()
        ).thenReturn(0L);


        when(
                cleanupProperties.getBatchSize()
        ).thenReturn(2);

        List<StorageFileEntry> entries = List.of(
                createEntry(
                        "2026/07/orphan-1.pdf"
                ),
                createEntry(
                        "2026/07/kept.pdf"
                ),
                createEntry(
                        "2026/07/orphan-2.pdf"
                ),
                createEntry(
                        "2026/07/orphan-3.pdf"
                ),
                createEntry(
                        "2026/07/orphan-4.pdf"
                )
        );

        /*
         * FileStorageService filesystem-dən tapdığı
         * faylları Consumer vasitəsilə bir-bir qaytarır.
         */
        doAnswer(invocation -> {

            Consumer<StorageFileEntry> consumer =
                    invocation.getArgument(1);

            entries.forEach(consumer);

            return null;

        }).when(fileStorageService)
                .forEachPermanentFileOlderThan(
                        any(Instant.class),
                        any()
                );


        when(
                storedFileRepository
                        .findExistingRelativePaths(
                                anyCollection()
                        )
        ).thenAnswer(invocation -> {

            Collection<String> candidatePaths =
                    invocation.getArgument(0);

            return candidatePaths
                    .stream()
                    .filter(
                            "2026/07/kept.pdf"::equals
                    )
                    .toList();
        });


        when(
                fileStorageService
                        .deleteTemporaryFilesOlderThan(
                                any(Instant.class)
                        )
        ).thenReturn(1);

        CleanupSummary summary =
                cleanupService.cleanup();

        assertAll(
                () -> assertEquals(
                        5,
                        summary.permanentCandidates()
                ),

                () -> assertEquals(
                        4,
                        summary.orphanFilesDeleted()
                ),

                () -> assertEquals(
                        1,
                        summary.temporaryFilesDeleted()
                ),

                () -> assertEquals(
                        0,
                        summary.failures()
                )
        );


        verify(
                storedFileRepository,
                times(3)
        ).findExistingRelativePaths(
                anyCollection()
        );


        verify(
                fileStorageService,
                times(4)
        ).delete(
                anyString()
        );

        
        verify(
                fileStorageService,
                never()
        ).delete(
                "2026/07/kept.pdf"
        );

        verify(
                fileStorageService,
                times(1)
        ).delete(
                "2026/07/orphan-1.pdf"
        );

        verify(
                fileStorageService,
                times(1)
        ).delete(
                "2026/07/orphan-2.pdf"
        );

        verify(
                fileStorageService,
                times(1)
        ).delete(
                "2026/07/orphan-3.pdf"
        );

        verify(
                fileStorageService,
                times(1)
        ).delete(
                "2026/07/orphan-4.pdf"
        );

        verify(
                fileStorageService,
                times(1)
        ).deleteTemporaryFilesOlderThan(
                any(Instant.class)
        );
    }

    private StorageFileEntry createEntry(
            String relativePath
    ) {
        return new StorageFileEntry(
                relativePath,
                Instant.EPOCH
        );
    }
}