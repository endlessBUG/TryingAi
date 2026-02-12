package com.ai.trainer.service;

import com.ai.trainer.model.TaskStatus;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.repository.TrainingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagerService {

    private final TrainingTaskRepository taskRepo;

    public TrainingTask createTask(TrainingTask task) {
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0.0);
        task.setCreatedAt(LocalDateTime.now());
        taskRepo.save(task);
        log.info("创建任务: {}", task.getTaskId());
        return task;
    }

    public TrainingTask getTask(String taskId) {
        return taskRepo.findById(taskId).orElse(null);
    }

    public List<TrainingTask> getAllTasks() {
        return taskRepo.findAll();
    }

    public List<TrainingTask> getTasksByStatus(TaskStatus status) {
        return taskRepo.findByStatus(status);
    }

    public void updateTaskStatus(String taskId, TaskStatus status) {
        TrainingTask task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(status);
        if (status == TaskStatus.RUNNING) task.setStartedAt(LocalDateTime.now());
        if (status == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
            task.setProgress(100.0);
        }
        taskRepo.save(task);
    }

    public void updateTaskProgress(String taskId, double progress, int currentStep, int totalSteps) {
        TrainingTask task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        task.setProgress(progress);
        task.setCurrentStep(currentStep);
        task.setTotalSteps(totalSteps);
        taskRepo.save(task);
    }

    public void setTaskProcessId(String taskId, long processId) {
        TrainingTask task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        task.setProcessId(processId);
        taskRepo.save(task);
    }

    public void setTaskError(String taskId, String errorMessage) {
        TrainingTask task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());
        taskRepo.save(task);
    }

    public boolean deleteTask(String taskId) {
        if (!taskRepo.existsById(taskId)) return false;
        taskRepo.deleteById(taskId);
        return true;
    }

    public boolean cancelTask(String taskId) {
        TrainingTask task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return false;
        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepo.save(task);
        return true;
    }

    public int getRunningTaskCount() {
        return taskRepo.findByStatus(TaskStatus.RUNNING).size();
    }
}
