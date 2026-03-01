package com.ai.trainer.controller;

import com.ai.trainer.model.PromptGenerator;
import com.ai.trainer.repository.PromptGeneratorRepository;
import com.ai.trainer.service.ImageCaptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompt-generators")
@RequiredArgsConstructor
public class PromptGeneratorController {

    private final PromptGeneratorRepository generatorRepo;
    private final ImageCaptionService captionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("success", true, "data", generatorRepo.findAll()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody PromptGenerator generator) {
        generator.setId(UUID.randomUUID().toString());
        generator.setCreatedAt(LocalDateTime.now());
        generatorRepo.save(generator);
        return ResponseEntity.ok(Map.of("success", true, "data", generator));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
                                                      @RequestBody PromptGenerator generator) {
        PromptGenerator existing = generatorRepo.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        existing.setName(generator.getName());
        existing.setType(generator.getType());
        existing.setBaseUrl(generator.getBaseUrl());
        existing.setModelName(generator.getModelName());
        existing.setSystemPrompt(generator.getSystemPrompt());
        existing.setMaxTokens(generator.getMaxTokens());
        existing.setEnabled(generator.getEnabled());
        generatorRepo.save(existing);
        return ResponseEntity.ok(Map.of("success", true, "data", existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        if (!generatorRepo.existsById(id)) return ResponseEntity.notFound().build();
        generatorRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    @PostMapping(value = "/{id}/test", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "*/*"})
    public ResponseEntity<Map<String, Object>> test(@PathVariable String id,
                                                    @RequestParam(required = false) MultipartFile file) {
        PromptGenerator generator = generatorRepo.findById(id).orElse(null);
        if (generator == null) return ResponseEntity.notFound().build();
        try {
            String reply = (file != null && !file.isEmpty())
                    ? captionService.testWithImage(generator, file)
                    : captionService.testConnection(generator);
            return ResponseEntity.ok(Map.of("success", true, "data", reply));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
