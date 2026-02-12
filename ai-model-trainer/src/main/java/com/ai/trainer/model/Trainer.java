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
@Table(name = "trainer")
public class Trainer {
    @Id
    private String id;
    private String name;
    private String path;
    private String gitUrl;
    private String pythonVersion;
    @Column(length = 20000)
    private String defaultYamlConfig;
    private LocalDateTime createdAt;
}
