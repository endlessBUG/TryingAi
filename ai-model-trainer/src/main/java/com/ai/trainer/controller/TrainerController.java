package com.ai.trainer.controller;

import com.ai.trainer.model.Trainer;
import com.ai.trainer.repository.TrainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trainers")
@RequiredArgsConstructor
public class TrainerController {

    private final TrainerRepository trainerRepo;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok(Map.of("success", true, "data", trainerRepo.findAll()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Trainer trainer) {
        trainer.setId(UUID.randomUUID().toString());
        trainer.setCreatedAt(LocalDateTime.now());
        trainerRepo.save(trainer);
        return ResponseEntity.ok(Map.of("success", true, "data", trainer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @RequestBody Trainer trainer) {
        Trainer existing = trainerRepo.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        existing.setName(trainer.getName());
        existing.setPath(trainer.getPath());
        existing.setGitUrl(trainer.getGitUrl());
        existing.setPythonVersion(trainer.getPythonVersion());
        existing.setDefaultYamlConfig(trainer.getDefaultYamlConfig());
        trainerRepo.save(existing);
        return ResponseEntity.ok(Map.of("success", true, "data", existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        if (!trainerRepo.existsById(id)) return ResponseEntity.notFound().build();
        trainerRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }
}
