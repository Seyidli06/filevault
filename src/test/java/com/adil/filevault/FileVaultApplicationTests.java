package com.adil.filevault;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        "filevault.cleanup.enabled=false"
})
@AutoConfigureMockMvc
class FileVaultApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

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

        String requestBody = """
                {
                  "fullName": "Integration Test User",
                  "email": "duplicate-test@example.com",
                  "password": "StrongPassword123"
                }
                """;

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
}