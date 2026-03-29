package com.ai.trainer.repository;

import com.ai.trainer.model.AIConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI配置数据访问层
 */
@Repository
public interface AIConfigRepository extends JpaRepository<AIConfig, Long> {

    /**
     * 根据服务类型查询配置列表，按优先级降序排列
     */
    List<AIConfig> findByServiceTypeOrderByPriorityDescCreatedAtDesc(String serviceType);

    /**
     * 查询所有配置，按优先级降序排列
     */
    List<AIConfig> findAllByOrderByPriorityDescCreatedAtDesc();

    /**
     * 根据服务类型和激活状态查询配置列表
     */
    List<AIConfig> findByServiceTypeAndIsActiveTrueOrderByPriorityDescCreatedAtDesc(String serviceType);

    /**
     * 根据服务类型和厂商标识查询配置
     */
    List<AIConfig> findByServiceTypeAndProvider(String serviceType, String provider);

    /**
     * 根据服务类型查询默认配置
     */
    Optional<AIConfig> findFirstByServiceTypeAndIsActiveTrueOrderByPriorityDescCreatedAtDesc(String serviceType);

    /**
     * 检查配置是否存在
     */
    boolean existsById(Long id);
}