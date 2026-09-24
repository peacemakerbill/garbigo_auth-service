package com.garbigo.auth.exception;

import com.garbigo.auth.dto.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<MessageResponse> handleCustomException(CustomException ex) {
        return ResponseEntity.badRequest().body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleException(Exception ex) {
        // Print stack trace to see actual error
        ex.printStackTrace();
        return ResponseEntity.internalServerError()
            .body(new MessageResponse("An unexpected error occurred: " + ex.getMessage())); // Show actual error
    }
}