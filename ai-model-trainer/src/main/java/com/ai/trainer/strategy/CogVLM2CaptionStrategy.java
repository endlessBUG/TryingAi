package com.ai.trainer.strategy;

import com.ai.trainer.model.GeneratorType;
import com.ai.trainer.model.PromptGenerator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class CogVLM2CaptionStrategy extends AbstractCaptionStrategy {

    public CogVLM2CaptionStrategy(RestTemplate restTemplate) {
        super(restTemplate);
    }

    @Override
    public GeneratorType getType() {
        return GeneratorType.COGVLM2;
    }

    @Override
    protected Map<String, Object> buildCaptionBody(PromptGenerator generator, String base64Image) {
        String imageUrl = "data:image/jpeg;base64," + base64Image;
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageUrl)
        );
        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", getSystemPrompt(generator)
        );
        // CogVLM2 要求 content 顺序：先图片后文字
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(imageContent, textContent)
        );
        return Map.of(
                "model", Optional.ofNullable(generator.getModelName()).orElse("cogvlm2-19b"),
                "messages", List.of(message),
                "max_tokens", resolveMaxTokens(generator)
        );
    }
}
