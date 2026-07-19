package com.adil.filevault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "filevault.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "filevault.security.jwt.access-token-expiration=15m",

        "filevault.storage.root-location=./target/test-data/filevault/uploads",
        "filevault.storage.temporary-location=./target/test-data/filevault/temp",
        "filevault.storage.max-file-size-bytes=10485760",

        "filevault.storage.allowed-types.pdf=application/pdf",
        "filevault.storage.allowed-types.docx=application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "filevault.cleanup.enabled=false",
})
class FileVaultApplicationTests {

    @Test
    void contextLoads() {
    }
}