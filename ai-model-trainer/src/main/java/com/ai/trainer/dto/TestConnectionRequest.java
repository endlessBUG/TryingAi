package com.ai.trainer.dto;

import lombok.Data;

import java.util.List;

/**
 * 测试连接请求
 */
@Data
public class TestConnectionRequest {

    private String baseUrl;

    private String apiKey;

    private List<String> model;

    private String provider;

    private String serviceType;

    private String endpoint;
}