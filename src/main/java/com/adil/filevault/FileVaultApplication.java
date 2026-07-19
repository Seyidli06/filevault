package com.adil.filevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileVaultApplication.class, args);
    }
}