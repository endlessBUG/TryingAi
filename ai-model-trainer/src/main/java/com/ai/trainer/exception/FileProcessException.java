package com.ai.trainer.exception;

public class FileProcessException extends RuntimeException {
    public FileProcessException(String message) {
        super(message);
    }

    public FileProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
