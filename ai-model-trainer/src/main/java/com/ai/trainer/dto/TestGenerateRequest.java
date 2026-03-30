package com.ai.trainer.dto;

import lombok.Data;

/**
 * 测试生成请求
 */
@Data
public class TestGenerateRequest {

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 输入图片URL (base64或URL)
     */
    private String imageUrl;

    /**
     * 音频URL (base64或URL)，用于语音图片转视频
     */
    private String audioUrl;

    /**
     * 首帧图片URL
     */
    private String firstFrameUrl;

    /**
     * 尾帧图片URL
     */
    private String lastFrameUrl;

    /**
     * 图片/视频尺寸
     */
    private String size;

    /**
     * 自定义宽度
     */
    private Integer width;

    /**
     * 自定义高度
     */
    private Integer height;

    /**
     * 负面提示词
     */
    private String negativePrompt;

    /**
     * TTS音色
     */
    private String voice;

    /**
     * 采样步数（默认30）
     */
    private Integer steps;

    /**
     * 随机种子（默认时间戳）
     */
    private Long seed;

    /**
     * 时长（秒），用于视频生成
     */
    private Integer duration;

    /**
     * 帧数，用于视频生成
     */
    private Integer frames;

    /**
     * TTS模式: speech 或 clone
     */
    private String ttsMode;

    /**
     * 参考音频URL (base64)
     */
    private String referenceAudioUrl;

    /**
     * 参考文本
     */
    private String referenceText;

    /**
     * 相机镜头动作 (用于相机控制视频生成)
     * 可选值: Zoom In, Zoom Out, Pan Left, Pan Right, Tilt Up, Tilt Down, Static, Rotate
     */
    private String cameraPose;
}