package com.ai.trainer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试生成结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerateResult {

    /**
     * 文本生成结果
     */
    private String text;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 视频URL
     */
    private String videoUrl;

    /**
     * 音频URL
     */
    private String audioUrl;

    /**
     * 异步任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 视频文件名（用于流式下载）
     */
    private String videoFilename;

    /**
     * 视频文件子目录
     */
    private String videoSubfolder;

    /**
     * 视频文件类型（output等）
     */
    private String videoFileType;

    /**
     * 配置ID（用于下载时获取ComfyUI地址）
     */
    private Long configId;
}