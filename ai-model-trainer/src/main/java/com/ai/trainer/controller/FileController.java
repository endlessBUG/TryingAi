package com.ai.trainer.controller;

import com.ai.trainer.model.Dataset;
import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.repository.ImagePromptRepository;
import com.ai.trainer.service.FileUploadService;
import com.ai.trainer.service.ImagePreprocessService;
import com.ai.trainer.service.ImageQualityService;
import com.ai.trainer.service.PromptGeneratorService;
import com.ai.trainer.service.PromptOptimizeService;
import com.ai.trainer.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;
    private final PromptGeneratorService promptService;
    private final FileStorageService storageService;
    private final ImagePromptRepository imagePromptRepo;
    private final ImageQualityService imageQualityService;
    private final PromptOptimizeService promptOptimizeService;
    private final ImagePreprocessService imagePreprocessService;

    @GetMapping("/datasets")
    public ResponseEntity<Map<String, Object>> listDatasets() {
        List<Dataset> list = fileUploadService.getAllDatasets();
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    @GetMapping("/datasets/{id}")
    public ResponseEntity<Map<String, Object>> getDataset(@PathVariable String id) {
        Dataset ds = fileUploadService.getDatasetWithPrompts(id);
        if (ds == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true, "data", ds));
    }

    @DeleteMapping("/datasets/{id}")
    public ResponseEntity<Map<String, Object>> deleteDataset(@PathVariable String id) {
        boolean deleted = fileUploadService.deleteDataset(id);
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        Dataset dataset = fileUploadService.uploadAndExtract(file);
        return ResponseEntity.ok(Map.of("success", true, "message", "上传成功", "dataset", dataset));
    }

    @GetMapping("/datasets/{datasetId}/images/{imageName}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String datasetId,
            @PathVariable String imageName
    ) throws Exception {
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(datasetId);
        ImagePrompt target = images.stream()
                .filter(img -> img.getImageName().equals(imageName))
                .findFirst().orElse(null);
        if (target == null || !storageService.exists(target.getImagePath())) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(new File(target.getImagePath()).toPath());
        if (contentType == null) contentType = "application/octet-stream";

        InputStream is = storageService.load(target.getImagePath());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(is));
    }

    @PutMapping("/prompts")
    public ResponseEntity<Map<String, Object>> updatePrompts(@RequestBody List<ImagePrompt> prompts) {
        promptService.savePromptFiles(prompts);
        return ResponseEntity.ok(Map.of("success", true, "message", "提示词保存成功"));
    }

    @PostMapping("/datasets/{datasetId}/preprocess")
    public ResponseEntity<Map<String, Object>> preprocessImages(
            @PathVariable String datasetId,
            @RequestParam(value = "resolution", defaultValue = "512") int resolution
    ) {
        Map<String, Object> result = imagePreprocessService.preprocess(datasetId, resolution);
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    @PostMapping("/datasets/{datasetId}/evaluate-quality")
    public ResponseEntity<Map<String, Object>> evaluateQuality(
            @PathVariable String datasetId,
            @RequestParam("generatorId") String generatorId
    ) {
        imageQualityService.evaluateDataset(datasetId, generatorId);
        Dataset ds = fileUploadService.getDatasetWithPrompts(datasetId);
        return ResponseEntity.ok(Map.of("success", true, "data", ds));
    }

    @PostMapping("/datasets/{datasetId}/optimize-prompts")
    public ResponseEntity<Map<String, Object>> optimizePrompts(
            @PathVariable String datasetId,
            @RequestParam(value = "triggerWord", required = false) String triggerWord
    ) {
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(datasetId);
        promptOptimizeService.optimize(images, triggerWord);
        promptService.savePromptFiles(images);
        imagePromptRepo.saveAll(images);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("images", images)));
    }

    @PostMapping("/prompts/regenerate")
    public ResponseEntity<Map<String, Object>> regeneratePrompts(
            @RequestParam("datasetId") String datasetId,
            @RequestParam("generatorId") String generatorId
    ) {
        promptService.generatePromptsAsync(datasetId, generatorId);
        return ResponseEntity.ok(Map.of("success", true, "message", "提示词生成已开始，请稍后刷新查看"));
    }
}
