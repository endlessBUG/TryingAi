package com.ai.trainer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建AI配置请求
 */
@Data
public class CreateAIConfigRequest {

    @NotBlank(message = "服务类型不能为空")
    private String serviceType;

    private String provider;

    @NotBlank(message = "配置名称不能为空")
    private String name;

    @NotBlank(message = "Base URL不能为空")
    private String baseUrl;

    private String apiKey;

    private List<String> model;

    private String endpoint;

    private Integer priority = 0;

    private Boolean isDefault = false;

    private String settings;
}