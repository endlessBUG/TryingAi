package com.ai.trainer.repository;

import com.ai.trainer.model.TaskStatus;
import com.ai.trainer.model.TrainingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingTaskRepository extends JpaRepository<TrainingTask, String> {

    List<TrainingTask> findByStatus(TaskStatus status);
}
