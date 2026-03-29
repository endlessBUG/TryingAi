package com.ai.trainer.service;

import com.ai.trainer.model.ComfyuiWorkflow;
import com.ai.trainer.model.ComfyuiWorkflowInfo;
import com.ai.trainer.repository.ComfyuiWorkflowRepository;
import com.ai.trainer.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComfyuiService {

    private final ComfyuiWorkflowRepository workflowRepo;
    private final SystemConfigRepository configRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient();

    public List<ComfyuiWorkflow> getAllWorkflows() {
        try {
            List<ComfyuiWorkflowInfo> workflowInfos = getWorkflowsFromComfyui();
            return workflowInfos.stream()
                    .map(this::convertToWorkflow)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取 ComfyUI 工作流失败：{}", e.getMessage());
            return List.of();
        }
    }

    private ComfyuiWorkflow convertToWorkflow(ComfyuiWorkflowInfo info) {
        return ComfyuiWorkflow.builder()
                .id(info.getPath())
                .name(extractFileName(info.getPath()))
                .category("workflow")
                .description(formatSize(info.getSize()))
                .workflowJson("")
                .enabled(true)
                .createdAt(parseTimestamp(info.getModified()))
                .build();
    }

    private java.time.LocalDateTime parseTimestamp(Double timestamp) {
        if (timestamp == null || timestamp == 0) return null;
        java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp.longValue());
        return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "unknown";
        int lastSlash = path.lastIndexOf('/');
        int lastBackslash = path.lastIndexOf('\\');
        int idx = Math.max(lastSlash, lastBackslash);
        String fileName = idx >= 0 ? path.substring(idx + 1) : path;
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private List<ComfyuiWorkflowInfo> getWorkflowsFromComfyui() throws IOException {
        String url = getApiUrl() + "/userdata?dir=workflows&recurse=true&split=false&full_info=true";
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            return objectMapper.readValue(body, new TypeReference<List<ComfyuiWorkflowInfo>>() {});
        }
    }

    public ComfyuiWorkflow getWorkflow(String id) {
        return workflowRepo.findById(id).orElse(null);
    }

    public ComfyuiWorkflow saveWorkflow(ComfyuiWorkflow workflow) {
        return workflowRepo.save(workflow);
    }

    public void deleteWorkflow(String id) {
        workflowRepo.deleteById(id);
    }

    public String getComfyuiUrl() {
        return configRepo.findById("comfyui.url")
                .map(c -> c.getConfigValue())
                .orElse("http://127.0.0.1:8188");
    }

    public JsonNode executeWorkflow(String workflowId, Map<String, Object> params) throws IOException {
        ComfyuiWorkflow workflow = workflowRepo.findById(workflowId).orElse(null);
        if (workflow == null) {
            throw new RuntimeException("工作流不存在: " + workflowId);
        }

        String workflowJson = workflow.getWorkflowJson();
        if (params != null && !params.isEmpty()) {
            workflowJson = replaceParams(workflowJson, params);
        }

        String promptId = submitPrompt(workflowJson);
        log.info("提交工作流: {} -> promptId: {}", workflow.getName(), promptId);

        return waitForResult(promptId);
    }

    public String submitPrompt(String workflowJson) throws IOException {
        String url = getApiUrl() + "/prompt";
        Map<String, Object> body = Map.of("prompt", objectMapper.readValue(workflowJson, JsonNode.class));
        String jsonBody = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("提交工作流失败: " + response.code());
            }
            JsonNode result = objectMapper.readTree(response.body().string());
            return result.get("prompt_id").asText();
        }
    }

    public JsonNode waitForResult(String promptId) throws IOException {
        int maxWait = 300;
        int waited = 0;
        while (waited < maxWait) {
            JsonNode history = getHistory(promptId);
            if (history.has(promptId) && history.get(promptId).has("outputs")) {
                return history.get(promptId).get("outputs");
            }
            try {
                Thread.sleep(1000);
                waited++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new RuntimeException("工作流执行超时");
    }

    public JsonNode getHistory(String promptId) throws IOException {
        String url = getApiUrl() + "/history/" + promptId;
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            return objectMapper.readTree(response.body().string());
        }
    }

    public JsonNode getNodeInfo() throws IOException {
        String url = getApiUrl() + "/object_info";
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            return objectMapper.readTree(response.body().string());
        }
    }

    public byte[] getImage(String filename, String subfolder, String type) throws IOException {
        String url = getApiUrl() + "/view?filename=" + filename;
        if (subfolder != null) url += "&subfolder=" + subfolder;
        if (type != null) url += "&type=" + type;

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().bytes();
        }
    }

    private String replaceParams(String json, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            json = json.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return json;
    }

    private String getApiUrl() {
        String baseUrl = getComfyuiUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api";
    }
}