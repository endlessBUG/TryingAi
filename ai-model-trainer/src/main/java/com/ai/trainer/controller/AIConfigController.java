package com.ai.trainer.controller;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.dto.*;
import com.ai.trainer.model.AIConfig;
import com.ai.trainer.service.AIConfigService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-configs")
@RequiredArgsConstructor
public class AIConfigController {

    private final AIConfigService configService;
    private final TrainerProperties trainerProperties;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final OkHttpClient streamingClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    /**
     * 获取配置列表
     * @param serviceType 服务类型（可选）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String serviceType) {
        List<AIConfig> configs = configService.listByType(serviceType);
        return ResponseEntity.ok(Map.of("success", true, "data", configs));
    }

    /**
     * 获取单个配置
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        AIConfig config = configService.getById(id);
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    /**
     * 创建配置
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateAIConfigRequest request) {
        AIConfig config = configService.create(request);
        return ResponseEntity.ok(Map.of("success", true, "data", config, "message", "创建成功"));
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody UpdateAIConfigRequest request) {
        AIConfig config = configService.update(id, request);
        return ResponseEntity.ok(Map.of("success", true, "data", config, "message", "更新成功"));
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        configService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    /**
     * 切换配置激活状态
     */
    @PostMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        AIConfig config = configService.toggleActive(id);
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    /**
     * 测试连接
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody TestConnectionRequest request) {
        configService.testConnection(request);
        return ResponseEntity.ok(Map.of("success", true, "message", "连接测试成功"));
    }

    /**
     * 测试生成
     */
    @PostMapping("/{id}/test-generate")
    public ResponseEntity<Map<String, Object>> testGenerate(
            @PathVariable Long id,
            @RequestBody TestGenerateRequest request) {
        TestGenerateResult result = configService.testGenerate(id, request);
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    /**
     * 获取测试任务状态
     */
    @GetMapping("/{id}/test-task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(
            @PathVariable Long id,
            @PathVariable String taskId) {
        TestGenerateResult result = configService.getTaskStatus(id, taskId);
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    /**
     * 获取 ComfyUI 工作流列表
     * 从 classpath:comfyui-workflows/ 目录读取 JSON 文件列表
     */
    @GetMapping("/comfyui-workflows")
    public ResponseEntity<Map<String, Object>> getComfyuiWorkflows() {
        List<Map<String, String>> workflows = new ArrayList<>();

        try {
            // 从 classpath 读取工作流文件
            Resource[] resources = resourceResolver.getResources("classpath:comfyui-workflows/*.json");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    Map<String, String> workflow = new HashMap<>();
                    workflow.put("filename", filename);
                    workflow.put("name", filename.replace(".json", ""));
                    workflows.add(workflow);
                    log.debug("发现工作流: {}", filename);
                }
            }
            log.info("从 classpath 读取到 {} 个工作流文件", workflows.size());
        } catch (Exception e) {
            log.warn("读取工作流文件失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("success", true, "data", workflows));
    }

    /**
     * 获取默认端点配置
     */
    @GetMapping("/default-endpoints")
    public ResponseEntity<Map<String, Object>> getDefaultEndpoints() {
        Map<String, Map<String, String>> endpoints = new HashMap<>();

        // 各厂商各服务类型的默认端点
        endpoints.put("comfyui", Map.of(
                "all", "/prompt"
        ));
        endpoints.put("chatfire", Map.of(
                "text", "/chat/completions",
                "image", "/images/generations",
                "video", "/video/generations",
                "text_to_speech", "/audio/speech"
        ));
        endpoints.put("openai", Map.of(
                "text", "/chat/completions",
                "image", "/images/generations",
                "text_to_speech", "/audio/speech"
        ));
        endpoints.put("gemini", Map.of(
                "text", "/v1beta/models/{model}:generateContent"
        ));
        endpoints.put("volces", Map.of(
                "video", "/contents/generations/tasks"
        ));
        endpoints.put("fishaudio", Map.of(
                "text_to_speech", "/v1/tts"
        ));

        return ResponseEntity.ok(Map.of("success", true, "data", endpoints));
    }

    /**
     * 流式下载 ComfyUI 生成的视频
     * 前端通过此接口获取视频流，支持 Range 请求
     */
    @GetMapping("/{id}/video-stream")
    public void getVideoStream(
            @PathVariable Long id,
            @RequestParam String filename,
            @RequestParam(required = false) String subfolder,
            @RequestParam(defaultValue = "output") String type,
            HttpServletResponse response) {

        try {
            AIConfig config = configService.getById(id);
            String baseUrl = config.getBaseUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            // 构建 ComfyUI /view URL
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("/view?filename=").append(filename);
            urlBuilder.append("&type=").append(type);
            if (subfolder != null && !subfolder.isEmpty()) {
                urlBuilder.append("&subfolder=").append(subfolder);
            }

            String url = urlBuilder.toString();
            log.info("代理视频流: {}", url);

            // 确定 Content-Type
            String contentType = "video/mp4";
            String lowerName = filename.toLowerCase();
            if (lowerName.endsWith(".webm")) {
                contentType = "video/webm";
            } else if (lowerName.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (lowerName.endsWith(".gif")) {
                contentType = "image/gif";
            }

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response comfyResponse = streamingClient.newCall(request).execute()) {
                if (!comfyResponse.isSuccessful()) {
                    response.sendError(comfyResponse.code(), "获取视频失败");
                    return;
                }

                // 设置响应头
                response.setContentType(contentType);
                String contentLength = comfyResponse.header("Content-Length");
                if (contentLength != null) {
                    response.setHeader("Content-Length", contentLength);
                }
                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("Access-Control-Allow-Origin", "*");

                // 流式传输
                try (InputStream is = comfyResponse.body().byteStream();
                     OutputStream os = response.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        os.flush();
                    }
                }
            }
        } catch (Exception e) {
            log.error("视频流代理失败", e);
            try {
                response.sendError(500, "视频流代理失败: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }
}