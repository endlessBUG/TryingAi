package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 智能提示词优化：trigger word 检测、格式统一、去重
 */
@Slf4j
@Service
public class PromptOptimizeService {

    public void optimize(List<ImagePrompt> images, String triggerWord) {
        ensureTriggerWord(images, triggerWord);
        deduplicatePrompts(images);
        log.info("提示词优化完成，共处理 {} 张图片", images.size());
    }

    private void ensureTriggerWord(List<ImagePrompt> images, String trigger) {
        if (trigger == null || trigger.isBlank()) return;
        String trimmed = trigger.trim();
        for (ImagePrompt img : images) {
            if (img.getPrompt() == null) continue;
            if (!containsTrigger(img.getPrompt(), trimmed)) {
                img.setPrompt(trimmed + ", " + img.getPrompt());
            }
        }
    }

    private boolean containsTrigger(String prompt, String trigger) {
        return prompt.toLowerCase().contains(trigger.toLowerCase());
    }

    private void deduplicatePrompts(List<ImagePrompt> images) {
        Map<String, Integer> seen = new HashMap<>();
        for (ImagePrompt img : images) {
            if (img.getPrompt() == null) continue;
            String normalized = normalize(img.getPrompt());
            int count = seen.getOrDefault(normalized, 0);
            if (count > 0) {
                img.setPrompt(appendSuffix(img.getPrompt(), img.getImageName()));
            }
            seen.put(normalized, count + 1);
        }
    }

    private String normalize(String prompt) {
        return prompt.toLowerCase().replaceAll("[\\s,]+", " ").trim();
    }

    private String appendSuffix(String prompt, String imageName) {
        String name = imageName.replaceAll("\\.[^.]+$", "").replaceAll("[_-]", " ");
        return prompt + ", " + name;
    }
}
