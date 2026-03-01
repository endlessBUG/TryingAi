package com.ai.trainer.service;

import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * 训练结果自动评估：通过 ComfyUI API 生成测试图片
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingEvalService {

    private final RestTemplate restTemplate;
    private final SystemConfigRepository configRepo;

    private static final List<String> DEFAULT_PROMPTS = List.of(
            "a portrait photo, high quality, detailed",
            "a full body shot, professional lighting"
    );

    public void evaluate(TrainingTask task) {
        String comfyUrl = getComfyUrl();
        if (comfyUrl == null) {
            log.info("未配置 ComfyUI 地址，跳过自动评估");
            return;
        }
        String loraName = findLoraName(task);
        if (loraName == null) {
            log.info("未找到 LoRA 模型文件，跳过评估");
            return;
        }
        Path evalDir = createEvalDir(task);
        generateTestImages(comfyUrl, loraName, evalDir, task);
    }

    private String getComfyUrl() {
        return configRepo.findById("comfyui.url")
                .map(c -> c.getConfigValue())
                .orElse(null);
    }

    private String findLoraName(TrainingTask task) {
        if (task.getOutputPath() == null) return null;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(
                Path.of(task.getOutputPath()), "*.safetensors")) {
            for (Path f : s) return f.getFileName().toString();
        } catch (IOException e) {
            log.warn("读取输出目录失败: {}", e.getMessage());
        }
        return null;
    }

    private Path createEvalDir(TrainingTask task) {
        Path dir = Path.of(task.getOutputPath(), "eval");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建评估目录失败", e);
        }
        return dir;
    }

    private void generateTestImages(String comfyUrl, String loraName, Path evalDir, TrainingTask task) {
        List<String> prompts = extractPrompts(task);
        for (int i = 0; i < prompts.size(); i++) {
            generateOne(comfyUrl, loraName, prompts.get(i), evalDir, i);
        }
    }

    private List<String> extractPrompts(TrainingTask task) {
        if (task.getYamlConfig() != null && task.getYamlConfig().contains("trigger_word")) {
            return DEFAULT_PROMPTS;
        }
        return DEFAULT_PROMPTS;
    }

    @SuppressWarnings("unchecked")
    private void generateOne(String comfyUrl, String loraName, String prompt, Path evalDir, int idx) {
        try {
            String fullPrompt = "<lora:" + loraName.replace(".safetensors", "") + ":0.8> " + prompt;
            Map<String, Object> body = buildComfyPayload(fullPrompt);
            String url = comfyUrl.replaceAll("/+$", "") + "/api/prompt";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("评估图片生成请求已提交: prompt={}", fullPrompt);
            }
        } catch (Exception e) {
            log.warn("评估图片生成失败: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildComfyPayload(String prompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", prompt);
        payload.put("client_id", UUID.randomUUID().toString());
        return payload;
    }
}
