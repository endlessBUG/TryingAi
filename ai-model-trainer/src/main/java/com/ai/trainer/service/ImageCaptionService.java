package com.ai.trainer.service;

import com.ai.trainer.model.GeneratorType;
import com.ai.trainer.model.PromptGenerator;
import com.ai.trainer.strategy.CaptionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageCaptionService {

    private final Map<GeneratorType, CaptionStrategy> strategyMap;

    public ImageCaptionService(List<CaptionStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(CaptionStrategy::getType, s -> s));
    }

    public String generateCaption(PromptGenerator generator, String imagePath) {
        return getStrategy(generator.getType()).generateCaption(generator, imagePath);
    }

    public String testConnection(PromptGenerator generator) {
        return getStrategy(generator.getType()).testConnection(generator);
    }

    public String testWithImage(PromptGenerator generator, MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("test_", "_" + file.getOriginalFilename());
        try {
            file.transferTo(tempFile);
            return generateCaption(generator, tempFile.toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private CaptionStrategy getStrategy(GeneratorType type) {
        CaptionStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的生成器类型: " + type);
        }
        return strategy;
    }
}
