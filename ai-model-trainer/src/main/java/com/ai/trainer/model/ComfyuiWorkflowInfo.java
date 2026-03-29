package com.ai.trainer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComfyuiWorkflowInfo {
    private String path;
    private Long size;
    private Double modified;
    private Double created;
}