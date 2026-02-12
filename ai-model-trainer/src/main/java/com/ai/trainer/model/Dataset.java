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
@Table(name = "dataset")
public class Dataset {
    @Id
    private String id;
    private String name;
    private String datasetPath;
    private Integer imageCount;
    private Long totalSize;
    private LocalDateTime createdAt;

    @Transient
    private List<ImagePrompt> images;
}
