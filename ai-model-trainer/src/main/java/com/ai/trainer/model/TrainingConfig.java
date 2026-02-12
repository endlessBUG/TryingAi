package com.ai.trainer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingConfig {
    private String modelType;
    private String baseModel;
    private Integer steps;
    private Integer batchSize;
    private Double learningRate;
    private Integer resolution;
    private Integer loraRank;
    private Integer loraAlpha;
    private String optimizer;
    private String lrScheduler;
    private Integer saveEvery;
    private Integer sampleEvery;
    private String samplePrompt;
    private String mixedPrecision;
    private Integer gradientAccumulationSteps;
    private Boolean use8bitAdam;
    private Boolean useXformers;
    private String extraArgs;
}
