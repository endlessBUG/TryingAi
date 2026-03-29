package com.ai.trainer.client;

import com.ai.trainer.dto.TestGenerateRequest;
import com.ai.trainer.dto.TestGenerateResult;
import com.ai.trainer.model.AIConfig;

/**
 * AI客户端接口
 */
public interface AIClient {

    /**
     * 测试连接
     */
    void testConnection(AIConfig config) throws Exception;

    /**
     * 执行测试生成
     */
    TestGenerateResult testGenerate(AIConfig config, TestGenerateRequest request) throws Exception;
}