package com.adil.filevault.exception;

import java.util.UUID;

public class StoredFileNotFoundException
        extends RuntimeException {

    public StoredFileNotFoundException(UUID fileId) {
        super("File not found: " + fileId);
    }
}