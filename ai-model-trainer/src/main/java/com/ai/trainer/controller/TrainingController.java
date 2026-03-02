package com.ai.trainer.controller;

import com.ai.trainer.model.CreateTaskRequest;
import com.ai.trainer.model.Dataset;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.TrainerRepository;
import com.ai.trainer.service.AutoPipelineService;
import com.ai.trainer.service.FileUploadService;
import com.ai.trainer.service.HyperParamRecommendService;
import com.ai.trainer.service.LogBroadcastService;
import com.ai.trainer.service.TaskManagerService;
import com.ai.trainer.service.TrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TaskManagerService taskManager;
    private final TrainingService trainingService;
    private final FileUploadService fileUploadService;
    private final TrainerRepository trainerRepo;
    private final HyperParamRecommendService hyperParamService;
    private final AutoPipelineService autoPipelineService;
    private final LogBroadcastService logBroadcastService;

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody CreateTaskRequest req) {
        Dataset ds = fileUploadService.getDataset(req.getDatasetId());
        Trainer trainer = trainerRepo.findById(req.getTrainerId()).orElse(null);
        TrainingTask task = TrainingTask.builder()
                .taskName(req.getTaskName())
                .datasetId(req.getDatasetId())
                .datasetName(ds != null ? ds.getName() : null)
                .datasetPath(ds != null ? ds.getDatasetPath() : null)
                .imageCount(ds != null ? ds.getImageCount() : null)
                .trainerId(req.getTrainerId())
                .trainerName(trainer != null ? trainer.getName() : null)
                .trainerPath(trainer != null ? trainer.getPath() : null)
                .yamlConfig(req.getYamlConfig())
                .build();
        TrainingTask created = taskManager.createTask(task);
        return ResponseEntity.ok(Map.of("success", true, "task", created));
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks() {
        List<TrainingTask> tasks = taskManager.getAllTasks();
        return ResponseEntity.ok(Map.of("success", true, "tasks", tasks));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String id) {
        TrainingTask task = taskManager.getTask(id);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true, "task", task));
    }

    @PostMapping("/tasks/{id}/start")
    public ResponseEntity<Map<String, Object>> startTask(@PathVariable String id) {
        trainingService.startTraining(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "训练已启动"));
    }

    @PostMapping("/tasks/{id}/restart")
    public ResponseEntity<Map<String, Object>> restartTask(@PathVariable String id) {
        taskManager.resetTask(id);
        trainingService.startTraining(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "训练已重新启动"));
    }

    @PostMapping("/tasks/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopTask(@PathVariable String id) {
        trainingService.stopTraining(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "训练已停止"));
    }

    @PostMapping("/auto-pipeline")
    public ResponseEntity<Map<String, Object>> autoPipeline(
            @RequestParam String datasetId,
            @RequestParam String trainerId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String yamlConfig = body != null ? body.get("yamlConfig") : null;
        autoPipelineService.execute(datasetId, trainerId, yamlConfig);
        return ResponseEntity.ok(Map.of("success", true, "message", "一键训练流水线已启动"));
    }

    @GetMapping("/recommend/{datasetId}")
    public ResponseEntity<Map<String, Object>> recommend(@PathVariable String datasetId) {
        Dataset ds = fileUploadService.getDataset(datasetId);
        if (ds == null) return ResponseEntity.notFound().build();
        Map<String, Object> params = hyperParamService.recommend(ds);
        return ResponseEntity.ok(Map.of("success", true, "data", params));
    }

    @GetMapping("/tasks/compare")
    public ResponseEntity<Map<String, Object>> compareTasks(@RequestParam List<String> taskIds) {
        List<TrainingTask> tasks = taskIds.stream()
                .map(taskManager::getTask)
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "tasks", tasks));
    }

    @GetMapping("/tasks/by-dataset/{datasetId}")
    public ResponseEntity<Map<String, Object>> getTasksByDataset(@PathVariable String datasetId) {
        List<TrainingTask> tasks = taskManager.getTasksByDataset(datasetId);
        return ResponseEntity.ok(Map.of("success", true, "tasks", tasks));
    }

    @GetMapping("/tasks/{id}/log")
    public ResponseEntity<Map<String, Object>> getTaskLog(@PathVariable String id) {
        TrainingTask task = taskManager.getTask(id);
        if (task == null || task.getLogPath() == null) {
            return ResponseEntity.ok(Map.of("success", false, "content", "暂无日志"));
        }
        File logFile = new File(task.getLogPath());
        if (!logFile.exists()) {
            return ResponseEntity.ok(Map.of("success", false, "content", "日志文件不存在: " + task.getLogPath()));
        }
        try {
            String content = Files.readString(logFile.toPath());
            return ResponseEntity.ok(Map.of("success", true, "content", content));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "content", "读取日志失败: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/tasks/{id}/log/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskLog(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L);
        logBroadcastService.subscribe(id, emitter);
        return emitter;
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String id) {
        boolean deleted = taskManager.deleteTask(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }
}
