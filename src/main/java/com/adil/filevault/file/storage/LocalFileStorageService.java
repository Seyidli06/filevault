package com.adil.filevault.file.storage;

import com.adil.filevault.config.FileStorageProperties;
import com.adil.filevault.exception.FileStorageException;
import com.adil.filevault.exception.InvalidFileException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@Slf4j
public class LocalFileStorageService
        implements FileStorageService {

    private static final int BUFFER_SIZE = 8 * 1024;

    private final Path rootLocation;
    private final Path temporaryLocation;
    private final long maxFileSizeBytes;

    public LocalFileStorageService(
            FileStorageProperties properties
    ) {
        this.rootLocation = properties
                .rootLocation()
                .toAbsolutePath()
                .normalize();

        this.temporaryLocation = properties
                .temporaryLocation()
                .toAbsolutePath()
                .normalize();

        this.maxFileSizeBytes =
                properties.maxFileSizeBytes();
    }

    @PostConstruct
    void initializeStorage() {
        try {
            Files.createDirectories(rootLocation);
            Files.createDirectories(temporaryLocation);

        } catch (IOException exception) {
            throw new FileStorageException(
                    "File storage directories could not be created",
                    exception
            );
        }
    }

    @Override
    public StagedFile stage(
            MultipartFile file
    ) {
        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(
                    temporaryLocation,
                    "upload-",
                    ".tmp"
            );

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            long totalBytes = 0;

            try (
                    InputStream inputStream =
                            file.getInputStream();

                    OutputStream outputStream =
                            Files.newOutputStream(
                                    temporaryFile,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING
                            )
            ) {
                byte[] buffer =
                        new byte[BUFFER_SIZE];

                int bytesRead;

                while (
                        (
                                bytesRead =
                                        inputStream.read(buffer)
                        ) != -1
                ) {
                    totalBytes += bytesRead;

                    /*
                     * MultipartFile#getSize() məlumatına
                     * tam etibar etmirik.
                     *
                     * Real stream-dən oxunan byte sayı
                     * ayrıca yoxlanılır.
                     */
                    if (totalBytes > maxFileSizeBytes) {
                        throw new InvalidFileException(
                                "File exceeds the maximum allowed size"
                        );
                    }

                    digest.update(
                            buffer,
                            0,
                            bytesRead
                    );

                    outputStream.write(
                            buffer,
                            0,
                            bytesRead
                    );
                }
            }

            if (totalBytes == 0) {
                throw new InvalidFileException(
                        "Uploaded file must not be empty"
                );
            }

            String sha256 = HexFormat
                    .of()
                    .formatHex(
                            digest.digest()
                    );

            return new StagedFile(
                    temporaryFile,
                    totalBytes,
                    sha256
            );

        } catch (InvalidFileException exception) {
            deleteTemporaryFile(temporaryFile);
            throw exception;

        } catch (
                IOException
                | NoSuchAlgorithmException exception
        ) {
            deleteTemporaryFile(temporaryFile);

            throw new FileStorageException(
                    "File could not be written to temporary storage",
                    exception
            );
        }
    }

    @Override
    public StoredObject commit(
            StagedFile stagedFile,
            String extension
    ) {
        Objects.requireNonNull(
                stagedFile,
                "stagedFile must not be null"
        );

        Objects.requireNonNull(
                extension,
                "extension must not be null"
        );

        LocalDate currentDate =
                LocalDate.now(ZoneOffset.UTC);

        String storedFilename =
                UUID.randomUUID()
                        + "."
                        + extension;

        Path relativePath = Path.of(
                Integer.toString(
                        currentDate.getYear()
                ),
                "%02d".formatted(
                        currentDate.getMonthValue()
                ),
                storedFilename
        );

        Path destination =
                resolveSafely(
                        rootLocation,
                        relativePath
                );

        try {
            Files.createDirectories(
                    destination.getParent()
            );

            moveFile(
                    stagedFile.path(),
                    destination
            );

            String portableRelativePath =
                    relativePath
                            .toString()
                            .replace('\\', '/');

            return new StoredObject(
                    storedFilename,
                    portableRelativePath,
                    stagedFile.sizeBytes(),
                    stagedFile.sha256()
            );

        } catch (IOException exception) {
            throw new FileStorageException(
                    "File could not be moved to permanent storage",
                    exception
            );
        }
    }

    @Override
    public Resource load(
            String relativePath
    ) {
        Objects.requireNonNull(
                relativePath,
                "relativePath must not be null"
        );

        try {
            Path requestedPath =
                    resolveSafely(
                            rootLocation,
                            Path.of(relativePath)
                    );

            Path realRoot =
                    rootLocation.toRealPath();

            Path realFile =
                    requestedPath.toRealPath();

            /*
             * Symlink vasitəsilə storage root-dan
             * kənara çıxılmasının qarşısını alır.
             */
            if (!realFile.startsWith(realRoot)) {
                throw new FileStorageException(
                        "Unsafe stored file path"
                );
            }

            if (
                    !Files.isRegularFile(
                            realFile,
                            LinkOption.NOFOLLOW_LINKS
                    )
                            || !Files.isReadable(realFile)
            ) {
                throw new FileStorageException(
                        "Stored file is not readable"
                );
            }

            return new FileSystemResource(realFile);

        } catch (NoSuchFileException exception) {
            throw new FileStorageException(
                    "The physical file does not exist",
                    exception
            );

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Stored file could not be loaded",
                    exception
            );
        }
    }

    @Override
    public void forEachPermanentFileOlderThan(
            Instant cutoff,
            Consumer<StorageFileEntry> consumer
    ) {
        Objects.requireNonNull(
                cutoff,
                "cutoff must not be null"
        );

        Objects.requireNonNull(
                consumer,
                "consumer must not be null"
        );

        /*
         * Files.walk lazy stream qaytarır.
         *
         * Tapılan fayllar əvvəlcə böyük List-ə
         * yığılmır. Hər uyğun entry bir-bir
         * consumer-ə ötürülür.
         */
        try (
                Stream<Path> paths =
                        Files.walk(rootLocation)
        ) {
            paths
                    .filter(path ->
                            Files.isRegularFile(
                                    path,
                                    LinkOption.NOFOLLOW_LINKS
                            )
                    )
                    .map(path ->
                            createStorageEntry(
                                    path,
                                    cutoff
                            )
                    )
                    .filter(Objects::nonNull)
                    .forEach(consumer);

        } catch (
                IOException
                | UncheckedIOException exception
        ) {
            throw new FileStorageException(
                    "Permanent storage could not be scanned",
                    exception
            );
        }
    }

    @Override
    public int deleteTemporaryFilesOlderThan(
            Instant cutoff
    ) {
        Objects.requireNonNull(
                cutoff,
                "cutoff must not be null"
        );

        /*
         * Temporary fayllar da əvvəlcə List-ə
         * yığılmadan birbaşa stream zamanı silinir.
         */
        try (
                Stream<Path> paths =
                        Files.walk(temporaryLocation)
        ) {
            return paths
                    .filter(path ->
                            Files.isRegularFile(
                                    path,
                                    LinkOption.NOFOLLOW_LINKS
                            )
                    )
                    .filter(path ->
                            isOlderThan(
                                    path,
                                    cutoff
                            )
                    )
                    .mapToInt(
                            this::deleteTemporaryCandidate
                    )
                    .sum();

        } catch (
                IOException
                | UncheckedIOException exception
        ) {
            throw new FileStorageException(
                    "Temporary storage could not be scanned",
                    exception
            );
        }
    }

    @Override
    public void discard(
            StagedFile stagedFile
    ) {
        if (stagedFile == null) {
            return;
        }

        deleteTemporaryFile(
                stagedFile.path()
        );
    }

    @Override
    public void delete(
            String relativePath
    ) {
        Objects.requireNonNull(
                relativePath,
                "relativePath must not be null"
        );

        Path path = resolveSafely(
                rootLocation,
                Path.of(relativePath)
        );

        try {
            Files.deleteIfExists(path);

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Stored file could not be deleted",
                    exception
            );
        }
    }

    private StorageFileEntry createStorageEntry(
            Path path,
            Instant cutoff
    ) {
        try {
            Instant lastModifiedAt =
                    Files.getLastModifiedTime(
                            path,
                            LinkOption.NOFOLLOW_LINKS
                    ).toInstant();

            /*
             * Cutoff-dan daha yeni fayllar
             * cleanup namizədi deyil.
             */
            if (lastModifiedAt.isAfter(cutoff)) {
                return null;
            }

            String relativePath =
                    rootLocation
                            .relativize(path)
                            .toString()
                            .replace('\\', '/');

            return new StorageFileEntry(
                    relativePath,
                    lastModifiedAt
            );

        } catch (IOException exception) {
            /*
             * Bir faylın metadata-sı oxunmadıqda
             * bütün cleanup dayandırılmır.
             */
            log.warn(
                    "Could not inspect stored file: {}",
                    path,
                    exception
            );

            return null;
        }
    }

    private int deleteTemporaryCandidate(
            Path candidate
    ) {
        try {
            return Files.deleteIfExists(candidate)
                    ? 1
                    : 0;

        } catch (IOException exception) {
            /*
             * Bir temporary fayl silinmədikdə
             * digər faylların cleanup prosesi davam edir.
             */
            log.warn(
                    "Temporary file could not be deleted: {}",
                    candidate,
                    exception
            );

            return 0;
        }
    }

    private boolean isOlderThan(
            Path path,
            Instant cutoff
    ) {
        try {
            Instant lastModifiedAt =
                    Files.getLastModifiedTime(
                            path,
                            LinkOption.NOFOLLOW_LINKS
                    ).toInstant();

            return !lastModifiedAt.isAfter(cutoff);

        } catch (IOException exception) {
            log.warn(
                    "Could not read file timestamp: {}",
                    path,
                    exception
            );

            return false;
        }
    }

    private void moveFile(
            Path source,
            Path destination
    ) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (
                AtomicMoveNotSupportedException exception
        ) {
            /*
             * Filesystem atomic move dəstəkləmirsə,
             * adi move əməliyyatına keçilir.
             */
            Files.move(
                    source,
                    destination
            );
        }
    }

    private Path resolveSafely(
            Path baseLocation,
            Path relativePath
    ) {
        Path resolved = baseLocation
                .resolve(relativePath)
                .normalize();

        /*
         * "../" kimi path traversal cəhdlərinin
         * base storage-dan kənara çıxmasını bloklayır.
         */
        if (!resolved.startsWith(baseLocation)) {
            throw new FileStorageException(
                    "Unsafe file storage path"
            );
        }

        return resolved;
    }

    private void deleteTemporaryFile(
            Path path
    ) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);

        } catch (IOException exception) {
            /*
             * Əsas upload xətası cleanup xətası
             * tərəfindən gizlədilməməlidir.
             */
            log.warn(
                    "Temporary file could not be removed: {}",
                    path,
                    exception
            );
        }
    }
}