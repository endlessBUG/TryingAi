package com.ai.trainer.util;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class YamlUtil {

    private YamlUtil() {}

    public static Map<String, Object> readYaml(String filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            return data != null ? data : new LinkedHashMap<>();
        }
    }

    public static void writeYaml(String filePath, Map<String, Object> data) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        try (Writer writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
        }
    }
}
