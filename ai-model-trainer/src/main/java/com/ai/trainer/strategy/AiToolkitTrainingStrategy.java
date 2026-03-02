package com.ai.trainer.strategy;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.service.CondaService;
import com.ai.trainer.service.LogBroadcastService;
import com.ai.trainer.service.TaskManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AiToolkitTrainingStrategy implements TrainingStrategy {

    private final CondaService condaService;
    private final TrainerProperties properties;
    private final LogBroadcastService logBroadcast;

    @Override
    public boolean supports(Trainer trainer) {
        String name = orEmpty(trainer.getName()).toLowerCase();
        String git = orEmpty(trainer.getGitUrl()).toLowerCase();
        return name.contains("ai-toolkit") || name.contains("ai_toolkit")
                || git.contains("ai-toolkit") || git.contains("ai_toolkit");
    }

    private static final String PYTORCH_INDEX = "https://download.pytorch.org/whl/cu126";
    private static final String PYTORCH_PACKAGES = "torch==2.7.0 torchvision==0.22.0 torchaudio==2.7.0";

    /** 模型目录名/ID -> ModelScope 模型 ID，用于下载 */
    private static final Map<String, String> MODEL_TO_SCOPE_ID = Map.ofEntries(
            Map.entry("Wan2.2-T2V-A14B-Diffusers-bf16", "zhaotutu12/Wan2.2-T2V-A14B-Diffusers-bf16"),
            Map.entry("ai-toolkit/Wan2.2-T2V-A14B-Diffusers-bf16", "zhaotutu12/Wan2.2-T2V-A14B-Diffusers-bf16"),
            Map.entry("Wan2.2-I2V-14B-480P-Diffusers", "ai-toolkit/Wan2.2-I2V-14B-480P-Diffusers"),
            Map.entry("FLUX.1-dev", "black-forest-labs/FLUX.1-dev"),
            Map.entry("FLUX.1-schnell", "black-forest-labs/FLUX.1-schnell")
    );

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

        String modelId = resolveModelPath(task);
        String configPath = writeYamlConfig(task);
        task.setConfigPath(configPath);

        String outputDir = generateOutputDir(task);
        task.setOutputPath(outputDir);

        ensureModel(task, envName, modelId);

        String trainerPath = trainer.getPath();
        String command = "cd '" + trainerPath + "' && python -u run.py '" + configPath + "'";
        String fullCommand = condaService.buildFullCommand(envName, command);
        task.setExecuteCommand(fullCommand);
        taskMgr.setTaskExecuteCommand(task.getTaskId(), fullCommand);

        log.info("执行 ai-toolkit 训练: cmd={}", fullCommand);
        Process process = condaService.startInEnv(envName, command, null);
        taskMgr.setTaskProcessId(task.getTaskId(), process.pid());

        String logPath = ensureLogFile(task.getTaskId());
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

    @SuppressWarnings("unchecked")
    private String extractModelPath(String yamlConfig) {
        Map<String, Object> root = new Yaml().load(yamlConfig);
        if (root == null) return null;
        Object configObj = root.get("config");
        if (!(configObj instanceof Map)) return null;
        Object processObj = ((Map<String, Object>) configObj).get("process");
        if (!(processObj instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map)) return null;
        Object modelObj = ((Map<String, Object>) first).get("model");
        if (!(modelObj instanceof Map)) return null;
        Object nameOrPath = ((Map<String, Object>) modelObj).get("name_or_path");
        return nameOrPath instanceof String s ? s : null;
    }

    private String resolveModelPath(TrainingTask task) {
        String modelRef = extractModelPath(task.getYamlConfig());
        if (modelRef == null || modelRef.isBlank() || Path.of(modelRef).isAbsolute()) return null;

        String modelName = modelRef.contains("/")
                ? modelRef.substring(modelRef.lastIndexOf('/') + 1) : modelRef;
        String absolutePath = Path.of(properties.getModelDir(), modelName).toAbsolutePath().toString();
        task.setYamlConfig(task.getYamlConfig().replace(modelRef, absolutePath));
        log.info("模型路径已解析: {} -> {}", modelRef, absolutePath);
        return modelRef;
    }

    private void ensureModel(TrainingTask task, String envName, String modelId) {
        String modelPath = extractModelPath(task.getYamlConfig());
        if (modelPath == null || modelPath.isBlank()) return;

        if (isModelComplete(modelPath)) {
            log.info("模型已存在且完整: {}", modelPath);
            return;
        }

        if (Files.isDirectory(Path.of(modelPath))) {
            log.info("模型不完整，ModelScope 将断点续传: {}", modelPath);
        }

        String scopeId = resolveModelScopeId(modelId, modelPath);
        if (scopeId == null) {
            log.warn("无法解析 ModelScope 模型 ID，跳过下载: {}", modelPath);
            return;
        }

        ensureModelScope(envName);
        log.info("模型不存在，从 ModelScope 下载: {} -> {}", scopeId, modelPath);
        Path.of(modelPath).toFile().mkdirs();
        String pyScript = String.format(
                "from modelscope.hub.snapshot_download import snapshot_download; " +
                "snapshot_download(model_id='%s', local_dir='%s')",
                scopeId.replace("'", "\\'"),
                modelPath.replace("'", "\\'")
        );
        condaService.runInEnv(envName, "python -c \"" + pyScript + "\"", null);
        log.info("模型下载完成: {}", modelPath);
    }

    private boolean isModelComplete(String modelPath) {
        Path dir = Path.of(modelPath);
        if (!Files.isDirectory(dir)) return false;
        return Files.exists(dir.resolve("model_index.json")) || Files.exists(dir.resolve("config.json"));
    }

    private String resolveModelScopeId(String modelId, String modelPath) {
        if (modelId != null && !modelId.isBlank() && !Path.of(modelId).isAbsolute()) {
            return MODEL_TO_SCOPE_ID.getOrDefault(modelId, modelId);
        }
        String modelName = modelPath.contains("/")
                ? modelPath.substring(modelPath.lastIndexOf('/') + 1) : modelPath;
        return MODEL_TO_SCOPE_ID.get(modelName);
    }

    private void ensureModelScope(String envName) {
        if (condaService.isModuleInstalled(envName, "modelscope")) {
            log.info("modelscope 已就绪: {}", envName);
            return;
        }
        log.info("安装 modelscope: {}", envName);
        condaService.pipInstall(envName, "modelscope", null);
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

    private String ensureLogFile(String taskId) {
        Path dir = Path.of(properties.getLogDir());
        dir.toFile().mkdirs();
        return dir.resolve("training_" + taskId + ".log").toAbsolutePath().toString();
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
                logBroadcast.send(taskId, line);
                parseProgress(taskId, line, taskMgr);
            }
        } catch (IOException e) {
            log.error("读取训练输出失败: {}", e.getMessage());
        } finally {
            logBroadcast.complete(taskId);
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
        return properties.getDefaultCondaEnv();
    }

    private String orEmpty(String s) { return s == null ? "" : s; }
    private String orDefault(String s, String def) { return s == null || s.isBlank() ? def : s; }
}
