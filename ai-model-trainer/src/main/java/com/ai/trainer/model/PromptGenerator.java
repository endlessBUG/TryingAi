package com.ai.trainer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prompt_generator")
public class PromptGenerator {
    @Id
    private String id;
    private String name;
    @Enumerated(EnumType.STRING)
    private GeneratorType type;
    private String baseUrl;
    private String modelName;
    @Column(length = 2000)
    private String systemPrompt;
    @Builder.Default
    private Integer maxTokens = 1000;
    @Builder.Default
    private Boolean enabled = true;
    private LocalDateTime createdAt;
}
