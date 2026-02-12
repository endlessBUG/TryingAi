package com.ai.trainer.storage;

import java.io.InputStream;

/**
 * 文件存储服务接口
 * 本地实现: LocalFileStorageService
 * S3扩展: 新增 S3FileStorageService 实现此接口即可
 */
public interface FileStorageService {

    /** 存储文件，返回实际存储路径 */
    String store(InputStream input, String path);

    /** 存储文件(字节)，返回实际存储路径 */
    String store(byte[] data, String path);

    /** 读取文件 */
    InputStream load(String path);

    /** 删除单个文件 */
    boolean delete(String path);

    /** 删除目录及其内容 */
    boolean deleteDir(String path);

    /** 判断文件是否存在 */
    boolean exists(String path);

    /** 获取文件大小 */
    long size(String path);

    /** 获取目录总大小 */
    long dirSize(String path);

    /** 创建唯一目录，返回路径 */
    String createDir(String parent, String prefix);
}
