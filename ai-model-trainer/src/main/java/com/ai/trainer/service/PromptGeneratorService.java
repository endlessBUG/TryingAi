package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.model.PromptGenerator;
import com.ai.trainer.repository.ImagePromptRepository;
import com.ai.trainer.repository.PromptGeneratorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptGeneratorService {

    private final PromptGeneratorRepository generatorRepo;
    private final ImageCaptionService captionService;
    private final ImagePromptRepository imagePromptRepo;

    @Async
    public void generatePromptsAsync(String datasetId, String generatorId) {
        PromptGenerator generator = generatorRepo.findById(generatorId).orElse(null);
        if (generator == null) {
            log.error("未找到提示词生成器: {}", generatorId);
            return;
        }
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(datasetId);
        log.info("开始异步生成提示词: datasetId={}, 共{}张图片", datasetId, images.size());
        for (ImagePrompt img : images) {
            generateAndSaveOne(generator, img);
        }
        log.info("提示词生成完成: datasetId={}", datasetId);
    }

    public void generatePrompts(List<ImagePrompt> images, String generatorId) {
        PromptGenerator generator = generatorRepo.findById(generatorId).orElse(null);
        if (generator == null) {
            log.error("未找到提示词生成器: {}", generatorId);
            return;
        }
        for (ImagePrompt img : images) {
            String caption = captionService.generateCaption(generator, img.getImagePath());
            img.setPrompt(caption);
        }
    }

    public void savePromptFiles(List<ImagePrompt> images) {
        for (ImagePrompt img : images) {
            saveOnePromptFile(img);
        }
    }

    private void generateAndSaveOne(PromptGenerator generator, ImagePrompt img) {
        try {
            String caption = captionService.generateCaption(generator, img.getImagePath());
            img.setPrompt(caption);
            saveOnePromptFile(img);
            imagePromptRepo.save(img);
        } catch (Exception e) {
            log.error("生成提示词失败: {}", img.getImageName(), e);
        }
    }

    private void saveOnePromptFile(ImagePrompt img) {
        String txtPath = img.getImagePath().replaceAll("\\.[^.]+$", ".txt");
        try (Writer w = new FileWriter(txtPath)) {
            w.write(img.getPrompt() != null ? img.getPrompt() : "");
        } catch (IOException e) {
            log.error("保存提示词文件失败: {}", txtPath, e);
        }
    }
}
