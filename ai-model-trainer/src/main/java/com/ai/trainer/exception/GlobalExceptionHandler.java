package com.ai.trainer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TrainingException.class)
    public ResponseEntity<Map<String, Object>> handleTraining(TrainingException e) {
        log.error("训练异常: {}", e.getMessage(), e);
        return ResponseEntity.badRequest().body(buildError(e.getMessage()));
    }

    @ExceptionHandler(FileProcessException.class)
    public ResponseEntity<Map<String, Object>> handleFileProcess(FileProcessException e) {
        log.error("文件处理异常: {}", e.getMessage(), e);
        return ResponseEntity.badRequest().body(buildError(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(buildError("系统内部错误"));
    }

    private Map<String, Object> buildError(String message) {
        return Map.of("success", false, "message", message);
    }
}
