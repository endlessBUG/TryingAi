package com.ai.trainer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI服务配置实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_configs")
public class AIConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 服务类型: text, image, image_to_image, video, video_frame, sound_to_video, text_to_speech
     */
    @Column(nullable = false, length = 50)
    private String serviceType;

    /**
     * 厂商标识: openai, comfyui, chatfire, gemini, volces, fishaudio 等
     */
    @Column(length = 50)
    private String provider;

    /**
     * 配置名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * API基础URL
     */
    @Column(nullable = false, length = 500)
    private String baseUrl;

    /**
     * API密钥
     */
    @Column(length = 500)
    private String apiKey;

    /**
     * 模型列表，JSON数组格式存储
     */
    @Column(columnDefinition = "TEXT")
    private String model;

    /**
     * API端点
     */
    @Column(length = 255)
    private String endpoint;

    /**
     * 优先级，数值越大优先级越高
     */
    @Column(nullable = false)
    private Integer priority = 0;

    /**
     * 是否默认配置
     */
    @Column(nullable = false)
    private Boolean isDefault = false;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * 扩展设置，JSON格式存储（如ComfyUI工作流配置）
     */
    @Column(columnDefinition = "TEXT")
    private String settings;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}