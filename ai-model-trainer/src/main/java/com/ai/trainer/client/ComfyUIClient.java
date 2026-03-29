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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ComfyUI 客户端
 * 支持 ComfyUI 工作流提交、文件上传、任务轮询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComfyUIClient implements AIClient {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)  // 10分钟超时
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(new LoggingInterceptor())
            .build();

    @Override
    public void testConnection(AIConfig config) throws Exception {
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/system_stats";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("ComfyUI连接测试失败: HTTP " + response.code());
            }
        }
    }

    @Override
    public TestGenerateResult testGenerate(AIConfig config, TestGenerateRequest request) throws Exception {
        String serviceType = config.getServiceType();

        return switch (serviceType) {
            case "image" -> generateImage(config, request);
            case "image_to_image" -> generateImageToImage(config, request);
            case "video" -> generateVideo(config, request);
            case "video_frame" -> generateVideoFrame(config, request);
            case "sound_to_video" -> generateSoundToVideo(config, request);
            default -> throw new Exception("ComfyUI不支持的服务类型: " + serviceType);
        };
    }

    /**
     * 文生图
     */
    private TestGenerateResult generateImage(AIConfig config, TestGenerateRequest request) throws Exception {
        // 根据工作流文件名判断默认步数：Turbo模型用8步，其他用30步
        String workflowFilename = getWorkflowFilename(config);
        int defaultSteps = (workflowFilename != null && workflowFilename.toLowerCase().contains("turbo")) ? 8 : 30;

        Map<String, String> params = new HashMap<>();
        params.put("prompt", request.getPrompt());
        params.put("negativePrompt", request.getNegativePrompt());
        params.put("width", String.valueOf(request.getWidth() != null ? request.getWidth() : 1024));
        params.put("height", String.valueOf(request.getHeight() != null ? request.getHeight() : 1024));
        params.put("steps", String.valueOf(request.getSteps() != null ? request.getSteps() : defaultSteps));
        params.put("seed", String.valueOf(request.getSeed() != null && request.getSeed() > 0 ? request.getSeed() : System.currentTimeMillis() % 1000000000));

        String workflowJson = loadWorkflowTemplate(config, params);
        String promptId = submitPrompt(config, workflowJson);
        return waitForCompletion(config, promptId);
    }

    /**
     * 图生图
     */
    private TestGenerateResult generateImageToImage(AIConfig config, TestGenerateRequest request) throws Exception {
        if (request.getImageUrl() == null || request.getImageUrl().isEmpty()) {
            throw new Exception("图生图需要提供输入图片");
        }

        String imageFilename = uploadFile(config, request.getImageUrl(), "image");

        Map<String, String> params = new HashMap<>();
        params.put("prompt", request.getPrompt());
        params.put("negativePrompt", request.getNegativePrompt());
        params.put("image", imageFilename);
        params.put("width", String.valueOf(request.getWidth() != null ? request.getWidth() : 1024));
        params.put("height", String.valueOf(request.getHeight() != null ? request.getHeight() : 1024));
        params.put("steps", String.valueOf(request.getSteps() != null ? request.getSteps() : 8));
        params.put("seed", String.valueOf(request.getSeed() != null && request.getSeed() > 0 ? request.getSeed() : System.currentTimeMillis() % 1000000000));

        String workflowJson = loadWorkflowTemplate(config, params);
        String promptId = submitPrompt(config, workflowJson);
        return waitForCompletion(config, promptId);
    }

    /**
     * 文生视频
     */
    private TestGenerateResult generateVideo(AIConfig config, TestGenerateRequest request) throws Exception {
        // 前端已经将秒数转换为帧数，Wan模型生成的视频会少16帧，需要补上
        int frames = request.getDuration() != null ? request.getDuration() + 16 : 81;

        Map<String, String> params = new HashMap<>();
        params.put("prompt", request.getPrompt());
        params.put("negativePrompt", request.getNegativePrompt());
        params.put("width", String.valueOf(request.getWidth() != null ? request.getWidth() : 720));
        params.put("height", String.valueOf(request.getHeight() != null ? request.getHeight() : 1280));
        params.put("frames", String.valueOf(frames));
        params.put("steps", String.valueOf(request.getSteps() != null ? request.getSteps() : 30));
        params.put("seed", String.valueOf(request.getSeed() != null && request.getSeed() > 0 ? request.getSeed() : System.currentTimeMillis() % 1000000000));

        String workflowJson = loadWorkflowTemplate(config, params);
        log.info("===== 发送给ComfyUI的工作流 =====\n{}", workflowJson);
        String promptId = submitPrompt(config, workflowJson);
        return waitForVideoCompletion(config, promptId);
    }

    /**
     * 首尾帧视频生成
     */
    private TestGenerateResult generateVideoFrame(AIConfig config, TestGenerateRequest request) throws Exception {
        if (request.getFirstFrameUrl() == null || request.getLastFrameUrl() == null) {
            throw new Exception("首尾帧视频生成需要提供首帧和尾帧图片");
        }

        String firstFrameFilename = uploadFile(config, request.getFirstFrameUrl(), "image");
        String lastFrameFilename = uploadFile(config, request.getLastFrameUrl(), "image");
        log.info("上传首帧文件: {}, 尾帧文件: {}", firstFrameFilename, lastFrameFilename);

        // 前端已经将秒数转换为帧数，直接使用
        int frames = request.getDuration() != null ? request.getDuration() : 80;

        Map<String, String> params = new HashMap<>();
        params.put("prompt", request.getPrompt());
        params.put("negativePrompt", request.getNegativePrompt());
        params.put("firstFrame", firstFrameFilename);
        params.put("lastFrame", lastFrameFilename);
        params.put("width", String.valueOf(request.getWidth() != null ? request.getWidth() : 720));
        params.put("height", String.valueOf(request.getHeight() != null ? request.getHeight() : 1280));
        params.put("frames", String.valueOf(frames));
        params.put("steps", String.valueOf(request.getSteps() != null ? request.getSteps() : 8));
        params.put("seed", String.valueOf(request.getSeed() != null && request.getSeed() > 0 ? request.getSeed() : System.currentTimeMillis() % 1000000000));

        String workflowJson = loadWorkflowTemplate(config, params);
        log.info("===== 首尾帧视频生成工作流 =====\n{}", workflowJson);
        String promptId = submitPrompt(config, workflowJson);
        return waitForVideoCompletion(config, promptId);
    }

    /**
     * 语音图片转视频 (S2V)
     */
    private TestGenerateResult generateSoundToVideo(AIConfig config, TestGenerateRequest request) throws Exception {
        if (request.getImageUrl() == null || request.getImageUrl().isEmpty()) {
            throw new Exception("语音图片转视频需要提供输入图片");
        }
        if (request.getAudioUrl() == null || request.getAudioUrl().isEmpty()) {
            throw new Exception("语音图片转视频需要提供音频");
        }

        String imageFilename = uploadFile(config, request.getImageUrl(), "image");
        String audioFilename = uploadFile(config, request.getAudioUrl(), "audio");
        // 前端已经将秒数转换为帧数，直接使用
        int frames = request.getDuration() != null ? request.getDuration() : 80;

        Map<String, String> params = new HashMap<>();
        params.put("prompt", request.getPrompt());
        params.put("negativePrompt", request.getNegativePrompt());
        params.put("image", imageFilename);
        params.put("audio", audioFilename);
        params.put("width", String.valueOf(request.getWidth() != null ? request.getWidth() : 720));
        params.put("height", String.valueOf(request.getHeight() != null ? request.getHeight() : 1280));
        params.put("frames", String.valueOf(frames));
        params.put("steps", String.valueOf(request.getSteps() != null ? request.getSteps() : 8));
        params.put("seed", String.valueOf(request.getSeed() != null && request.getSeed() > 0 ? request.getSeed() : System.currentTimeMillis() % 1000000000));

        String workflowJson = loadWorkflowTemplate(config, params);
        String promptId = submitPrompt(config, workflowJson);
        return waitForVideoCompletion(config, promptId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 从配置中获取工作流文件名
     * 如果未配置，根据服务类型和模型名自动推断
     */
    private String getWorkflowFilename(AIConfig config) {
        // 优先从 settings 中获取
        if (config.getSettings() != null && !config.getSettings().isEmpty()) {
            try {
                JsonNode settingsNode = objectMapper.readTree(config.getSettings());
                String filename = settingsNode.path("workflow_filename").asText(null);
                if (filename != null && !filename.isEmpty()) {
                    return filename;
                }
            } catch (Exception e) {
                log.warn("解析settings失败", e);
            }
        }

        // 根据 serviceType 和 model 自动推断默认工作流文件名
        return getDefaultWorkflowFilename(config);
    }

    /**
     * 根据服务类型和模型名获取默认工作流文件名
     */
    private String getDefaultWorkflowFilename(AIConfig config) {
        String serviceType = config.getServiceType();
        String modelJson = config.getModel();
        String model = "";

        // 解析 model JSON 数组
        if (modelJson != null && !modelJson.isEmpty()) {
            try {
                JsonNode modelNode = objectMapper.readTree(modelJson);
                if (modelNode.isArray() && modelNode.size() > 0) {
                    model = modelNode.get(0).asText();
                } else if (modelNode.isTextual()) {
                    model = modelNode.asText();
                }
            } catch (Exception e) {
                log.warn("解析model字段失败", e);
            }
        }

        // 映射规则：model 名称 -> 工作流文件名
        return switch (serviceType) {
            case "image" -> {
                // Turbo 模型使用 turbo 工作流
                if (model.contains("turbo")) yield "z_image_turbo.json";
                yield "z_image_turbo.json"; // 默认使用 turbo
            }
            case "image_to_image" -> {
                // Flux2 Klein KV 模型
                if (model.contains("flux2") || model.contains("klein") || model.contains("kv")) {
                    yield "flux2_klein_9b_kv_i2i.json";
                }
                yield "flux2_klein_9b_kv_i2i.json"; // 默认使用 flux2
            }
            case "video" -> {
                // Wan2.2 文生视频
                yield "wan22_t2v.json";
            }
            case "video_frame" -> {
                // Wan2.2 首尾帧视频
                yield "wan22_flf2v.json";
            }
            case "sound_to_video" -> {
                // Wan2.2 语音图片转视频
                yield "wan22_s2v.json";
            }
            default -> null;
        };
    }

    /**
     * 加载工作流模板并替换占位符
     * 工作流文件必须是 API 格式
     * 占位符: {{PROMPT}}, {{NEGATIVE_PROMPT}}, {{WIDTH}}, {{HEIGHT}}, {{FRAMES}}, {{SEED}}, {{STEPS}}, {{IMAGE}}, {{AUDIO}}, {{FIRST_FRAME}}, {{LAST_FRAME}}
     */
    private String loadWorkflowTemplate(AIConfig config, Map<String, String> params) throws Exception {
        // 从 settings 中获取工作流文件名
        String workflowFilename = getWorkflowFilename(config);

        if (workflowFilename == null || workflowFilename.isEmpty()) {
            throw new Exception("未配置工作流文件");
        }

        // 从 classpath 读取工作流文件
        String resourcePath = "comfyui-workflows/" + workflowFilename;
        var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new Exception("工作流文件不存在: " + resourcePath);
        }

        String content = new String(inputStream.readAllBytes());
        inputStream.close();
        log.info("加载工作流模板: {}", resourcePath);

        // 设置默认参数
        String prompt = params.getOrDefault("prompt", "");
        String negativePrompt = params.getOrDefault("negativePrompt", "色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，背景人很多，倒着走");
        int width = Integer.parseInt(params.getOrDefault("width", "1024"));
        int height = Integer.parseInt(params.getOrDefault("height", "1024"));
        int frames = Integer.parseInt(params.getOrDefault("frames", "81"));
        int seed = Integer.parseInt(params.getOrDefault("seed", String.valueOf(System.currentTimeMillis() % 1000000000)));
        int steps = Integer.parseInt(params.getOrDefault("steps", "30"));
        String image = params.getOrDefault("image", "");
        String audio = params.getOrDefault("audio", "");
        String firstFrame = params.getOrDefault("firstFrame", "");
        String lastFrame = params.getOrDefault("lastFrame", "");

        // 替换占位符
        content = content.replace("{{PROMPT}}", escapeJson(prompt));
        content = content.replace("{{NEGATIVE_PROMPT}}", escapeJson(negativePrompt));
        content = content.replace("{{WIDTH}}", String.valueOf(width));
        content = content.replace("{{HEIGHT}}", String.valueOf(height));
        content = content.replace("{{FRAMES}}", String.valueOf(frames));
        content = content.replace("{{SEED}}", String.valueOf(seed));
        content = content.replace("{{STEPS}}", String.valueOf(steps));
        content = content.replace("{{STEPS_HALF}}", String.valueOf(steps / 2));
        content = content.replace("{{IMAGE}}", image);
        content = content.replace("{{AUDIO}}", audio);
        content = content.replace("{{FIRST_FRAME}}", firstFrame);
        content = content.replace("{{LAST_FRAME}}", lastFrame);

        return content;
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * 提交工作流到ComfyUI
     */
    private String submitPrompt(AIConfig config, String workflowJson) throws Exception {
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/prompt";

        String body = "{\"prompt\":" + workflowJson + "}";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new Exception("提交工作流失败: HTTP " + response.code() + " - " + errorBody);
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            return json.path("prompt_id").asText();
        }
    }

    /**
     * 上传文件到ComfyUI
     */
    private String uploadFile(AIConfig config, String dataUrl, String fileType) throws Exception {
        // 解析base64数据
        String base64Data;
        String extension = "png";

        if (dataUrl.startsWith("data:")) {
            // 解析 data URL 格式
            Pattern pattern = Pattern.compile("data:([^;]+);base64,(.+)");
            Matcher matcher = pattern.matcher(dataUrl);
            if (matcher.matches()) {
                String mimeType = matcher.group(1);
                base64Data = matcher.group(2);

                // 根据MIME类型确定扩展名
                if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
                    extension = "jpg";
                } else if (mimeType.contains("png")) {
                    extension = "png";
                } else if (mimeType.contains("webp")) {
                    extension = "webp";
                } else if (mimeType.contains("wav")) {
                    extension = "wav";
                } else if (mimeType.contains("mp3")) {
                    extension = "mp3";
                } else if (mimeType.contains("m4a")) {
                    extension = "m4a";
                }
            } else {
                throw new Exception("无效的data URL格式");
            }
        } else {
            // 假设是纯base64
            base64Data = dataUrl;
        }

        byte[] fileData = Base64.getDecoder().decode(base64Data);
        String filename = fileType + "_" + System.currentTimeMillis() + "." + extension;

        // 构建multipart请求
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/upload/image";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", filename,
                        RequestBody.create(fileData, MediaType.parse("application/octet-stream")))
                .addFormDataPart("overwrite", "true")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("上传文件失败: HTTP " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            String uploadedName = json.path("name").asText();
            String subfolder = json.path("subfolder").asText();

            if (!subfolder.isEmpty()) {
                return subfolder + "/" + uploadedName;
            }
            return uploadedName;
        }
    }

    /**
     * 等待图片生成完成
     */
    private TestGenerateResult waitForCompletion(AIConfig config, String promptId) throws Exception {
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/history/" + promptId;

        // 轮询等待完成，最多等待5分钟
        long startTime = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000;

        while (System.currentTimeMillis() - startTime < timeout) {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode json = objectMapper.readTree(response.body().string());
                    JsonNode historyItem = json.path(promptId);

                    if (!historyItem.isMissingNode()) {
                        boolean completed = historyItem.path("status").path("completed").asBoolean(false);
                        if (completed) {
                            // 获取输出图片
                            JsonNode outputs = historyItem.path("outputs");
                            return extractImageResult(config, outputs);
                        }
                    }
                }
            }

            // 等待2秒后重试
            Thread.sleep(2000);
        }

        throw new Exception("等待生成超时");
    }

    /**
     * 等待视频生成完成
     */
    private TestGenerateResult waitForVideoCompletion(AIConfig config, String promptId) throws Exception {
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/history/" + promptId;

        // 视频生成时间更长，最多等待30分钟
        long startTime = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000;

        while (System.currentTimeMillis() - startTime < timeout) {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode json = objectMapper.readTree(response.body().string());
                    JsonNode historyItem = json.path(promptId);

                    if (!historyItem.isMissingNode()) {
                        boolean completed = historyItem.path("status").path("completed").asBoolean(false);
                        if (completed) {
                            JsonNode outputs = historyItem.path("outputs");
                            return extractVideoResult(config, outputs);
                        }
                    }
                }
            }

            Thread.sleep(5000);
        }

        throw new Exception("等待视频生成超时");
    }

    /**
     * 提取图片结果
     */
    private TestGenerateResult extractImageResult(AIConfig config, JsonNode outputs) throws Exception {
        log.info("提取图片结果, outputs: {}", outputs.toPrettyString());

        // outputs 是一个对象，键是节点ID
        for (JsonNode output : outputs) {
            JsonNode images = output.path("images");
            if (images.isArray() && images.size() > 0) {
                String filename = images.get(0).path("filename").asText();
                String subfolder = images.get(0).path("subfolder").asText();
                String type = images.get(0).path("type").asText();
                log.info("找到图片: filename={}, subfolder={}, type={}", filename, subfolder, type);

                // 根据文件扩展名确定 MIME 类型
                String mimeType = "image/png"; // 默认
                if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (filename.toLowerCase().endsWith(".webp")) {
                    mimeType = "image/webp";
                } else if (filename.toLowerCase().endsWith(".gif")) {
                    mimeType = "image/gif";
                }

                // 获取图片数据
                byte[] imageData = getFile(config, filename, subfolder, type);
                String base64 = Base64.getEncoder().encodeToString(imageData);
                log.info("图片大小: {} bytes, base64长度: {}, mimeType: {}", imageData.length, base64.length(), mimeType);
                return TestGenerateResult.builder()
                        .imageUrl("data:" + mimeType + ";base64," + base64)
                        .build();
            }
        }
        throw new Exception("未找到生成的图片");
    }

    /**
     * 提取视频结果（只返回文件信息，不下载文件内容）
     */
    private TestGenerateResult extractVideoResult(AIConfig config, JsonNode outputs) throws Exception {
        log.info("提取视频结果, outputs: {}", outputs.toPrettyString());

        // 首先遍历所有输出，找到第一个有效的视频/动画输出
        String foundFilename = null;
        String foundSubfolder = null;
        String foundType = null;
        String foundCategory = null; // "videos", "gif", "images"

        for (JsonNode output : outputs) {
            // 检查视频输出 (SaveVideo 节点)
            JsonNode videos = output.path("videos");
            if (videos.isArray() && videos.size() > 0) {
                foundFilename = videos.get(0).path("filename").asText();
                foundSubfolder = videos.get(0).path("subfolder").asText();
                foundType = videos.get(0).path("type").asText();
                foundCategory = "videos";
                log.info("找到视频输出: filename={}, category={}", foundFilename, foundCategory);
                break;
            }

            // 检查GIF输出
            JsonNode gifs = output.path("gif");
            if (gifs.isArray() && gifs.size() > 0) {
                foundFilename = gifs.get(0).path("filename").asText();
                foundSubfolder = gifs.get(0).path("subfolder").asText();
                foundType = gifs.get(0).path("type").asText();
                foundCategory = "gif";
                log.info("找到GIF输出: filename={}, category={}", foundFilename, foundCategory);
                break;
            }
        }

        // 如果没有找到视频/GIF，检查 images 字段（SaveVideo 和 SaveAnimatedWEBP 都输出到这里）
        if (foundFilename == null) {
            for (JsonNode output : outputs) {
                JsonNode images = output.path("images");
                if (images.isArray() && images.size() > 0) {
                    String filename = images.get(0).path("filename").asText();
                    String lowerName = filename.toLowerCase();
                    // 视频格式：mp4, webm, 或者动画格式：webp, gif
                    if (lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") ||
                        lowerName.endsWith(".webp") || lowerName.endsWith(".gif")) {
                        foundFilename = filename;
                        foundSubfolder = images.get(0).path("subfolder").asText();
                        foundType = images.get(0).path("type").asText();
                        foundCategory = "images";
                        log.info("找到视频/动画输出: filename={}, category={}", foundFilename, foundCategory);
                        break;
                    }
                }
            }
        }

        if (foundFilename == null) {
            throw new Exception("未找到生成的视频");
        }

        // 根据文件扩展名确定 MIME 类型
        String mimeType;
        String lowerName = foundFilename.toLowerCase();

        if (lowerName.endsWith(".mp4")) {
            mimeType = "video/mp4";
        } else if (lowerName.endsWith(".webm")) {
            mimeType = "video/webm";
        } else if (lowerName.endsWith(".webp")) {
            mimeType = "image/webp";
        } else if (lowerName.endsWith(".gif")) {
            mimeType = "image/gif";
        } else {
            mimeType = "video/mp4"; // 默认
        }

        log.info("视频结果: filename={}, subfolder={}, type={}, mimeType={}", foundFilename, foundSubfolder, foundType, mimeType);

        // 返回文件信息，前端通过流式接口下载
        return TestGenerateResult.builder()
                .videoFilename(foundFilename)
                .videoSubfolder(foundSubfolder)
                .videoFileType(foundType)
                .configId(config.getId())
                .build();
    }

    /**
     * 从ComfyUI获取文件
     */
    private byte[] getFile(AIConfig config, String filename, String subfolder, String type) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(normalizeBaseUrl(config.getBaseUrl()));
        urlBuilder.append("/view?filename=").append(filename);
        urlBuilder.append("&type=").append(type);
        if (subfolder != null && !subfolder.isEmpty()) {
            urlBuilder.append("&subfolder=").append(subfolder);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("获取文件失败: HTTP " + response.code());
            }
            return response.body().bytes();
        }
    }

    /**
     * 获取文件流式URL（供前端直接访问）
     */
    public String getFileStreamUrl(AIConfig config, String filename, String subfolder, String type) {
        StringBuilder urlBuilder = new StringBuilder(normalizeBaseUrl(config.getBaseUrl()));
        urlBuilder.append("/view?filename=").append(filename);
        urlBuilder.append("&type=").append(type);
        if (subfolder != null && !subfolder.isEmpty()) {
            urlBuilder.append("&subfolder=").append(subfolder);
        }
        return urlBuilder.toString();
    }

    /**
     * 标准化URL，移除末尾斜杠
     */
    private String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 获取任务状态
     */
    public TestGenerateResult getTaskStatus(AIConfig config, String promptId) throws Exception {
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/history/" + promptId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("获取任务状态失败: HTTP " + response.code());
            }

            JsonNode json = objectMapper.readTree(response.body().string());
            JsonNode historyItem = json.path(promptId);

            if (historyItem.isMissingNode()) {
                return TestGenerateResult.builder()
                        .taskId(promptId)
                        .status("pending")
                        .build();
            }

            boolean completed = historyItem.path("status").path("completed").asBoolean(false);
            if (completed) {
                JsonNode outputs = historyItem.path("outputs");
                // 尝试提取视频或图片结果
                try {
                    return extractVideoResult(config, outputs);
                } catch (Exception e) {
                    return extractImageResult(config, outputs);
                }
            }

            return TestGenerateResult.builder()
                    .taskId(promptId)
                    .status("processing")
                    .build();
        }
    }
}