package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.model.PromptGenerator;
import com.ai.trainer.repository.PromptGeneratorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptGeneratorService {

    private final PromptGeneratorRepository generatorRepo;
    private final ImageCaptionService captionService;

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

    private void saveOnePromptFile(ImagePrompt img) {
        String txtPath = img.getImagePath().replaceAll("\\.[^.]+$", ".txt");
        try (Writer w = new FileWriter(txtPath)) {
            w.write(img.getPrompt() != null ? img.getPrompt() : "");
        } catch (IOException e) {
            log.error("保存提示词文件失败: {}", txtPath, e);
        }
    }
}
