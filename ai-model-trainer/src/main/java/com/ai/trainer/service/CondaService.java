package com.ai.trainer.service;

import com.ai.trainer.exception.TrainingException;
import com.ai.trainer.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CondaService {

    private final SystemConfigRepository configRepo;

    public String getCondaPath() {
        return configRepo.findById("conda.path")
                .map(c -> c.getConfigValue())
                .orElseThrow(() -> new TrainingException("未配置 Conda 路径，请在 Conda 配置页面设置"));
    }

    public boolean envExists(String envName) {
        try {
            String output = executeCondaCommand("env", "list");
            return output.lines().anyMatch(line -> line.trim().startsWith(envName + " ") || line.trim().startsWith(envName + "\t"));
        } catch (Exception e) {
            log.warn("检查 conda 环境失败: {}", e.getMessage());
            return false;
        }
    }

    public void createEnv(String envName, String pythonVersion) {
        if (envExists(envName)) {
            log.info("Conda 环境已存在，跳过创建: {}", envName);
            return;
        }
        log.info("创建 Conda 环境: {} (Python {})", envName, pythonVersion);
        String result = executeCondaCommand("create", "-n", envName, "python=" + pythonVersion, "-y");
        log.debug("conda create output: {}", result);
        log.info("Conda 环境创建完成: {}", envName);
    }

    public void installRequirements(String envName, String requirementsPath) {
        File reqFile = new File(requirementsPath);
        if (!reqFile.exists()) {
            log.info("requirements.txt 不存在，跳过依赖安装: {}", requirementsPath);
            return;
        }
        log.info("在环境 {} 中安装依赖: {}", envName, requirementsPath);
        String actualPath = preprocessRequirements(requirementsPath);
        String pipCmd = buildPipInstallCommand(actualPath);
        String result = runInEnv(envName, pipCmd, reqFile.getParentFile());
        log.debug("pip install output: {}", result);
        log.info("依赖安装完成: {}", envName);
    }

    private String buildPipInstallCommand(String requirementsPath) {
        String indexUrl = getPipIndexUrl();
        if (indexUrl != null && !indexUrl.isBlank()) {
            return String.format("pip install -r \"%s\" -i %s --trusted-host %s",
                    requirementsPath, indexUrl, extractHost(indexUrl));
        }
        return String.format("pip install -r \"%s\"", requirementsPath);
    }

    private String getPipIndexUrl() {
        return configRepo.findById("pip.index.url")
                .map(c -> c.getConfigValue())
                .orElse(null);
    }

    private String extractHost(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String preprocessRequirements(String requirementsPath) {
        String proxy = getGithubProxy();
        if (proxy == null || proxy.isBlank()) return requirementsPath;

        try {
            List<String> lines = Files.readAllLines(Path.of(requirementsPath));
            boolean hasGitUrl = lines.stream().anyMatch(l -> l.contains("git+https://github.com/"));
            if (!hasGitUrl) return requirementsPath;

            String proxyPrefix = proxy.endsWith("/") ? proxy : proxy + "/";
            List<String> processed = lines.stream()
                    .map(line -> line.replace("git+https://github.com/", "git+" + proxyPrefix + "https://github.com/"))
                    .toList();

            Path tempFile = Path.of(requirementsPath).getParent().resolve("requirements_proxied.txt");
            Files.write(tempFile, processed);
            log.info("已生成代理 requirements: {}", tempFile);
            return tempFile.toAbsolutePath().toString();
        } catch (IOException e) {
            log.warn("预处理 requirements.txt 失败，使用原文件: {}", e.getMessage());
            return requirementsPath;
        }
    }

    private String getGithubProxy() {
        return configRepo.findById("github.proxy")
                .map(c -> c.getConfigValue())
                .orElse(null);
    }

    public boolean isModuleInstalled(String envName, String moduleName) {
        try {
            runInEnv(envName, "pip show " + moduleName, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void pipInstall(String envName, String packages, String indexUrl) {
        String base = "pip install " + packages + " --no-cache-dir --timeout 300";
        String cmd = indexUrl != null && !indexUrl.isBlank()
                ? base + " --index-url " + indexUrl
                : base;
        runInEnv(envName, cmd, null);
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public String runInEnv(String envName, String command, File workDir) {
        return executeProcess(buildRunInEnvArgs(envName, command), workDir);
    }

    public String buildFullCommand(String envName, String command) {
        String condaPath = getCondaPath();
        if (IS_WINDOWS) {
            return condaPath + " run --no-capture-output -n " + envName + " cmd /c " + command;
        }
        return condaPath + " run --no-capture-output -n " + envName + " bash -c \"" + command + "\"";
    }

    public Process startInEnv(String envName, String command, File workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(buildRunInEnvArgs(envName, command));
            if (workDir != null) pb.directory(workDir);
            applyHfMirrorEnv(pb.environment());
            pb.redirectErrorStream(true);
            return pb.start();
        } catch (Exception e) {
            throw new TrainingException("启动进程失败: " + e.getMessage());
        }
    }

    private void applyHfMirrorEnv(Map<String, String> env) {
        String mirror = getHfMirror();
        if (mirror != null && !mirror.isBlank()) {
            env.put("HF_ENDPOINT", mirror.replaceAll("/+$", ""));
            log.debug("已设置 HuggingFace 镜像: HF_ENDPOINT={}", mirror);
        }
    }

    private String getHfMirror() {
        String v = configRepo.findById("hf.mirror").map(c -> c.getConfigValue()).orElse(null);
        if (v != null && !v.isBlank() && !"off".equalsIgnoreCase(v.trim())) return v.trim();
        if (v != null && "off".equalsIgnoreCase(v.trim())) return null;
        return "https://hf-mirror.com";
    }

    private String[] buildRunInEnvArgs(String envName, String command) {
        String condaPath = getCondaPath();
        if (IS_WINDOWS) {
            return new String[]{"cmd", "/c", condaPath, "run", "--no-capture-output", "-n", envName, "cmd", "/c", command};
        }
        return new String[]{condaPath, "run", "--no-capture-output", "-n", envName, "bash", "-c", command};
    }

    private String executeCondaCommand(String... args) {
        String condaPath = getCondaPath();
        if (IS_WINDOWS) {
            String[] command = new String[args.length + 3];
            command[0] = "cmd";
            command[1] = "/c";
            command[2] = condaPath;
            System.arraycopy(args, 0, command, 3, args.length);
            return executeProcess(command, null);
        }
        String[] command = new String[args.length + 1];
        command[0] = condaPath;
        System.arraycopy(args, 0, command, 1, args.length);
        return executeProcess(command, null);
    }

    private String executeProcess(String[] command, File workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workDir != null) pb.directory(workDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("[conda] {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new TrainingException("命令执行失败(exit=" + exitCode + "): " + output);
            }
            return output.toString();
        } catch (TrainingException e) {
            throw e;
        } catch (Exception e) {
            throw new TrainingException("命令执行异常: " + e.getMessage());
        }
    }
}
