package com.ai.trainer.controller;

import com.ai.trainer.model.CreateTaskRequest;
import com.ai.trainer.model.Dataset;
import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.TrainerRepository;
import com.ai.trainer.service.FileUploadService;
import com.ai.trainer.service.TaskManagerService;
import com.ai.trainer.service.TrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TaskManagerService taskManager;
    private final TrainingService trainingService;
    private final FileUploadService fileUploadService;
    private final TrainerRepository trainerRepo;

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

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String id) {
        boolean deleted = taskManager.deleteTask(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }
}
