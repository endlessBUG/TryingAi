package com.ai.trainer.strategy;

import com.ai.trainer.model.GeneratorType;
import com.ai.trainer.model.PromptGenerator;

public interface CaptionStrategy {

    GeneratorType getType();

    String generateCaption(PromptGenerator generator, String imagePath);

    String testConnection(PromptGenerator generator);
}
