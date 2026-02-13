package com.ai.trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class AiModelTrainerApplication {

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(System.getProperty("user.home"), "tryingai"));
        SpringApplication.run(AiModelTrainerApplication.class, args);
    }
}
