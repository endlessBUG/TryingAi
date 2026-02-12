package com.ai.trainer.controller;

import com.ai.trainer.util.YamlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @GetMapping("/template/training")
    public ResponseEntity<Map<String, Object>> getTemplate() {
        Map<String, Object> template = buildDefaultTemplate();
        return ResponseEntity.ok(Map.of("success", true, "data", template));
    }

    @PostMapping("/yaml")
    public ResponseEntity<Map<String, Object>> saveYaml(
            @RequestParam String filePath,
            @RequestBody Map<String, Object> configData
    ) throws Exception {
        YamlUtil.writeYaml(filePath, configData);
        return ResponseEntity.ok(Map.of("success", true, "message", "配置保存成功"));
    }

    @GetMapping("/yaml")
    public ResponseEntity<Map<String, Object>> readYaml(@RequestParam String filePath) throws Exception {
        Map<String, Object> data = YamlUtil.readYaml(filePath);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private Map<String, Object> buildDefaultTemplate() {
        Map<String, Object> tpl = new LinkedHashMap<>();
        tpl.put("job", Map.of("name", "my_training", "device", "cuda:0"));
        tpl.put("model", Map.of("name_or_path", ""));
        tpl.put("train", Map.of(
                "dtype", "bf16", "train_steps", 1000,
                "learning_rate", 1e-4, "batch_size", 1,
                "optimizer", "adamw8bit", "lr_scheduler", "cosine",
                "gradient_accumulation_steps", 1
        ));
        tpl.put("datasets", List.of(Map.of(
                "folder_path", "", "caption_ext", ".txt", "resolution", 512
        )));
        tpl.put("network", Map.of("type", "lora", "rank", 16, "alpha", 16));
        tpl.put("save", Map.of("save_every", 100, "max_step_saves_to_keep", 3));
        tpl.put("sample", Map.of(
                "sampler", "euler", "sample_every", 100,
                "width", 512, "height", 512, "prompts", List.of("a photo")
        ));
        return tpl;
    }
}
