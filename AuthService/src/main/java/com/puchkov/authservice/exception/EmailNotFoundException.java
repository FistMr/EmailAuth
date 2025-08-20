package com.puchkov.authservice.exception;

public class EmailNotFoundException extends RuntimeException {
    public EmailNotFoundException(String s) {
        super(s);
    }
}
