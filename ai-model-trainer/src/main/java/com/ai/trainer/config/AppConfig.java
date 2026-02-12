package com.ai.trainer.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AppConfig {

    private final TrainerProperties properties;

    @PostConstruct
    public void init() {
        createDirectories();
    }

    private void createDirectories() {
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getUploadDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getDatasetDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getOutputDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getConfigDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getLogDir());
        log.info("数据目录初始化完成");
    }
}
