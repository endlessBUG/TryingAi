package com.ai.trainer.util;

import com.ai.trainer.exception.FileProcessException;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * FileUtil测试类
 */
public class FileUtilTest {
    
    private static final String TEST_DIR = "./test-temp";
    private static final String TEST_ZIP = "./test-temp/test.zip";
    
    @Before
    public void setUp() {
        FileUtil.ensureDirectory(TEST_DIR);
    }
    
    @After
    public void tearDown() {
        FileUtil.deleteDirectory(TEST_DIR);
    }
    
    @Test
    public void testGenerateUniqueDir() {
        String dir1 = FileUtil.generateUniqueDir(TEST_DIR, "test");
        String dir2 = FileUtil.generateUniqueDir(TEST_DIR, "test");
        
        assertNotNull(dir1);
        assertNotNull(dir2);
        assertNotEquals(dir1, dir2);
        
        File file1 = new File(dir1);
        File file2 = new File(dir2);
        
        assertTrue(file1.exists());
        assertTrue(file2.exists());
        assertTrue(file1.isDirectory());
        assertTrue(file2.isDirectory());
    }
    
    @Test
    public void testEnsureDirectory() {
        String testPath = TEST_DIR + "/sub/dir";
        FileUtil.ensureDirectory(testPath);
        
        File dir = new File(testPath);
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }
    
    @Test
    public void testGetFileExtension() {
        assertEquals("jpg", FileUtil.getFileExtension("image.jpg"));
        assertEquals("tar.gz", FileUtil.getFileExtension("archive.tar.gz"));
        assertEquals("", FileUtil.getFileExtension("noextension"));
    }
    
    @Test
    public void testGetBaseName() {
        assertEquals("image", FileUtil.getBaseName("image.jpg"));
        assertEquals("document", FileUtil.getBaseName("document.pdf"));
        assertEquals("file", FileUtil.getBaseName("file"));
    }
    
    @Test
    public void testUnzip() throws IOException {
        // 创建测试ZIP文件
        createTestZipFile();
        
        // 解压
        String extractDir = TEST_DIR + "/extracted";
        FileUtil.ensureDirectory(extractDir);
        List<String> files = FileUtil.unzip(new File(TEST_ZIP), extractDir);
        
        assertNotNull(files);
        assertFalse(files.isEmpty());
        
        // 验证文件已解压
        for (String filePath : files) {
            File file = new File(filePath);
            assertTrue(file.exists());
        }
    }
    
    @Test(expected = FileProcessException.class)
    public void testUnzipNonExistentFile() {
        FileUtil.unzip(new File("nonexistent.zip"), TEST_DIR);
    }
    
    @Test
    public void testFilterImageFiles() {
        // 创建测试文件列表
        FileUtil.ensureDirectory(TEST_DIR + "/images");
        createTestFile(TEST_DIR + "/images/image1.jpg");
        createTestFile(TEST_DIR + "/images/image2.png");
        createTestFile(TEST_DIR + "/images/document.txt");
        createTestFile(TEST_DIR + "/images/image3.webp");
        
        List<String> allFiles = List.of(
            TEST_DIR + "/images/image1.jpg",
            TEST_DIR + "/images/image2.png",
            TEST_DIR + "/images/document.txt",
            TEST_DIR + "/images/image3.webp"
        );
        
        String[] supportedFormats = {"jpg", "jpeg", "png", "webp"};
        List<File> imageFiles = FileUtil.filterImageFiles(allFiles, supportedFormats);
        
        assertEquals(3, imageFiles.size());
    }
    
    // 辅助方法：创建测试ZIP文件
    private void createTestZipFile() throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(TEST_ZIP))) {
            // 添加测试文件到ZIP
            addToZip(zos, "test1.jpg", "test content 1");
            addToZip(zos, "test2.png", "test content 2");
            addToZip(zos, "subfolder/test3.jpg", "test content 3");
        }
    }
    
    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes());
        zos.closeEntry();
    }
    
    private void createTestFile(String path) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
