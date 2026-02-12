package com.ai.trainer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "image_prompt")
public class ImagePrompt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String datasetId;
    private String imageName;
    private String imagePath;
    @Column(length = 2000)
    private String prompt;
    private Integer width;
    private Integer height;
    private Long fileSize;
}
