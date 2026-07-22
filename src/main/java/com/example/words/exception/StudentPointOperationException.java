package com.example.words.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class StudentPointOperationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public StudentPointOperationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
