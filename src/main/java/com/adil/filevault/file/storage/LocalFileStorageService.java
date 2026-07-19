package com.adil.filevault.file.storage;

import com.adil.filevault.config.FileStorageProperties;
import com.adil.filevault.exception.FileStorageException;
import com.adil.filevault.exception.InvalidFileException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.NoSuchFileException;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.LinkOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
    public StagedFile stage(MultipartFile file) {
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
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead =
                        inputStream.read(buffer)) != -1) {

                    totalBytes += bytesRead;

                    /*
                     * Real streamed byte count is checked.
                     * We do not rely only on MultipartFile#getSize().
                     */
                    if (totalBytes > maxFileSizeBytes) {
                        throw new InvalidFileException(
                                "File exceeds the maximum allowed size"
                        );
                    }

                    digest.update(buffer, 0, bytesRead);
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
                    .formatHex(digest.digest());

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
        LocalDate currentDate =
                LocalDate.now(ZoneOffset.UTC);

        String storedFilename =
                UUID.randomUUID() + "." + extension;

        Path relativePath = Path.of(
                Integer.toString(currentDate.getYear()),
                "%02d".formatted(
                        currentDate.getMonthValue()
                ),
                storedFilename
        );

        Path destination =
                resolveSafely(rootLocation, relativePath);

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
    public void discard(StagedFile stagedFile) {
        if (stagedFile != null) {
            deleteTemporaryFile(stagedFile.path());
        }
    }

    @Override
    public void delete(String relativePath) {
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
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private Path resolveSafely(
            Path baseLocation,
            Path relativePath
    ) {
        Path resolved = baseLocation
                .resolve(relativePath)
                .normalize();

        if (!resolved.startsWith(baseLocation)) {
            throw new FileStorageException(
                    "Unsafe file storage path"
            );
        }

        return resolved;
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Main upload error must not be hidden.
        }
    }

    @Override
    public Resource load(String relativePath) {
        try {
            Path requestedPath = resolveSafely(
                    rootLocation,
                    Path.of(relativePath)
            );

            Path realRoot = rootLocation.toRealPath();
            Path realFile = requestedPath.toRealPath();

            /*
             * Symlink vasitəsilə storage root-dan
             * kənara çıxılmasının qarşısını alır.
             */
            if (!realFile.startsWith(realRoot)) {
                throw new FileStorageException(
                        "Unsafe stored file path"
                );
            }

            if (!Files.isRegularFile(realFile)
                    || !Files.isReadable(realFile)) {

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
    public List<StorageFileEntry> findPermanentFilesOlderThan(
            Instant cutoff
    ) {
        try (Stream<Path> paths = Files.walk(rootLocation)) {
            return paths
                    .filter(path ->
                            Files.isRegularFile(
                                    path,
                                    LinkOption.NOFOLLOW_LINKS
                            )
                    )
                    .map(path ->
                            createStorageEntry(path, cutoff)
                    )
                    .filter(Objects::nonNull)
                    .toList();

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Permanent storage could not be scanned",
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
             * Cutoff-dan yeni fayllar cleanup üçün
             * namizəd deyil.
             */
            if (lastModifiedAt.isAfter(cutoff)) {
                return null;
            }

            String relativePath = rootLocation
                    .relativize(path)
                    .toString()
                    .replace('\\', '/');

            return new StorageFileEntry(
                    relativePath,
                    lastModifiedAt
            );

        } catch (IOException exception) {
            log.warn(
                    "Could not inspect stored file: {}",
                    path,
                    exception
            );

            return null;
        }
    }

    @Override
    public int deleteTemporaryFilesOlderThan(
            Instant cutoff
    ) {
        List<Path> candidates;

        try (Stream<Path> paths =
                     Files.walk(temporaryLocation)) {

            candidates = paths
                    .filter(path ->
                            Files.isRegularFile(
                                    path,
                                    LinkOption.NOFOLLOW_LINKS
                            )
                    )
                    .filter(path ->
                            isOlderThan(path, cutoff)
                    )
                    .toList();

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Temporary storage could not be scanned",
                    exception
            );
        }

        int deletedCount = 0;

        for (Path candidate : candidates) {
            try {
                if (Files.deleteIfExists(candidate)) {
                    deletedCount++;
                }
            } catch (IOException exception) {
                log.warn(
                        "Temporary file could not be deleted: {}",
                        candidate,
                        exception
                );
            }
        }

        return deletedCount;
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
}