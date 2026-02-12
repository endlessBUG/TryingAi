package com.ai.trainer.service;

import com.ai.trainer.model.TaskStatus;
import com.ai.trainer.model.TrainingTask;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * TaskManagerService测试类
 */
public class TaskManagerServiceTest {
    
    private TaskManagerService taskManager;
    
    @Before
    public void setUp() {
        taskManager = new TaskManagerService();
    }
    
    @Test
    public void testCreateTask() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .datasetPath("/path/to/dataset")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        
        assertNotNull(created);
        assertNotNull(created.getTaskId());
        assertEquals("test_task", created.getTaskName());
        assertEquals(TaskStatus.PENDING, created.getStatus());
        assertNotNull(created.getCreatedAt());
        assertEquals(0.0, created.getProgress(), 0.001);
    }
    
    @Test
    public void testGetTask() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        TrainingTask retrieved = taskManager.getTask(created.getTaskId());
        
        assertNotNull(retrieved);
        assertEquals(created.getTaskId(), retrieved.getTaskId());
        assertEquals(created.getTaskName(), retrieved.getTaskName());
    }
    
    @Test
    public void testGetAllTasks() {
        // 创建多个任务
        for (int i = 0; i < 3; i++) {
            TrainingTask task = TrainingTask.builder()
                    .taskName("test_task_" + i)
                    .build();
            taskManager.createTask(task);
        }
        
        List<TrainingTask> allTasks = taskManager.getAllTasks();
        
        assertNotNull(allTasks);
        assertEquals(3, allTasks.size());
    }
    
    @Test
    public void testUpdateTaskStatus() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        // 更新为RUNNING
        taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING);
        TrainingTask running = taskManager.getTask(taskId);
        assertEquals(TaskStatus.RUNNING, running.getStatus());
        assertNotNull(running.getStartedAt());
        
        // 更新为COMPLETED
        taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED);
        TrainingTask completed = taskManager.getTask(taskId);
        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals(100.0, completed.getProgress(), 0.001);
    }
    
    @Test
    public void testUpdateTaskProgress() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        taskManager.updateTaskProgress(taskId, 50.0, 500, 1000);
        
        TrainingTask updated = taskManager.getTask(taskId);
        assertEquals(50.0, updated.getProgress(), 0.001);
        assertEquals(500, updated.getCurrentStep().intValue());
        assertEquals(1000, updated.getTotalSteps().intValue());
    }
    
    @Test
    public void testSetTaskProcessId() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        taskManager.setTaskProcessId(taskId, 12345L);
        
        TrainingTask updated = taskManager.getTask(taskId);
        assertEquals(12345L, updated.getProcessId().longValue());
    }
    
    @Test
    public void testSetTaskError() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        taskManager.setTaskError(taskId, "Test error message");
        
        TrainingTask failed = taskManager.getTask(taskId);
        assertEquals(TaskStatus.FAILED, failed.getStatus());
        assertEquals("Test error message", failed.getErrorMessage());
        assertNotNull(failed.getCompletedAt());
    }
    
    @Test
    public void testGetTasksByStatus() {
        // 创建不同状态的任务
        TrainingTask task1 = TrainingTask.builder().taskName("task1").build();
        TrainingTask task2 = TrainingTask.builder().taskName("task2").build();
        TrainingTask task3 = TrainingTask.builder().taskName("task3").build();
        
        TrainingTask created1 = taskManager.createTask(task1);
        TrainingTask created2 = taskManager.createTask(task2);
        TrainingTask created3 = taskManager.createTask(task3);
        
        taskManager.updateTaskStatus(created2.getTaskId(), TaskStatus.RUNNING);
        taskManager.updateTaskStatus(created3.getTaskId(), TaskStatus.COMPLETED);
        
        List<TrainingTask> pending = taskManager.getTasksByStatus(TaskStatus.PENDING);
        List<TrainingTask> running = taskManager.getTasksByStatus(TaskStatus.RUNNING);
        List<TrainingTask> completed = taskManager.getTasksByStatus(TaskStatus.COMPLETED);
        
        assertEquals(1, pending.size());
        assertEquals(1, running.size());
        assertEquals(1, completed.size());
    }
    
    @Test
    public void testDeleteTask() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        boolean deleted = taskManager.deleteTask(taskId);
        assertTrue(deleted);
        
        TrainingTask retrieved = taskManager.getTask(taskId);
        assertNull(retrieved);
    }
    
    @Test
    public void testCancelTask() {
        TrainingTask task = TrainingTask.builder()
                .taskName("test_task")
                .build();
        
        TrainingTask created = taskManager.createTask(task);
        String taskId = created.getTaskId();
        
        taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING);
        
        boolean cancelled = taskManager.cancelTask(taskId);
        assertTrue(cancelled);
        
        TrainingTask updated = taskManager.getTask(taskId);
        assertEquals(TaskStatus.CANCELLED, updated.getStatus());
    }
    
    @Test
    public void testGetRunningTaskCount() {
        // 创建多个任务
        for (int i = 0; i < 5; i++) {
            TrainingTask task = TrainingTask.builder()
                    .taskName("task_" + i)
                    .build();
            TrainingTask created = taskManager.createTask(task);
            
            if (i < 3) {
                taskManager.updateTaskStatus(created.getTaskId(), TaskStatus.RUNNING);
            }
        }
        
        int runningCount = taskManager.getRunningTaskCount();
        assertEquals(3, runningCount);
    }
}
