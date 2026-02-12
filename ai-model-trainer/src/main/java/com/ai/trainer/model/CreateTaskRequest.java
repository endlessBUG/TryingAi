package com.ai.trainer.model;

import lombok.Data;

@Data
public class CreateTaskRequest {
    private String taskName;
    private String datasetId;
    private String trainerId;
    private String yamlConfig;
}
