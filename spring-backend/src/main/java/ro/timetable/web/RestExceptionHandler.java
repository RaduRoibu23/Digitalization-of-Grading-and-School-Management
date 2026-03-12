package ro.timetable.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ro.timetable.web.dto.ApiDtos.ApiErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage());
        }

        String detail = fieldErrors.containsValue("Nota invalida")
                ? "Nota invalida"
                : "Request validation failed";

        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "validation_failed",
                detail,
                null,
                null,
                fieldErrors
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "invalid_request",
                "Request body must be valid JSON",
                null,
                null,
                null
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "validation_failed",
                ex.getMessage(),
                null,
                null,
                null
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiErrorResponse(
                "request_failed",
                ex.getReason() == null ? "Request failed" : ex.getReason(),
                null,
                null,
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                "internal_error",
                ex.getMessage() == null ? "Unexpected server error" : ex.getMessage(),
                null,
                null,
                null
        ));
    }
}