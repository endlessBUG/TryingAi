package com.ai.trainer.service;

import com.ai.trainer.config.TrainerProperties;
import com.ai.trainer.model.Dataset;
import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.repository.DatasetRepository;
import com.ai.trainer.repository.ImagePromptRepository;
import com.ai.trainer.storage.FileStorageService;
import com.ai.trainer.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final TrainerProperties properties;
    private final PromptGeneratorService promptService;
    private final DatasetRepository datasetRepo;
    private final ImagePromptRepository imagePromptRepo;
    private final FileStorageService storageService;

    @Transactional
    public Dataset uploadAndExtract(MultipartFile file, boolean generatePrompts, boolean useAi) throws IOException {
        String uploadDir = storageService.createDir(properties.getUploadDir(), "upload");
        File uploadedFile = saveUploadedFile(file, uploadDir);

        String datasetDir = storageService.createDir(properties.getDatasetDir(), "ds");
        List<String> extracted = FileUtil.unzip(uploadedFile, datasetDir);

        List<File> imageFiles = FileUtil.filterImageFiles(extracted, properties.getSupportedFormatArray());
        String datasetId = UUID.randomUUID().toString();
        List<ImagePrompt> images = buildImagePrompts(imageFiles, datasetId);

        if (generatePrompts) {
            promptService.generatePrompts(images);
            promptService.savePromptFiles(images);
        }

        Dataset dataset = buildDataset(datasetId, file.getOriginalFilename(), datasetDir, images.size());
        datasetRepo.save(dataset);
        imagePromptRepo.saveAll(images);

        storageService.deleteDir(uploadDir);
        dataset.setImages(images);
        return dataset;
    }

    public List<Dataset> getAllDatasets() {
        return datasetRepo.findAll();
    }

    public Dataset getDataset(String id) {
        return datasetRepo.findById(id).orElse(null);
    }

    public Dataset getDatasetWithPrompts(String id) {
        Dataset ds = datasetRepo.findById(id).orElse(null);
        if (ds == null) return null;
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(id);
        for (ImagePrompt img : images) {
            loadPromptFromFile(img);
        }
        ds.setImages(images);
        return ds;
    }

    @Transactional
    public boolean deleteDataset(String id) {
        Dataset ds = datasetRepo.findById(id).orElse(null);
        if (ds == null) return false;
        imagePromptRepo.deleteByDatasetId(id);
        datasetRepo.deleteById(id);
        storageService.deleteDir(ds.getDatasetPath());
        log.info("删除数据集: {} ({})", ds.getName(), ds.getId());
        return true;
    }

    private File saveUploadedFile(MultipartFile file, String dir) throws IOException {
        File dest = new File(dir, file.getOriginalFilename());
        file.transferTo(dest);
        return dest;
    }

    private List<ImagePrompt> buildImagePrompts(List<File> imageFiles, String datasetId) {
        List<ImagePrompt> list = new ArrayList<>();
        for (File f : imageFiles) {
            ImagePrompt ip = ImagePrompt.builder()
                    .datasetId(datasetId)
                    .imageName(f.getName())
                    .imagePath(f.getAbsolutePath())
                    .fileSize(f.length())
                    .build();
            readImageDimensions(f, ip);
            list.add(ip);
        }
        return list;
    }

    private void readImageDimensions(File file, ImagePrompt ip) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                ip.setWidth(img.getWidth());
                ip.setHeight(img.getHeight());
            }
        } catch (IOException e) {
            log.warn("读取图片尺寸失败: {}", file.getName());
        }
    }

    private Dataset buildDataset(String id, String fileName, String datasetDir, int imageCount) {
        String name = FileUtil.getBaseName(fileName != null ? fileName : "dataset");
        long totalSize = storageService.dirSize(datasetDir);
        return Dataset.builder()
                .id(id)
                .name(name)
                .datasetPath(datasetDir)
                .imageCount(imageCount)
                .totalSize(totalSize)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void loadPromptFromFile(ImagePrompt img) {
        String txtPath = img.getImagePath().replaceAll("\\.[^.]+$", ".txt");
        if (storageService.exists(txtPath)) {
            try {
                byte[] bytes = storageService.load(txtPath).readAllBytes();
                img.setPrompt(new String(bytes).trim());
            } catch (Exception e) {
                log.warn("读取提示词文件失败: {}", txtPath);
            }
        }
    }
}
