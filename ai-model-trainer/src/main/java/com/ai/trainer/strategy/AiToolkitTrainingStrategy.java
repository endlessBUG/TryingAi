package com.ai.trainer.strategy;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.service.CondaService;
import com.ai.trainer.service.TaskManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AiToolkitTrainingStrategy implements TrainingStrategy {

    private final CondaService condaService;
    private final TrainerProperties properties;

    @Override
    public boolean supports(Trainer trainer) {
        String name = orEmpty(trainer.getName()).toLowerCase();
        String git = orEmpty(trainer.getGitUrl()).toLowerCase();
        return name.contains("ai-toolkit") || name.contains("ai_toolkit")
                || git.contains("ai-toolkit") || git.contains("ai_toolkit");
    }

    @Override
    public void ensureEnvironment(Trainer trainer) {
        String envName = resolveEnvName(trainer);
        String pyVer = orDefault(trainer.getPythonVersion(), "3.10");
        condaService.createEnv(envName, pyVer);
        installDependencies(envName, trainer);
    }

    @Override
    public void executeTraining(TrainingTask task, Trainer trainer, TaskManagerService taskMgr) {
        String envName = resolveEnvName(trainer);
        String configPath = writeYamlConfig(task);
        task.setConfigPath(configPath);

        String outputDir = generateOutputDir(task);
        task.setOutputPath(outputDir);

        String command = "python run.py --config " + configPath;
        File workDir = new File(trainer.getPath());

        log.info("执行 ai-toolkit 训练: env={}, workDir={}", envName, workDir);
        Process process = condaService.startInEnv(envName, command, workDir);
        taskMgr.setTaskProcessId(task.getTaskId(), process.pid());

        String logPath = properties.getLogDir() + "/training_" + task.getTaskId() + ".log";
        task.setLogPath(logPath);
        readProcessOutput(process, task.getTaskId(), logPath, taskMgr);

        waitForProcess(process);
    }

    @Override
    public void stopTraining(TrainingTask task) {
        if (task.getProcessId() == null) return;
        ProcessHandle.of(task.getProcessId()).ifPresent(ProcessHandle::destroy);
    }

    private void installDependencies(String envName, Trainer trainer) {
        String reqPath = trainer.getPath() + File.separator + "requirements.txt";
        condaService.installRequirements(envName, reqPath);
    }

    private String writeYamlConfig(TrainingTask task) {
        try {
            String configDir = properties.getConfigDir();
            Path path = Path.of(configDir, task.getTaskId() + ".yaml");
            Files.writeString(path, orEmpty(task.getYamlConfig()));
            return path.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new TrainingException("写入 YAML 配置失败: " + e.getMessage());
        }
    }

    private String generateOutputDir(TrainingTask task) {
        Path path = Path.of(properties.getOutputDir(), task.getTaskId());
        path.toFile().mkdirs();
        return path.toAbsolutePath().toString();
    }

    private void readProcessOutput(Process process, String taskId, String logPath, TaskManagerService taskMgr) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             Writer logWriter = new FileWriter(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                logWriter.write(line + "\n");
                logWriter.flush();
                parseProgress(taskId, line, taskMgr);
            }
        } catch (IOException e) {
            log.error("读取训练输出失败: {}", e.getMessage());
        }
    }

    private void waitForProcess(Process process) {
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new TrainingException("训练进程退出码: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TrainingException("训练被中断");
        }
    }

    private void parseProgress(String taskId, String line, TaskManagerService taskMgr) {
        if (!line.contains("step")) return;
        try {
            String[] parts = line.replaceAll(".*[Ss]tep\\s*", "").split("[/\\s]");
            if (parts.length >= 2) {
                int current = Integer.parseInt(parts[0].trim());
                int total = Integer.parseInt(parts[1].trim());
                double progress = (double) current / total * 100;
                taskMgr.updateTaskProgress(taskId, progress, current, total);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private String resolveEnvName(Trainer trainer) {
        if (trainer.getCondaEnvName() != null && !trainer.getCondaEnvName().isBlank()) {
            return trainer.getCondaEnvName();
        }
        return "trainer_" + trainer.getId();
    }

    private String orEmpty(String s) { return s == null ? "" : s; }
    private String orDefault(String s, String def) { return s == null || s.isBlank() ? def : s; }
}
