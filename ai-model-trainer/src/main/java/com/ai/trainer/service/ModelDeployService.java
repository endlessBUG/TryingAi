package com.ai.trainer.service;

import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

/**
 * 模型自动部署服务：训练完成后将 LoRA 模型文件复制到 ComfyUI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDeployService {

    private final SystemConfigRepository configRepo;

    public void deployToComfyUI(TrainingTask task) {
        String comfyuiPath = getComfyUIPath();
        if (comfyuiPath == null || comfyuiPath.isBlank()) {
            log.info("未配置 ComfyUI 路径，跳过自动部署");
            return;
        }
        if (task.getOutputPath() == null) {
            log.warn("任务无输出路径，跳过部署: {}", task.getTaskId());
            return;
        }
        Path loraDir = buildLoraDir(comfyuiPath);
        copyModelFiles(task, loraDir);
    }

    private String getComfyUIPath() {
        return configRepo.findById("comfyui.path")
                .map(c -> c.getConfigValue())
                .orElse(null);
    }

    private Path buildLoraDir(String comfyuiPath) {
        Path loraDir = Path.of(comfyuiPath, "models", "loras");
        try {
            Files.createDirectories(loraDir);
        } catch (IOException e) {
            log.error("创建 LoRA 目录失败: {}", loraDir, e);
        }
        return loraDir;
    }

    private void copyModelFiles(TrainingTask task, Path loraDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                Path.of(task.getOutputPath()), "*.safetensors")) {
            for (Path file : stream) {
                Path target = loraDir.resolve(file.getFileName());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("已部署 LoRA 模型: {} -> {}", file.getFileName(), target);
            }
        } catch (IOException e) {
            log.error("部署 LoRA 模型失败: {}", e.getMessage());
        }
    }
}
