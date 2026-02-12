package com.ai.trainer.util;

import com.ai.trainer.exception.FileProcessException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class FileUtil {

    private FileUtil() {}

    public static String generateUniqueDir(String parentDir, String prefix) {
        String dirName = prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File dir = new File(parentDir, dirName);
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    public static void ensureDirectory(String path) {
        new File(path).mkdirs();
    }

    public static void deleteDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f.getAbsolutePath());
                else f.delete();
            }
        }
        dir.delete();
    }

    public static String getFileExtension(String fileName) {
        if (fileName.endsWith(".tar.gz")) return "tar.gz";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    public static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static List<String> unzip(File zipFile, String destDir) {
        if (!zipFile.exists()) {
            throw new FileProcessException("文件不存在: " + zipFile.getAbsolutePath());
        }
        List<String> extractedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File outFile = new File(destDir, entry.getName());
                outFile.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(outFile)) {
                    zis.transferTo(os);
                }
                extractedFiles.add(outFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new FileProcessException("解压文件失败", e);
        }
        return extractedFiles;
    }

    public static List<File> filterImageFiles(List<String> filePaths, String[] formats) {
        Set<String> fmtSet = new HashSet<>(Arrays.asList(formats));
        return filePaths.stream()
                .map(File::new)
                .filter(f -> fmtSet.contains(getFileExtension(f.getName())))
                .collect(Collectors.toList());
    }

    public static long getDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.isDirectory() ? getDirectorySize(f) : f.length();
            }
        }
        return size;
    }
}
