package com.ai.trainer.storage;

import com.ai.trainer.exception.FileProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Override
    public String store(InputStream input, String path) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(file)) {
                input.transferTo(out);
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new FileProcessException("存储文件失败: " + path, e);
        }
    }

    @Override
    public String store(byte[] data, String path) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), data);
            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new FileProcessException("存储文件失败: " + path, e);
        }
    }

    @Override
    public InputStream load(String path) {
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new FileProcessException("文件不存在: " + path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        return new File(path).delete();
    }

    @Override
    public boolean deleteDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) return true;
        deleteRecursive(dir);
        return !dir.exists();
    }

    @Override
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    public long size(String path) {
        return new File(path).length();
    }

    @Override
    public long dirSize(String path) {
        return calcDirSize(new File(path));
    }

    @Override
    public String createDir(String parent, String prefix) {
        String name = prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        File dir = new File(parent, name);
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    private void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }

    private long calcDirSize(File dir) {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.isDirectory() ? calcDirSize(f) : f.length();
            }
        }
        return total;
    }
}
