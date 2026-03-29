package com.ai.trainer.client;

import com.ai.trainer.dto.TestGenerateRequest;
import com.ai.trainer.dto.TestGenerateResult;
import com.ai.trainer.model.AIConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI兼容客户端
 * 支持 OpenAI, Chatfire 等兼容 OpenAI API 格式的厂商
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIClient implements AIClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(new LoggingInterceptor())
            .build();

    @Override
    public void testConnection(AIConfig config) throws Exception {
        String url = buildUrl(config, "/models");
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("连接测试失败: HTTP " + response.code());
            }
        }
    }

    @Override
    public TestGenerateResult testGenerate(AIConfig config, TestGenerateRequest request) throws Exception {
        String serviceType = config.getServiceType();

        return switch (serviceType) {
            case "text" -> generateText(config, request);
            case "image" -> generateImage(config, request);
            case "video" -> generateVideo(config, request);
            case "text_to_speech" -> generateSpeech(config, request);
            default -> throw new Exception("不支持的服务类型: " + serviceType);
        };
    }

    /**
     * 文本生成
     */
    private TestGenerateResult generateText(AIConfig config, TestGenerateRequest request) throws Exception {
        String url = buildUrl(config, "/chat/completions");
        String model = getFirstModel(config);

        ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("model", model);
        ArrayNode messages = bodyNode.putArray("messages");
        messages.add(objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", request.getPrompt()));
        String body = objectMapper.writeValueAsString(bodyNode);

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("文本生成失败: HTTP " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            String text = json.path("choices").path(0).path("message").path("content").asText();
            return TestGenerateResult.builder().text(text).build();
        }
    }

    /**
     * 图片生成
     */
    private TestGenerateResult generateImage(AIConfig config, TestGenerateRequest request) throws Exception {
        String url = buildUrl(config, "/images/generations");
        String model = getFirstModel(config);

        String body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", request.getPrompt())
                .put("n", 1)
                .put("size", request.getSize() != null ? request.getSize() : "1024x1024")
        );

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("图片生成失败: HTTP " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            JsonNode dataNode = json.path("data").path(0);

            // 检查返回的是 URL 还是 base64 数据
            String imageUrl = dataNode.path("url").asText(null);
            if (imageUrl == null || imageUrl.isEmpty()) {
                // 如果没有 url，检查 b64_json 字段
                String b64Json = dataNode.path("b64_json").asText(null);
                if (b64Json != null && !b64Json.isEmpty()) {
                    imageUrl = "data:image/png;base64," + b64Json;
                }
            }

            return TestGenerateResult.builder().imageUrl(imageUrl).build();
        }
    }

    /**
     * 视频生成（异步）
     */
    private TestGenerateResult generateVideo(AIConfig config, TestGenerateRequest request) throws Exception {
        String url = buildUrl(config, "/video/generations");
        String model = getFirstModel(config);

        var bodyNode = objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", request.getPrompt());

        if (request.getImageUrl() != null) {
            bodyNode.put("image_url", request.getImageUrl());
        }

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("视频生成请求失败: HTTP " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());

            // 检查是否直接返回了视频URL
            String videoUrl = json.path("data").path(0).path("url").asText(null);
            if (videoUrl != null) {
                return TestGenerateResult.builder().videoUrl(videoUrl).build();
            }

            // 否则返回任务ID
            String taskId = json.path("id").asText(null);
            if (taskId == null) {
                taskId = json.path("task_id").asText(null);
            }
            return TestGenerateResult.builder()
                    .taskId(taskId)
                    .status("processing")
                    .build();
        }
    }

    /**
     * 文本转语音
     * 支持 OpenAI 和 FishAudio 格式
     */
    private TestGenerateResult generateSpeech(AIConfig config, TestGenerateRequest request) throws Exception {
        String provider = config.getProvider();
        String url;
        String body;

        if ("fishaudio".equals(provider)) {
            // FishAudio API 格式
            url = buildUrl(config, "/v1/tts");

            ObjectNode bodyNode = objectMapper.createObjectNode();
            bodyNode.put("text", request.getPrompt());

            // 音色克隆模式
            if ("clone".equals(request.getTtsMode()) && request.getReferenceAudioUrl() != null && !request.getReferenceAudioUrl().isEmpty()) {
                // 解析 base64 音频数据
                String audioData = request.getReferenceAudioUrl();
                if (audioData.contains(",")) {
                    audioData = audioData.split(",")[1];
                }
                bodyNode.put("reference_audio", audioData);
                if (request.getReferenceText() != null && !request.getReferenceText().isEmpty()) {
                    bodyNode.put("reference_text", request.getReferenceText());
                }
            } else {
                // 普通模式，使用音色ID
                String voice = request.getVoice();
                if (voice != null && !voice.isEmpty()) {
                    bodyNode.put("reference_id", voice);
                }
                // 如果没有填写音色ID，不传reference_id，让服务端使用默认音色
            }

            body = objectMapper.writeValueAsString(bodyNode);
            log.info("FishAudio TTS 请求 URL: {}", url);
            log.info("FishAudio TTS 请求 Body (前500字符): {}", body.length() > 500 ? body.substring(0, 500) + "..." : body);
        } else {
            // OpenAI 格式
            url = buildUrl(config, "/audio/speech");
            String model = getFirstModel(config);

            body = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("model", model)
                    .put("input", request.getPrompt())
                    .put("voice", request.getVoice() != null ? request.getVoice() : "alloy")
            );
            log.info("OpenAI TTS 请求 URL: {}", url);
            log.info("OpenAI TTS 请求 Body: {}", body);
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")));

        // FishAudio 不需要 Authorization header（本地部署）
        if (!"fishaudio".equals(provider) && config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + config.getApiKey());
        }

        Request httpRequest = requestBuilder.build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("语音生成失败: HTTP {}, URL: {}, body: {}", response.code(), url, errorBody);
                throw new Exception("语音生成失败: HTTP " + response.code() + " - " + errorBody);
            }
            // 返回音频数据，这里简化处理返回base64
            byte[] audioData = response.body().bytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(audioData);
            return TestGenerateResult.builder()
                    .audioUrl("data:audio/wav;base64," + base64)
                    .build();
        }
    }

    /**
     * 构建完整URL
     */
    private String buildUrl(AIConfig config, String endpoint) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + (config.getEndpoint() != null ? config.getEndpoint() : endpoint);
    }

    /**
     * 获取第一个模型名称
     */
    private String getFirstModel(AIConfig config) {
        try {
            if (config.getModel() != null && !config.getModel().isEmpty()) {
                JsonNode models = objectMapper.readTree(config.getModel());
                if (models.isArray() && models.size() > 0) {
                    return models.get(0).asText();
                }
                return config.getModel();
            }
        } catch (Exception e) {
            return config.getModel();
        }
        return "gpt-4";
    }
}