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
    private String dataDir = "./data";
    private String uploadDir = "./data/uploads";
    private String datasetDir = "./data/datasets";
    private String outputDir = "./data/outputs";
    private String configDir = "./data/configs";
    private String logDir = "./data/logs";
    private String supportedFormats = "jpg,jpeg,png,webp,bmp";
    private int maxConcurrentTasks = 2;

    public String[] getSupportedFormatArray() {
        return supportedFormats.split(",");
    }
}
