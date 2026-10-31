package dev.jobpilot;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiError extends RuntimeException {
  final int status;

  ApiError(int status, String msg) {
    super(msg);
    this.status = status;
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> error(ApiError e) {
    return ResponseEntity.status(e.status)
        .body(java.util.Map.of("message", e.getMessage(), "status", e.status));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            java.util.Map.of(
                "message", "Invalid input. Check required fields and formats.", "status", 400));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> large() {
    return ResponseEntity.status(413)
        .body(java.util.Map.of("message", "Maximum upload size is 10 MB"));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(java.util.Map.of("message", "Record already exists or conflicts with stored data"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error("request_failed type={}", e.getClass().getSimpleName());
    return ResponseEntity.status(500)
        .body(java.util.Map.of("message", "Operation failed. Check service health and retry."));
  }
}
