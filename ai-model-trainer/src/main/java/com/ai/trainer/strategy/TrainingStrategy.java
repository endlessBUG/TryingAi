package com.ai.trainer.strategy;

import com.ai.trainer.model.Trainer;
import com.ai.trainer.model.TrainingTask;
import com.ai.trainer.service.TaskManagerService;

/**
 * 训练策略接口 - 不同训练器使用不同的训练实现
 */
public interface TrainingStrategy {

    /**
     * 是否支持该训练器
     */
    boolean supports(Trainer trainer);

    /**
     * 确保训练环境就绪（conda 环境、依赖安装等）
     */
    void ensureEnvironment(Trainer trainer);

    /**
     * 执行训练
     */
    void executeTraining(TrainingTask task, Trainer trainer, TaskManagerService taskMgr);

    /**
     * 停止训练
     */
    void stopTraining(TrainingTask task);
}
