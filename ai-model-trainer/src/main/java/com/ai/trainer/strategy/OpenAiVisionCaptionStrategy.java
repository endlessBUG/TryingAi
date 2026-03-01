package com.ai.trainer.strategy;

import com.ai.trainer.model.GeneratorType;
import com.ai.trainer.model.PromptGenerator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class OpenAiVisionCaptionStrategy extends AbstractCaptionStrategy {

    public OpenAiVisionCaptionStrategy(RestTemplate restTemplate) {
        super(restTemplate);
    }

    @Override
    public GeneratorType getType() {
        return GeneratorType.OPENAI_VISION;
    }

    @Override
    protected Map<String, Object> buildCaptionBody(PromptGenerator generator, String base64Image) {
        String imageUrl = "data:image/jpeg;base64," + base64Image;
        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", getSystemPrompt(generator)
        );
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageUrl)
        );
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );
        return Map.of(
                "model", Optional.ofNullable(generator.getModelName()).orElse("gpt-4-vision-preview"),
                "messages", List.of(message),
                "max_tokens", 500
        );
    }
}
