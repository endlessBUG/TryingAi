package com.ai.trainer.service;

import com.ai.trainer.model.ImagePrompt;
import com.ai.trainer.repository.ImagePromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/**
 * 图片自动预处理：缩放、去 EXIF、重复检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagePreprocessService {

    private final ImagePromptRepository imagePromptRepo;

    public Map<String, Object> preprocess(String datasetId, int targetRes) {
        List<ImagePrompt> images = imagePromptRepo.findByDatasetId(datasetId);
        int resized = 0;
        List<String> duplicates = new ArrayList<>();

        Map<String, String> hashMap = new HashMap<>();
        for (ImagePrompt img : images) {
            resized += resizeImage(img, targetRes) ? 1 : 0;
            detectDuplicate(img, hashMap, duplicates);
        }
        imagePromptRepo.saveAll(images);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", images.size());
        result.put("resized", resized);
        result.put("duplicates", duplicates);
        return result;
    }

    private boolean resizeImage(ImagePrompt img, int targetRes) {
        try {
            Path path = Path.of(img.getImagePath());
            BufferedImage original = ImageIO.read(path.toFile());
            if (original == null) return false;

            int w = original.getWidth();
            int h = original.getHeight();
            if (w <= targetRes && h <= targetRes) return false;

            double scale = calcScale(w, h, targetRes);
            int nw = (int) (w * scale);
            int nh = (int) (h * scale);

            BufferedImage resized = scaleImage(original, nw, nh);
            writeImage(resized, path);
            updateDimensions(img, nw, nh, path);
            return true;
        } catch (IOException e) {
            log.warn("图片缩放失败: {}", img.getImageName(), e);
            return false;
        }
    }

    private double calcScale(int w, int h, int target) {
        return (double) target / Math.max(w, h);
    }

    private BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dest;
    }

    private void writeImage(BufferedImage img, Path path) throws IOException {
        String ext = getExtension(path);
        ImageIO.write(img, ext.equals("png") ? "png" : "jpg", path.toFile());
    }

    private void updateDimensions(ImagePrompt img, int w, int h, Path path) throws IOException {
        img.setWidth(w);
        img.setHeight(h);
        img.setFileSize(Files.size(path));
    }

    private void detectDuplicate(ImagePrompt img, Map<String, String> hashMap, List<String> duplicates) {
        try {
            String hash = fileHash(img.getImagePath());
            if (hashMap.containsKey(hash)) {
                duplicates.add(img.getImageName() + " ↔ " + hashMap.get(hash));
            } else {
                hashMap.put(hash, img.getImageName());
            }
        } catch (Exception e) {
            log.debug("计算文件哈希失败: {}", img.getImageName());
        }
    }

    private String fileHash(String path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "jpg";
    }
}
