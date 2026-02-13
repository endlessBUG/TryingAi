package com.ai.trainer.service;

import com.ai.trainer.model.PromptGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 调用视觉模型 API 生成图片描述，兼容 OpenAI Vision API 格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCaptionService {

    private final RestTemplate restTemplate;

    /**
     * 测试模型连通性，发送纯文本请求验证 API 是否可用
     */
    public String testConnection(PromptGenerator generator) {
        Map<String, Object> textContent = Map.of("type", "text", "text", "Hello, respond with OK.");
        Map<String, Object> message = Map.of("role", "user", "content", List.of(textContent));
        Map<String, Object> body = Map.of(
                "model", Optional.ofNullable(generator.getModelName()).orElse("default"),
                "messages", List.of(message),
                "max_tokens", 50
        );
        return callVisionApi(generator.getBaseUrl(), generator.getModelName(), body);
    }

    public String generateCaption(PromptGenerator generator, String imagePath) {
        try {
            String base64 = encodeImageToBase64(imagePath);
            Map<String, Object> body = buildRequestBody(generator, base64);
            return callVisionApi(generator.getBaseUrl(), generator.getModelName(), body);
        } catch (Exception e) {
            log.error("生成图片描述失败: {}", imagePath, e);
            return "";
        }
    }

    private String encodeImageToBase64(String imagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(imagePath));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private Map<String, Object> buildRequestBody(PromptGenerator generator, String base64Image) {
        String mediaType = "image/jpeg";
        String imageUrl = "data:" + mediaType + ";base64," + base64Image;

        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageUrl)
        );
        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", getSystemPrompt(generator)
        );

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );

        return Map.of(
                "model", Optional.ofNullable(generator.getModelName()).orElse("default"),
                "messages", List.of(message),
                "max_tokens", 500
        );
    }

    @SuppressWarnings("unchecked")
    private String callVisionApi(String baseUrl, String modelName, Map<String, Object> body) {
        String url = baseUrl.replaceAll("/+$", "") + "/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class
        );

        return extractContent(response.getBody());
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseBody) {
        if (responseBody == null) return "";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return "";
        return String.valueOf(message.getOrDefault("content", ""));
    }

    private String getSystemPrompt(PromptGenerator generator) {
        if (generator.getSystemPrompt() != null && !generator.getSystemPrompt().isBlank()) {
            return generator.getSystemPrompt();
        }
        return "Describe this image in detail for AI training. "
                + "Output comma-separated English tags covering subject, style, colors, background.";
    }
}
