package com.ai.trainer.strategy;

import com.ai.trainer.model.PromptGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCaptionStrategy implements CaptionStrategy {

    protected final RestTemplate restTemplate;

    @Override
    public String generateCaption(PromptGenerator generator, String imagePath) {
        try {
            String base64 = encodeImageToBase64(imagePath);
            Map<String, Object> body = buildCaptionBody(generator, base64);
            return callApi(generator.getBaseUrl(), body);
        } catch (Exception e) {
            log.error("生成图片描述失败: {}", imagePath, e);
            return "";
        }
    }

    @Override
    public String testConnection(PromptGenerator generator) {
        Map<String, Object> textContent = Map.of("type", "text", "text", "Hello, respond with OK.");
        Map<String, Object> message = Map.of("role", "user", "content", List.of(textContent));
        Map<String, Object> body = Map.of(
                "model", Optional.ofNullable(generator.getModelName()).orElse("default"),
                "messages", List.of(message),
                "max_tokens", 50
        );
        return callApi(generator.getBaseUrl(), body);
    }

    protected int resolveMaxTokens(PromptGenerator generator) {
        return (generator.getMaxTokens() != null && generator.getMaxTokens() > 0)
                ? generator.getMaxTokens() : 1000;
    }

    protected abstract Map<String, Object> buildCaptionBody(PromptGenerator generator, String base64Image);

    @SuppressWarnings("unchecked")
    protected String callApi(String baseUrl, Map<String, Object> body) {
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

    private String encodeImageToBase64(String imagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(imagePath));
        return Base64.getEncoder().encodeToString(bytes);
    }

    protected String getSystemPrompt(PromptGenerator generator) {
        if (generator.getSystemPrompt() != null && !generator.getSystemPrompt().isBlank()) {
            return generator.getSystemPrompt();
        }
        return "Describe this image in detail for AI training. "
                + "Output comma-separated English tags covering subject, style, colors, background.";
    }
}
