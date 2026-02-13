package com.ai.trainer.service;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.model.TaskStatus;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.TrainerRepository;
import com.ai.trainer.strategy.TrainingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainerProperties properties;
    private final TaskManagerService taskManager;
    private final TrainerRepository trainerRepo;
    private final List<TrainingStrategy> strategies;

    @Async
    public void startTraining(String taskId) {
        TrainingTask task = taskManager.getTask(taskId);
        if (task == null) throw new TrainingException("任务不存在: " + taskId);

        try {
            taskManager.updateTaskStatus(taskId, TaskStatus.PREPARING);

            Trainer trainer = ensureTrainerReady(task);
            TrainingStrategy strategy = resolveStrategy(trainer);

            log.info("使用训练策略: {}", strategy.getClass().getSimpleName());
            strategy.ensureEnvironment(trainer);

            taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING);
            strategy.executeTraining(task, trainer, taskManager);

            taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("训练失败: {}", taskId, e);
            taskManager.setTaskError(taskId, e.getMessage());
        }
    }

    public void stopTraining(String taskId) {
        TrainingTask task = taskManager.getTask(taskId);
        if (task == null || task.getProcessId() == null) return;
        try {
            Trainer trainer = task.getTrainerId() != null
                    ? trainerRepo.findById(task.getTrainerId()).orElse(null)
                    : null;
            if (trainer != null) {
                TrainingStrategy strategy = resolveStrategy(trainer);
                strategy.stopTraining(task);
            } else {
                ProcessHandle.of(task.getProcessId()).ifPresent(ProcessHandle::destroy);
            }
            taskManager.cancelTask(taskId);
            log.info("已停止训练任务: {}", taskId);
        } catch (Exception e) {
            log.error("停止训练失败: {}", taskId, e);
        }
    }

    private TrainingStrategy resolveStrategy(Trainer trainer) {
        return strategies.stream()
                .filter(s -> s.supports(trainer))
                .findFirst()
                .orElseThrow(() -> new TrainingException("未找到匹配的训练策略: " + trainer.getName()));
    }

    // === 训练器就绪检查（git clone 逻辑） ===

    private Trainer ensureTrainerReady(TrainingTask task) {
        if (task.getTrainerId() == null) {
            throw new TrainingException("任务未关联训练器");
        }
        Trainer trainer = trainerRepo.findById(task.getTrainerId()).orElse(null);
        if (trainer == null) {
            throw new TrainingException("训练器不存在: " + task.getTrainerId());
        }
        if (hasValidPath(trainer.getPath())) {
            return trainer;
        }
        if (trainer.getGitUrl() == null || trainer.getGitUrl().isBlank()) {
            throw new TrainingException("训练器未配置路径且无 Git 地址，无法启动训练");
        }
        cloneAndUpdatePath(trainer, task);
        return trainer;
    }

    private boolean hasValidPath(String path) {
        return path != null && !path.isBlank() && new File(path).isDirectory();
    }

    private void cloneAndUpdatePath(Trainer trainer, TrainingTask task) {
        String targetDir = resolveCloneDir(trainer);
        File target = new File(targetDir);
        if (target.exists() && target.isDirectory()) {
            log.info("训练器目录已存在，跳过 clone: {}", targetDir);
        } else {
            log.info("从 Git 克隆训练器: {} -> {}", trainer.getGitUrl(), targetDir);
            executeGitClone(trainer.getGitUrl(), targetDir);
        }
        trainer.setPath(target.getAbsolutePath());
        trainerRepo.save(trainer);
        task.setTrainerPath(target.getAbsolutePath());
        log.info("训练器路径已更新为: {}", target.getAbsolutePath());
    }

    private String resolveCloneDir(Trainer trainer) {
        String repoName = extractRepoName(trainer.getGitUrl());
        return Path.of(properties.getDataDir(), "trainers", trainer.getId() + "_" + repoName)
                .toAbsolutePath().toString();
    }

    private String extractRepoName(String gitUrl) {
        String name = gitUrl;
        if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        return name.isBlank() ? "repo" : name;
    }

    private void executeGitClone(String gitUrl, String targetDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl, targetDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[git clone] {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new TrainingException("Git clone 失败，退出码: " + exitCode);
            }
        } catch (TrainingException e) {
            throw e;
        } catch (Exception e) {
            throw new TrainingException("Git clone 异常: " + e.getMessage());
        }
    }
}
