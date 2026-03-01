package com.ai.trainer.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (hasOldTypeConstraint()) {
            recreatePromptGeneratorTable();
        }
    }

    private boolean hasOldTypeConstraint() {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='prompt_generator'",
                String.class
        );
        return !schemas.isEmpty() && schemas.get(0) != null && schemas.get(0).contains("LOCAL_MODEL");
    }

    private void recreatePromptGeneratorTable() {
        log.info("检测到旧 CHECK 约束，开始重建 prompt_generator 表...");
        jdbcTemplate.execute("DROP TABLE IF EXISTS prompt_generator_v2");
        // 完整复制数据（不带约束），再逐字段 UPDATE，避免列顺序假设导致错位
        jdbcTemplate.execute("CREATE TABLE prompt_generator_v2 AS SELECT * FROM prompt_generator");
        jdbcTemplate.execute("UPDATE prompt_generator_v2 SET type='COGVLM2' WHERE type='LOCAL_MODEL'");
        jdbcTemplate.execute("UPDATE prompt_generator_v2 SET type='OPENAI_VISION' WHERE type='REMOTE_API'");
        jdbcTemplate.execute("DROP TABLE prompt_generator");
        jdbcTemplate.execute("ALTER TABLE prompt_generator_v2 RENAME TO prompt_generator");
        log.info("prompt_generator 表重建完成，数据已迁移");
    }
}
