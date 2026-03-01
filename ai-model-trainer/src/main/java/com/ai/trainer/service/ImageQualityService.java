package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.model.PromptGenerator;
import com.ai.trainer.repository.ImagePromptRepository;
import com.ai.trainer.repository.PromptGeneratorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图片质量自动筛选：调用视觉模型对图片打分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageQualityService {

    private final ImageCaptionService captionService;
    private final ImagePromptRepository imagePromptRepo;
    private final PromptGeneratorRepository generatorRepo;

    public void evaluateDataset(String datasetId, String generatorId) {
        PromptGenerator generator = findGenerator(generatorId);
        if (generator == null) return;

        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(datasetId);
        for (ImagePrompt img : images) {
            evaluateOne(img, generator);
        }
        imagePromptRepo.saveAll(images);
        log.info("数据集 {} 质量评估完成，共 {} 张图片", datasetId, images.size());
    }

    private PromptGenerator findGenerator(String generatorId) {
        return generatorRepo.findById(generatorId).orElse(null);
    }

    private void evaluateOne(ImagePrompt img, PromptGenerator gen) {
        try {
            String result = callQualityApi(img, gen);
            parseResult(img, result);
        } catch (Exception e) {
            log.warn("图片质量评估失败: {}", img.getImageName(), e);
            img.setQualityScore(null);
        }
    }

    private String callQualityApi(ImagePrompt img, PromptGenerator gen) {
        PromptGenerator qualityGen = buildQualityGenerator(gen);
        return captionService.generateCaption(qualityGen, img.getImagePath());
    }

    private PromptGenerator buildQualityGenerator(PromptGenerator base) {
        PromptGenerator gen = new PromptGenerator();
        gen.setBaseUrl(base.getBaseUrl());
        gen.setModelName(base.getModelName());
        gen.setSystemPrompt(buildQualityPrompt());
        return gen;
    }

    private String buildQualityPrompt() {
        return "Evaluate this image quality for AI LoRA training. "
                + "Rate 1-10 (10=best). Consider: sharpness, lighting, composition, "
                + "consistency of subject. Reply ONLY in format: score:N reason:brief_reason";
    }

    private void parseResult(ImagePrompt img, String result) {
        if (result == null || result.isBlank()) return;
        try {
            String lower = result.toLowerCase();
            int idx = lower.indexOf("score:");
            if (idx >= 0) {
                String after = lower.substring(idx + 6).trim();
                String numStr = after.split("[^0-9.]")[0];
                img.setQualityScore(Double.parseDouble(numStr));
            }
            int rIdx = lower.indexOf("reason:");
            if (rIdx >= 0) {
                String reason = result.substring(rIdx + 7).trim();
                img.setQualityReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            }
        } catch (Exception e) {
            log.debug("解析质量评分失败: {}", result);
        }
    }
}
