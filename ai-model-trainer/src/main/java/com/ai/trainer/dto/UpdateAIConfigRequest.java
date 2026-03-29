package com.ai.trainer.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新AI配置请求
 */
@Data
public class UpdateAIConfigRequest {

    private String name;

    private String provider;

    private String baseUrl;

    private String apiKey;

    private List<String> model;

    private String endpoint;

    private Integer priority;

    private Boolean isDefault;

    private Boolean isActive;

    private String settings;
}