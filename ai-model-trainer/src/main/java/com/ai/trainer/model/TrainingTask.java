package com.ai.trainer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "training_task")
public class TrainingTask {
    @Id
    private String taskId;
    private String taskName;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
    private String datasetId;
    private String datasetName;
    private String datasetPath;
    private String trainerId;
    private String trainerName;
    private String trainerPath;
    private Integer imageCount;
    private String configPath;
    private String outputPath;
    @Column(length = 10000)
    private String yamlConfig;
    @Builder.Default
    private Double progress = 0.0;
    private Integer currentStep;
    private Integer totalSteps;
    private Long processId;
    @Column(length = 2000)
    private String errorMessage;
    private String logPath;
    private String condaEnvName;
    @Column(length = 2000)
    private String executeCommand;
    @Column(length = 50000)
    private String lossHistory;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Transient
    private TrainingConfig trainingConfig;
    @Transient
    private List<ImagePrompt> prompts;
}
