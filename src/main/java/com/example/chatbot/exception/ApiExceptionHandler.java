package com.example.chatbot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request.");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleLocalAiError(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage() == null ? "Local AI service is unavailable." : ex.getMessage()
        );
    }
}
