package com.ai.trainer.controller;

import com.ai.trainer.model.SystemConfig;
import com.ai.trainer.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigRepository configRepo;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        Map<String, String> result = new HashMap<>();
        configRepo.findAll().forEach(c -> result.put(c.getConfigKey(), c.getConfigValue()));
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> configs) {
        configs.forEach((key, value) -> configRepo.save(new SystemConfig(key, value)));
        return ResponseEntity.ok(Map.of("success", true, "message", "保存成功"));
    }
}
