package com.ai.trainer.repository;

import com.ai.trainer.model.ComfyuiWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComfyuiWorkflowRepository extends JpaRepository<ComfyuiWorkflow, String> {
    List<ComfyuiWorkflow> findByEnabledTrue();
    List<ComfyuiWorkflow> findByCategory(String category);
}