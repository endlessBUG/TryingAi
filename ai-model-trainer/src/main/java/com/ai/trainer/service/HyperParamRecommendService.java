package com.ai.trainer.service;

import com.ai.trainer.model.Dataset;
import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.repository.ImagePromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能超参数推荐：根据数据集特征推荐训练参数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyperParamRecommendService {

    private final ImagePromptRepository imagePromptRepo;

    public Map<String, Object> recommend(Dataset dataset) {
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(dataset.getId());
        int count = images.size();
        int avgRes = calcAvgResolution(images);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imageCount", count);
        params.put("avgResolution", avgRes);
        params.putAll(recommendByCount(count));
        params.put("resolution", recommendResolution(avgRes));
        return params;
    }

    private Map<String, Object> recommendByCount(int count) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (count <= 10) {
            r.put("steps", 1500);
            r.put("learningRate", 2e-4);
            r.put("networkRank", 16);
            r.put("networkAlpha", 16);
            r.put("batchSize", 1);
            r.put("reason", "图片较少，适当提高学习率并减少步数防止过拟合");
        } else if (count <= 30) {
            r.put("steps", 2000);
            r.put("learningRate", 1e-4);
            r.put("networkRank", 32);
            r.put("networkAlpha", 32);
            r.put("batchSize", 1);
            r.put("reason", "中等数据量，使用标准参数");
        } else if (count <= 100) {
            r.put("steps", 3000);
            r.put("learningRate", 5e-5);
            r.put("networkRank", 32);
            r.put("networkAlpha", 32);
            r.put("batchSize", 2);
            r.put("reason", "数据量较多，降低学习率增加步数以充分学习");
        } else {
            r.put("steps", 5000);
            r.put("learningRate", 3e-5);
            r.put("networkRank", 64);
            r.put("networkAlpha", 64);
            r.put("batchSize", 2);
            r.put("reason", "大数据量，使用更高 rank 和更多步数");
        }
        return r;
    }

    private int recommendResolution(int avgRes) {
        if (avgRes >= 1024) return 1024;
        if (avgRes >= 768) return 768;
        return 512;
    }

    private int calcAvgResolution(List<ImagePrompt> images) {
        if (images.isEmpty()) return 512;
        return (int) images.stream()
                .filter(i -> i.getWidth() != null && i.getHeight() != null)
                .mapToInt(i -> Math.min(i.getWidth(), i.getHeight()))
                .average()
                .orElse(512);
    }
}
