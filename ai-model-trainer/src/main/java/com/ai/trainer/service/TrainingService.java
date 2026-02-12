package com.ai.trainer.service;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.model.TaskStatus;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.TrainerRepository;
import com.ai.trainer.util.FileUtil;
import com.ai.trainer.util.YamlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainerProperties properties;
    private final TaskManagerService taskManager;
    private final TrainerRepository trainerRepo;

    @Async
    public void startTraining(String taskId) {
        TrainingTask task = taskManager.getTask(taskId);
        if (task == null) throw new TrainingException("任务不存在: " + taskId);

        try {
            taskManager.updateTaskStatus(taskId, TaskStatus.PREPARING);
            ensureTrainerReady(task);
            String configPath = generateConfig(task);
            task.setConfigPath(configPath);

            String outputDir = FileUtil.generateUniqueDir(properties.getOutputDir(), task.getTaskName());
            task.setOutputPath(outputDir);

            taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING);
            executeTraining(task);
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
            ProcessHandle.of(task.getProcessId()).ifPresent(ProcessHandle::destroy);
            taskManager.cancelTask(taskId);
            log.info("已停止训练任务: {}", taskId);
        } catch (Exception e) {
            log.error("停止训练失败: {}", taskId, e);
        }
    }

    private String generateConfig(TrainingTask task) throws IOException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("job", Map.of("name", task.getTaskName()));
        config.put("model", Map.of("name_or_path", task.getTrainingConfig().getBaseModel()));

        String configPath = properties.getConfigDir() + "/" + task.getTaskId() + ".yaml";
        YamlUtil.writeYaml(configPath, config);
        return configPath;
    }

    private void executeTraining(TrainingTask task) throws Exception {
        String command = String.format("%s run.py --config %s",
                properties.getPythonPath(), task.getConfigPath());

        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        pb.directory(new File(properties.getAiToolkitPath()));
        pb.redirectErrorStream(true);

        String logPath = properties.getLogDir() + "/training_" + task.getTaskId() + ".log";
        task.setLogPath(logPath);

        Process process = pb.start();
        taskManager.setTaskProcessId(task.getTaskId(), process.pid());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             Writer logWriter = new FileWriter(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                logWriter.write(line + "\n");
                logWriter.flush();
                parseProgress(task.getTaskId(), line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new TrainingException("训练进程退出码: " + exitCode);
        }
    }

    private void ensureTrainerReady(TrainingTask task) {
        if (task.getTrainerId() == null) {
            throw new TrainingException("任务未关联训练器");
        }
        Trainer trainer = trainerRepo.findById(task.getTrainerId()).orElse(null);
        if (trainer == null) {
            throw new TrainingException("训练器不存在: " + task.getTrainerId());
        }
        if (hasValidPath(trainer.getPath())) {
            return;
        }
        if (trainer.getGitUrl() == null || trainer.getGitUrl().isBlank()) {
            throw new TrainingException("训练器未配置路径且无 Git 地址，无法启动训练");
        }
        cloneAndUpdatePath(trainer, task);
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

    private void parseProgress(String taskId, String line) {
        if (!line.contains("step")) return;
        try {
            // 简单解析: "Step 100/1000"
            String[] parts = line.replaceAll(".*[Ss]tep\\s*", "").split("[/\\s]");
            if (parts.length >= 2) {
                int current = Integer.parseInt(parts[0].trim());
                int total = Integer.parseInt(parts[1].trim());
                double progress = (double) current / total * 100;
                taskManager.updateTaskProgress(taskId, progress, current, total);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
