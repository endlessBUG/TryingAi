package com.ai.trainer.config;

import com.ai.trainer.model.SystemConfig;
import com.ai.trainer.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AppConfig {

    private final TrainerProperties properties;
    private final SystemConfigRepository configRepo;

    @PostConstruct
    public void init() {
        createDirectories();
        detectConda();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                connector.setProperty("maxHttpHeaderSize", "65536");
                connector.setProperty("maxKeepAliveRequests", "1");
                connector.setProperty("connectionTimeout", "3600000");
            });
        };
    }

    private void createDirectories() {
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getUploadDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getDatasetDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getOutputDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getConfigDir());
        com.ai.trainer.util.FileUtil.ensureDirectory(properties.getLogDir());
        log.info("数据目录初始化完成");
    }

    private void detectConda() {
        if (configRepo.findById("conda.path").isPresent()) {
            log.info("Conda 路径已配置，跳过自动检测");
            return;
        }
        String condaPath = findCondaPath();
        if (condaPath != null) {
            configRepo.save(new SystemConfig("conda.path", condaPath));
            log.info("自动检测到 Conda 路径: {}", condaPath);
        } else {
            log.warn("未检测到 Conda，请在 Conda 配置页面手动设置");
        }
    }

    private String findCondaPath() {
        String fromCommand = detectViaCommand();
        if (fromCommand != null) return fromCommand;
        return detectViaCommonPaths();
    }

    private String detectViaCommand() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] cmd = isWindows
                ? new String[]{"cmd", "/c", "where conda"}
                : new String[]{"/bin/sh", "-c", "which conda"};
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank() && Files.exists(Path.of(line.trim()))) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            log.debug("通过命令检测 Conda 失败: {}", e.getMessage());
        }
        return null;
    }

    private String detectViaCommonPaths() {
        String userHome = System.getProperty("user.home");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        List<String> candidates = isWindows
                ? List.of(
                    userHome + "\\miniconda3\\Scripts\\conda.exe",
                    userHome + "\\anaconda3\\Scripts\\conda.exe",
                    userHome + "\\Miniconda3\\Scripts\\conda.exe",
                    userHome + "\\Anaconda3\\Scripts\\conda.exe",
                    "C:\\ProgramData\\miniconda3\\Scripts\\conda.exe",
                    "C:\\ProgramData\\anaconda3\\Scripts\\conda.exe")
                : List.of(
                    userHome + "/miniconda3/bin/conda",
                    userHome + "/anaconda3/bin/conda",
                    "/opt/miniconda3/bin/conda",
                    "/opt/anaconda3/bin/conda",
                    "/usr/local/bin/conda");

        for (String path : candidates) {
            if (Files.exists(Path.of(path))) return path;
        }
        return null;
    }
}
