package com.adil.filevault.file.validation;

import com.adil.filevault.config.FileStorageProperties;
import com.adil.filevault.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class SecureFileValidator {

    private static final String GENERIC_MEDIA_TYPE =
            "application/octet-stream";

    private static final int MAX_DOCX_ENTRY_COUNT = 2_000;

    private static final Pattern SAFE_FILENAME_PATTERN =
            Pattern.compile(
                    "[\\p{L}\\p{N}._()\\[\\]\\- ]+"
            );

    private static final Set<String> REQUIRED_DOCX_ENTRIES =
            Set.of(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "word/document.xml"
            );

    private final FileStorageProperties properties;

    private final Tika tika = new Tika();

    /**
     * Cheap validations before writing the upload to temporary storage.
     */
    public FileCandidate validateRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException(
                    "Uploaded file must not be empty"
            );
        }

        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new InvalidFileException(
                    "File exceeds the maximum allowed size"
            );
        }

        String originalFilename =
                normalizeFilename(file.getOriginalFilename());

        String extension =
                extractExtension(originalFilename);

        String expectedMediaType =
                properties.allowedTypes().get(extension);

        if (expectedMediaType == null) {
            throw new InvalidFileException(
                    "Unsupported file extension: ." + extension
            );
        }

        String clientMediaType = file.getContentType();

        /*
         * Content-Type comes from the client and cannot be trusted.
         * We only use it as an early consistency check.
         */
        if (StringUtils.hasText(clientMediaType)
                && !GENERIC_MEDIA_TYPE.equalsIgnoreCase(
                clientMediaType
        )
                && !expectedMediaType.equalsIgnoreCase(
                clientMediaType
        )) {

            throw new InvalidFileException(
                    "File Content-Type does not match its extension"
            );
        }

        return new FileCandidate(
                originalFilename,
                extension,
                expectedMediaType,
                clientMediaType
        );
    }

    /**
     * Validates the real content after the upload is streamed to temp storage.
     */
    public String validateContent(
            Path temporaryFile,
            FileCandidate candidate
    ) {
        String detectedMediaType =
                detectMediaType(temporaryFile, candidate);

        if (!candidate.expectedMediaType()
                .equalsIgnoreCase(detectedMediaType)) {

            throw new InvalidFileException(
                    "The real file type does not match the extension. "
                            + "Detected type: "
                            + detectedMediaType
            );
        }

        switch (candidate.extension()) {
            case "pdf" -> validatePdfSignature(temporaryFile);
            case "docx" -> validateDocxStructure(temporaryFile);
            default -> throw new InvalidFileException(
                    "Unsupported file extension"
            );
        }

        return detectedMediaType;
    }

    private String detectMediaType(
            Path path,
            FileCandidate candidate
    ) {
        try (
                InputStream inputStream =
                        new BufferedInputStream(
                                Files.newInputStream(path)
                        )
        ) {
            return tika.detect(
                    inputStream,
                    candidate.originalFilename()
            );
        } catch (IOException exception) {
            throw new InvalidFileException(
                    "The uploaded file could not be inspected"
            );
        }
    }

    private void validatePdfSignature(Path path) {
        byte[] expected =
                "%PDF-".getBytes(StandardCharsets.US_ASCII);

        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] actual =
                    inputStream.readNBytes(expected.length);

            if (!java.util.Arrays.equals(expected, actual)) {
                throw new InvalidFileException(
                        "Invalid PDF file signature"
                );
            }
        } catch (IOException exception) {
            throw new InvalidFileException(
                    "PDF file could not be validated"
            );
        }
    }

    private void validateDocxStructure(Path path) {
        Set<String> missingEntries =
                new HashSet<>(REQUIRED_DOCX_ENTRIES);

        int entryCount = 0;

        try (
                ZipInputStream zipInputStream =
                        new ZipInputStream(
                                new BufferedInputStream(
                                        Files.newInputStream(path)
                                )
                        )
        ) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;

                if (entryCount > MAX_DOCX_ENTRY_COUNT) {
                    throw new InvalidFileException(
                            "DOCX contains too many internal entries"
                    );
                }

                String entryName = entry.getName()
                        .replace('\\', '/');

                if (isUnsafeArchivePath(entryName)) {
                    throw new InvalidFileException(
                            "DOCX contains an unsafe internal path"
                    );
                }

                missingEntries.remove(entryName);
                zipInputStream.closeEntry();
            }
        } catch (InvalidFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidFileException(
                    "Invalid or corrupted DOCX file"
            );
        }

        if (!missingEntries.isEmpty()) {
            throw new InvalidFileException(
                    "The file is not a valid DOCX document"
            );
        }
    }

    private boolean isUnsafeArchivePath(String entryName) {
        return entryName.startsWith("/")
                || entryName.equals("..")
                || entryName.startsWith("../")
                || entryName.contains("/../");
    }

    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new InvalidFileException(
                    "Original filename is missing"
            );
        }

        /*
         * Both Windows and Unix path separators are handled.
         */
        String normalized = originalFilename
                .strip()
                .replace('\\', '/');

        int lastSlashIndex = normalized.lastIndexOf('/');

        String leafFilename = lastSlashIndex >= 0
                ? normalized.substring(lastSlashIndex + 1)
                : normalized;

        if (leafFilename.isBlank()
                || leafFilename.length() > 255) {
            throw new InvalidFileException(
                    "Invalid filename length"
            );
        }

        if (leafFilename.startsWith(".")
                || leafFilename.contains("..")
                || !SAFE_FILENAME_PATTERN
                .matcher(leafFilename)
                .matches()) {

            throw new InvalidFileException(
                    "Filename contains unsafe characters"
            );
        }

        return leafFilename;
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex <= 0
                || dotIndex == filename.length() - 1) {
            throw new InvalidFileException(
                    "File extension is missing"
            );
        }

        return filename
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }
}