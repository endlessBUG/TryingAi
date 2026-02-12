package com.ai.trainer.repository;

import com.ai.trainer.model.ImagePrompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImagePromptRepository extends JpaRepository<ImagePrompt, Long> {

    List<ImagePrompt> findByDatasetId(String datasetId);

    void deleteByDatasetId(String datasetId);
}
