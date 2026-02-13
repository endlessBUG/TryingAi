package com.ai.trainer.repository;

import com.ai.trainer.model.PromptGenerator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptGeneratorRepository extends JpaRepository<PromptGenerator, String> {
    List<PromptGenerator> findByEnabledTrue();
}
