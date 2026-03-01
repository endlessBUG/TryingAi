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

    private static final String PYTORCH_INDEX = "https://download.pytorch.org/whl/cu126";
    private static final String PYTORCH_PACKAGES = "torch==2.7.0 torchvision==0.22.0 torchaudio==2.7.0";

    @Override
    public void ensureEnvironment(Trainer trainer) {
        String envName = resolveEnvName(trainer);
        String pyVer = orDefault(trainer.getPythonVersion(), "3.10");
        condaService.createEnv(envName, pyVer);
        ensurePyTorch(envName);
        ensureRequirements(envName, trainer);
    }

    private void ensurePyTorch(String envName) {
        if (condaService.isModuleInstalled(envName, "torchaudio")) {
            log.info("PyTorch 套件已就绪: {}", envName);
            return;
        }
        log.info("安装 PyTorch 套件: {}", envName);
        condaService.pipInstall(envName, PYTORCH_PACKAGES, PYTORCH_INDEX);
    }

    private void ensureRequirements(String envName, Trainer trainer) {
        String reqPath = trainer.getPath() + File.separator + "requirements.txt";
        if (!new File(reqPath).exists()) return;
        if (condaService.isModuleInstalled(envName, "diffusers")) {
            log.info("AI Toolkit 依赖已就绪: {}", envName);
            return;
        }
        log.info("安装 AI Toolkit 依赖: {}", envName);
        condaService.installRequirements(envName, reqPath);
    }

    @Override
    public void executeTraining(TrainingTask task, Trainer trainer, TaskManagerService taskMgr) {
        String envName = resolveEnvName(trainer);
        task.setCondaEnvName(envName);
        taskMgr.setTaskCondaEnvName(task.getTaskId(), envName);

        String configPath = writeYamlConfig(task);
        task.setConfigPath(configPath);

        String outputDir = generateOutputDir(task);
        task.setOutputPath(outputDir);

        String command = "python run.py \"" + configPath + "\"";
        File workDir = new File(trainer.getPath());
        String fullCommand = condaService.buildFullCommand(envName, command);
        task.setExecuteCommand(fullCommand);
        taskMgr.setTaskExecuteCommand(task.getTaskId(), fullCommand);

        log.info("执行 ai-toolkit 训练: cmd={}, workDir={}", fullCommand, workDir);
        Process process = condaService.startInEnv(envName, command, workDir);
        taskMgr.setTaskProcessId(task.getTaskId(), process.pid());

        String logPath = properties.getLogDir() + "/training_" + task.getTaskId() + ".log";
        task.setLogPath(logPath);
        taskMgr.setTaskLogPath(task.getTaskId(), logPath);
        readProcessOutput(process, task.getTaskId(), logPath, taskMgr);

        waitForProcess(process);
    }

    @Override
    public void stopTraining(TrainingTask task) {
        if (task.getProcessId() == null) return;
        ProcessHandle.of(task.getProcessId()).ifPresent(ProcessHandle::destroy);
    }

    private String writeYamlConfig(TrainingTask task) {
        try {
            String configDir = properties.getConfigDir();
            Path path = Path.of(configDir, task.getTaskId() + ".yaml");
            String yaml = normalizeYamlPaths(orEmpty(task.getYamlConfig()));
            Files.writeString(path, yaml);
            return path.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new TrainingException("写入 YAML 配置失败: " + e.getMessage());
        }
    }

    /** 将 YAML 中所有 Windows 反斜杠路径转为正斜杠，防止 YAML 解析转义错误 */
    private String normalizeYamlPaths(String yaml) {
        return yaml.replace("\\", "/");
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
