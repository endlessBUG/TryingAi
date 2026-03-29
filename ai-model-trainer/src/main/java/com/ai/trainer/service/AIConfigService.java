package com.ai.trainer.service;

import com.ai.trainer.client.AIClient;
import com.ai.trainer.client.ComfyUIClient;
import com.ai.trainer.client.OpenAIClient;
import com.ai.trainer.dto.*;
import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.model.AIConfig;
import com.ai.trainer.repository.AIConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIConfigService {

    private final AIConfigRepository configRepository;
    private final OpenAIClient openAIClient;
    private final ComfyUIClient comfyUIClient;
    private final ObjectMapper objectMapper;

    // ==================== CRUD 操作 ====================

    /**
     * 获取所有配置
     */
    public List<AIConfig> listAll() {
        return configRepository.findAllByOrderByPriorityDescCreatedAtDesc();
    }

    /**
     * 按服务类型获取配置列表
     */
    public List<AIConfig> listByType(String serviceType) {
        if (serviceType == null || serviceType.isEmpty()) {
            return listAll();
        }
        return configRepository.findByServiceTypeOrderByPriorityDescCreatedAtDesc(serviceType);
    }

    /**
     * 获取单个配置
     */
    public AIConfig getById(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new TrainingException("配置不存在: " + id));
    }

    /**
     * 创建配置
     */
    @Transactional
    public AIConfig create(CreateAIConfigRequest request) {
        AIConfig config = new AIConfig();
        config.setServiceType(request.getServiceType());
        config.setProvider(request.getProvider());
        config.setName(request.getName());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(request.getApiKey());
        config.setModel(toJsonString(request.getModel()));
        config.setEndpoint(request.getEndpoint());
        config.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        config.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        config.setIsActive(true);
        config.setSettings(request.getSettings());

        // 自动设置端点
        if (config.getEndpoint() == null || config.getEndpoint().isEmpty()) {
            config.setEndpoint(getDefaultEndpoint(config));
        }

        return configRepository.save(config);
    }

    /**
     * 更新配置
     */
    @Transactional
    public AIConfig update(Long id, UpdateAIConfigRequest request) {
        AIConfig config = getById(id);

        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getProvider() != null) {
            config.setProvider(request.getProvider());
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(request.getBaseUrl());
        }
        if (request.getApiKey() != null) {
            config.setApiKey(request.getApiKey());
        }
        if (request.getModel() != null) {
            config.setModel(toJsonString(request.getModel()));
        }
        if (request.getEndpoint() != null) {
            config.setEndpoint(request.getEndpoint());
        }
        if (request.getPriority() != null) {
            config.setPriority(request.getPriority());
        }
        if (request.getIsDefault() != null) {
            config.setIsDefault(request.getIsDefault());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getSettings() != null) {
            config.setSettings(request.getSettings());
        }

        return configRepository.save(config);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void delete(Long id) {
        if (!configRepository.existsById(id)) {
            throw new TrainingException("配置不存在: " + id);
        }
        configRepository.deleteById(id);
    }

    /**
     * 切换配置激活状态
     */
    @Transactional
    public AIConfig toggleActive(Long id) {
        AIConfig config = getById(id);
        config.setIsActive(!config.getIsActive());
        return configRepository.save(config);
    }

    // ==================== 测试操作 ====================

    /**
     * 测试连接
     */
    public void testConnection(TestConnectionRequest request) {
        AIConfig config = new AIConfig();
        config.setServiceType(request.getServiceType());
        config.setProvider(request.getProvider());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(request.getApiKey());
        config.setModel(toJsonString(request.getModel()));
        config.setEndpoint(request.getEndpoint());

        try {
            AIClient client = getClient(config.getProvider());
            client.testConnection(config);
        } catch (Exception e) {
            throw new TrainingException("连接测试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试生成
     */
    public TestGenerateResult testGenerate(Long id, TestGenerateRequest request) {
        AIConfig config = getById(id);

        if (!config.getIsActive()) {
            throw new TrainingException("配置未激活");
        }

        try {
            AIClient client = getClient(config.getProvider());
            return client.testGenerate(config, request);
        } catch (Exception e) {
            log.error("测试生成失败", e);
            throw new TrainingException("测试生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取测试任务状态
     */
    public TestGenerateResult getTaskStatus(Long configId, String taskId) {
        // 对于ComfyUI，需要轮询历史记录
        AIConfig config = getById(configId);

        if ("comfyui".equals(config.getProvider())) {
            // ComfyUI的任务状态查询
            try {
                return comfyUIClient.getTaskStatus(config, taskId);
            } catch (Exception e) {
                throw new TrainingException("获取任务状态失败: " + e.getMessage(), e);
            }
        }

        // 对于其他类型，返回不支持
        throw new TrainingException("该厂商不支持任务状态查询");
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据厂商获取对应的客户端
     */
    private AIClient getClient(String provider) {
        if (provider == null) {
            return openAIClient;
        }

        return switch (provider.toLowerCase()) {
            case "comfyui" -> comfyUIClient;
            default -> openAIClient; // OpenAI兼容格式（包括chatfire, openai, gemini等）
        };
    }

    /**
     * 根据厂商和服务类型获取默认端点
     */
    private String getDefaultEndpoint(AIConfig config) {
        String provider = config.getProvider();
        String serviceType = config.getServiceType();

        if (provider == null) {
            provider = "openai";
        }

        return switch (provider.toLowerCase()) {
            case "comfyui" -> "/prompt";
            case "gemini", "google" -> serviceType.equals("text") ?
                    "/v1beta/models/{model}:generateContent" : "/v1beta/models/{model}:generateContent";
            case "chatfire" -> switch (serviceType) {
                case "text" -> "/chat/completions";
                case "image" -> "/images/generations";
                case "video" -> "/video/generations";
                case "text_to_speech" -> "/audio/speech";
                default -> "/chat/completions";
            };
            case "volces", "volcengine" -> switch (serviceType) {
                case "video" -> "/contents/generations/tasks";
                default -> "/chat/completions";
            };
            case "fishaudio" -> "/v1/tts";
            default -> switch (serviceType) {
                case "text" -> "/chat/completions";
                case "image" -> "/images/generations";
                case "image_to_image" -> "/images/edits";
                case "video" -> "/videos";
                case "text_to_speech" -> "/audio/speech";
                default -> "/chat/completions";
            };
        };
    }

    /**
     * 将列表转换为JSON字符串
     */
    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return String.join(",", list);
        }
    }
}