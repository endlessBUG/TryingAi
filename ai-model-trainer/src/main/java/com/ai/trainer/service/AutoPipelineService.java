package com.ai.trainer.service;

import com.ai.trainer.model.*;
import com.ai.trainer.repository.TrainerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一键训练流水线：下载模型 -> 创建任务 -> 启动训练
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPipelineService {

    private static final Pattern NAME_OR_PATH = Pattern.compile("name_or_path:\\s*\"?([^\"\\n]+)\"?");

    private final HyperParamRecommendService paramService;
    private final TaskManagerService taskManager;
    private final TrainingService trainingService;
    private final TrainerRepository trainerRepo;
    private final FileUploadService fileUploadService;
    private final CondaService condaService;

    @Async
    public void execute(String datasetId, String trainerId, String yamlConfig) {
        log.info("一键训练流水线启动: dataset={}", datasetId);

        Dataset dataset = fileUploadService.getDataset(datasetId);
        if (dataset == null) {
            log.error("数据集不存在: {}", datasetId);
            return;
        }

        Trainer trainer = trainerRepo.findById(trainerId).orElse(null);
        String yaml = resolveYaml(yamlConfig, dataset, trainer);
        ensureModelDownloaded(yaml, trainer);

        TrainingTask task = buildTask(dataset, trainer, yaml);
        TrainingTask created = taskManager.createTask(task);
        trainingService.startTraining(created.getTaskId());

        log.info("一键训练流水线完成创建并启动: taskId={}", created.getTaskId());
    }

    private void ensureModelDownloaded(String yaml, Trainer trainer) {
        String modelId = extractModelId(yaml);
        if (modelId == null || !modelId.contains("/")) return;
        if (trainer == null || trainer.getCondaEnvName() == null) return;

        try {
            log.info("检查并下载基础模型: {}", modelId);
            condaService.runInEnv(trainer.getCondaEnvName(),
                    "huggingface-cli download " + modelId, null);
            log.info("基础模型已就绪: {}", modelId);
        } catch (Exception e) {
            log.warn("模型预下载失败（训练时将重试）: {}", e.getMessage());
        }
    }

    private String extractModelId(String yaml) {
        if (yaml == null) return null;
        Matcher m = NAME_OR_PATH.matcher(yaml);
        return m.find() ? m.group(1).trim() : null;
    }

    private TrainingTask buildTask(Dataset ds, Trainer trainer, String yaml) {
        return TrainingTask.builder()
                .taskName("一键训练-" + ds.getName())
                .datasetId(ds.getId())
                .datasetName(ds.getName())
                .datasetPath(ds.getDatasetPath())
                .imageCount(ds.getImageCount())
                .trainerId(trainer != null ? trainer.getId() : null)
                .trainerName(trainer != null ? trainer.getName() : null)
                .trainerPath(trainer != null ? trainer.getPath() : null)
                .yamlConfig(yaml)
                .build();
    }

    private String resolveYaml(String userYaml, Dataset ds, Trainer trainer) {
        if (userYaml != null && !userYaml.isBlank()) {
            return userYaml;
        }
        if (trainer != null && trainer.getDefaultYamlConfig() != null) {
            return trainer.getDefaultYamlConfig().replace("{{DATASET_PATH}}", toYamlPath(ds.getDatasetPath()));
        }
        return generateDefaultYaml(ds);
    }

    private String generateDefaultYaml(Dataset ds) {
        Map<String, Object> params = paramService.recommend(ds);
        return "# 自动生成的训练配置\n"
                + "train:\n"
                + "  steps: " + params.getOrDefault("steps", 2000) + "\n"
                + "  learning_rate: " + params.getOrDefault("learningRate", 1e-4) + "\n"
                + "  batch_size: " + params.getOrDefault("batchSize", 1) + "\n"
                + "network:\n"
                + "  rank: " + params.getOrDefault("networkRank", 32) + "\n"
                + "  alpha: " + params.getOrDefault("networkAlpha", 32) + "\n"
                + "datasets:\n"
                + "  - folder_path: " + toYamlPath(ds.getDatasetPath()) + "\n"
                + "    resolution: " + params.getOrDefault("resolution", 512) + "\n";
    }

    /** 将 Windows 反斜杠路径转为正斜杠，避免 YAML 字符串中产生非法转义序列 */
    private String toYamlPath(String path) {
        if (path == null) return "";
        return path.replace("\\", "/");
    }
}
