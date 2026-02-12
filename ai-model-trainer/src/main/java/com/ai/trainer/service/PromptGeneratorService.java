package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

@Slf4j
@Service
public class PromptGeneratorService {

    public void generatePrompts(List<ImagePrompt> images) {
        for (ImagePrompt img : images) {
            String baseName = FileUtil.getBaseName(img.getImageName());
            img.setPrompt(formatBaseName(baseName));
        }
    }

    public void savePromptFiles(List<ImagePrompt> images) {
        for (ImagePrompt img : images) {
            String txtPath = img.getImagePath().replaceAll("\\.[^.]+$", ".txt");
            try (Writer w = new FileWriter(txtPath)) {
                w.write(img.getPrompt() != null ? img.getPrompt() : "");
            } catch (IOException e) {
                log.error("保存提示词文件失败: {}", txtPath, e);
            }
        }
    }

    private String formatBaseName(String baseName) {
        return baseName.replaceAll("[_\\-]", " ").replaceAll("\\d+", "").trim();
    }
}
