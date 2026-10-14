package com.kinetic.checkout.exception;

import com.kinetic.checkout.dto.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> business(ApiException error) {
        return ResponseEntity.status(error.status())
                .body(new ErrorResponse(error.code(), error.getMessage(), error.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult()
                .getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(
                        new ErrorResponse(
                                "VALIDATION_ERROR", "Please check the request fields.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<ErrorResponse> malformed(Exception error) {
        return ResponseEntity.badRequest()
                .body(
                        new ErrorResponse(
                                "INVALID_REQUEST",
                                "A required header or JSON field is missing or invalid.",
                                Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database(DataAccessException error) {
        log.error("Database operation failed", error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        new ErrorResponse(
                                "STORE_BUSY",
                                "The store is busy. Please retry the same request.",
                                Map.of()));
    }
}
