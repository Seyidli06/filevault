package com.adil.filevault;

import com.adil.filevault.audit.repository.FileDownloadAuditRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adil.filevault.audit.entity.FileDownloadAudit;
import org.springframework.data.domain.Sort;

@Testcontainers
@SpringBootTest(properties = {
        "filevault.security.jwt.secret-base64="
                + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",

        "filevault.security.jwt.access-token-expiration=15m",

        "filevault.storage.root-location="
                + "./target/test-data/filevault/uploads",

        "filevault.storage.temporary-location="
                + "./target/test-data/filevault/temp",

        "filevault.storage.max-file-size-bytes=10485760",

        "filevault.storage.allowed-types.pdf=application/pdf",

        "filevault.storage.allowed-types.docx="
                + "application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document",

        "filevault.cleanup.enabled=false",

        "server.forward-headers-strategy=framework"
})
@AutoConfigureMockMvc
class FileVaultApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileDownloadAuditRepository auditRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void filesWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(
                        get("/api/files")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(
                        jsonPath("$.error")
                                .value("Unauthorized")
                );
    }

    @Test
    void invalidRegisterRequestShouldReturn400()
            throws Exception {

        String requestBody = """
                {
                  "fullName": "",
                  "email": "not-an-email",
                  "password": "123"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Request validation failed")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.fullName")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.fieldErrors.email")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.fieldErrors.password")
                                .exists()
                );
    }

    @Test
    void duplicateEmailShouldReturn409()
            throws Exception {

        String email =
                "duplicate-" + UUID.randomUUID()
                        + "@example.com";

        String requestBody = registerRequestBody(email);

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.error")
                                .value("Conflict")
                );
    }

    /*
     * Düzgün PDF upload ediləndə:
     * - 201 qaytarılmalıdır;
     * - metadata response-da görünməlidir;
     * - SHA-256 düzgün hesablanmalıdır.
     */
    @Test
    void validPdfUploadShouldReturn201AndChecksum()
            throws Exception {

        String token = registerAndGetToken(
                uniqueEmail("upload")
        );

        byte[] pdfContent = validPdfContent();

        String expectedSha256 =
                calculateSha256(pdfContent);

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "integration-test.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        pdfContent
                );

        mockMvc.perform(
                        multipart("/api/files")
                                .file(file)
                                .param(
                                        "title",
                                        "Integration Test PDF"
                                )
                                .param(
                                        "description",
                                        "Uploaded during integration test"
                                )
                                .param(
                                        "category",
                                        "DOCUMENT"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(token)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id").exists()
                )
                .andExpect(
                        jsonPath("$.title")
                                .value("Integration Test PDF")
                )
                .andExpect(
                        jsonPath("$.category")
                                .value("DOCUMENT")
                )
                .andExpect(
                        jsonPath("$.originalFilename")
                                .value("integration-test.pdf")
                )
                .andExpect(
                        jsonPath("$.mediaType")
                                .value(MediaType.APPLICATION_PDF_VALUE)
                )
                .andExpect(
                        jsonPath("$.sizeBytes")
                                .value(pdfContent.length)
                )
                .andExpect(
                        jsonPath("$.sha256")
                                .value(expectedSha256)
                );
    }

    /*
     * Extension allowlist-də olmayan fayl
     * upload edilməməlidir.
     */
    @Test
    void unsupportedExtensionShouldReturn400()
            throws Exception {

        String token = registerAndGetToken(
                uniqueEmail("unsupported")
        );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "malware.exe",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        "MZ test executable"
                                .getBytes(StandardCharsets.UTF_8)
                );

        mockMvc.perform(
                        multipart("/api/files")
                                .file(file)
                                .param(
                                        "title",
                                        "Unsupported File"
                                )
                                .param(
                                        "description",
                                        "This must be rejected"
                                )
                                .param(
                                        "category",
                                        "OTHER"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(token)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Bad Request")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Unsupported file extension: .exe"
                                )
                );
    }

    /*
     * User B, User A-ya məxsus faylı
     * endirə bilməməlidir.
     */
    @Test
    void anotherUserShouldNotDownloadFile()
            throws Exception {

        String ownerToken = registerAndGetToken(
                uniqueEmail("owner")
        );

        String anotherUserToken = registerAndGetToken(
                uniqueEmail("other-user")
        );

        String fileId = uploadPdfAndGetId(
                ownerToken,
                "private-document.pdf"
        );

        long auditCountBefore =
                auditRepository.count();

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}/download",
                                fileId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(anotherUserToken)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status").value(404)
                );

        /*
         * İcazəsiz download üçün
         * DOWNLOAD_GRANTED auditi yaranmamalıdır.
         */
        assertEquals(
                auditCountBefore,
                auditRepository.count()
        );
    }

    /*
     * Owner faylı uğurla endirəndə:
     * - response 200 olmalıdır;
     * - binary content eyni olmalıdır;
     * - audit cədvəlinə bir row əlavə edilməlidir.
     */
    @Test
    void successfulDownloadShouldCreateAuditLog()
            throws Exception {

        String token = registerAndGetToken(
                uniqueEmail("audit")
        );

        byte[] pdfContent = validPdfContent();

        String fileId = uploadPdfAndGetId(
                token,
                "audited-document.pdf",
                pdfContent
        );

        String forwardedIp = "203.0.113.42";
        String userAgent = "FileVault-Integration-Test";

        long auditCountBefore =
                auditRepository.count();

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}/download",
                                fileId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(token)
                                )
                                .header(
                                        "X-Forwarded-For",
                                        forwardedIp
                                )
                                .header(
                                        HttpHeaders.USER_AGENT,
                                        userAgent
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                "X-Content-Type-Options",
                                "nosniff"
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(
                        content().contentType(
                                MediaType.APPLICATION_PDF
                        )
                )
                .andExpect(
                        content().bytes(pdfContent)
                );

        assertEquals(
                auditCountBefore + 1,
                auditRepository.count()
        );

        FileDownloadAudit latestAudit =
                auditRepository
                        .findAll(
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "id"
                                )
                        )
                        .getFirst();

        assertEquals(
                UUID.fromString(fileId),
                latestAudit.getFileIdSnapshot()
        );

        assertEquals(
                forwardedIp,
                latestAudit.getRequestIp()
        );

        assertEquals(
                userAgent,
                latestAudit.getUserAgent()
        );
    }

    private String registerAndGetToken(
            String email
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        registerRequestBody(email)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.accessToken").isNotEmpty()
                )
                .andReturn();

        return JsonPath.read(
                result.getResponse()
                        .getContentAsString(),
                "$.accessToken"
        );
    }

    private String uploadPdfAndGetId(
            String token,
            String filename
    ) throws Exception {
        return uploadPdfAndGetId(
                token,
                filename,
                validPdfContent()
        );
    }

    private String uploadPdfAndGetId(
            String token,
            String filename,
            byte[] pdfContent
    ) throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        filename,
                        MediaType.APPLICATION_PDF_VALUE,
                        pdfContent
                );

        MvcResult result = mockMvc.perform(
                        multipart("/api/files")
                                .file(file)
                                .param(
                                        "title",
                                        "Private Test File"
                                )
                                .param(
                                        "description",
                                        "Integration test file"
                                )
                                .param(
                                        "category",
                                        "DOCUMENT"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(token)
                                )
                )
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(
                result.getResponse()
                        .getContentAsString(),
                "$.id"
        );
    }

    private String registerRequestBody(
            String email
    ) {
        return """
                {
                  "fullName": "Integration Test User",
                  "email": "%s",
                  "password": "StrongPassword123"
                }
                """.formatted(email);
    }

    private String uniqueEmail(String prefix) {
        return prefix
                + "-"
                + UUID.randomUUID()
                + "@example.com";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] validPdfContent() {
        return """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog >>
                endobj
                trailer
                << /Root 1 0 R >>
                %%EOF
                """.getBytes(StandardCharsets.US_ASCII);
    }

    private String calculateSha256(
            byte[] content
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        return HexFormat
                .of()
                .formatHex(
                        digest.digest(content)
                );
    }
}