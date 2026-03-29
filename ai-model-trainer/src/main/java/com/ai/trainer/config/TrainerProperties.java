package com.ai.trainer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "trainer")
public class TrainerProperties {
    private String aiToolkitPath = "./ai-toolkit";
    private String pythonPath = "python";
    private String dataDir = System.getProperty("user.home") + "/tryingai";
    private String uploadDir = System.getProperty("user.home") + "/tryingai/uploads";
    private String datasetDir = System.getProperty("user.home") + "/tryingai/datasets";
    private String outputDir = System.getProperty("user.home") + "/tryingai/outputs";
    private String configDir = System.getProperty("user.home") + "/tryingai/configs";
    private String logDir = System.getProperty("user.home") + "/tryingai/logs";
    private String modelDir = System.getProperty("user.home") + "/tryingai/models";
    private String comfyuiDir = System.getProperty("user.home") + "/ai/trainer/comfyui";
    private String supportedFormats = "jpg,jpeg,png,webp,bmp";
    private int maxConcurrentTasks = 2;
    private String defaultCondaEnv = "wan22";

    public String[] getSupportedFormatArray() {
        return supportedFormats.split(",");
    }
}