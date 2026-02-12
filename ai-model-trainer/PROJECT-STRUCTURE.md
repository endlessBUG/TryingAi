# 项目结构说明

## 目录树

```
ai-model-trainer/
│
├── src/
│   ├── main/
│   │   ├── java/com/ai/trainer/
│   │   │   ├── controller/                    # REST API控制器层
│   │   │   │   ├── FileController.java       # 文件上传和提示词管理
│   │   │   │   ├── TrainingController.java   # 训练任务管理
│   │   │   │   └── ConfigController.java     # 配置管理
│   │   │   │
│   │   │   ├── service/                       # 业务逻辑服务层
│   │   │   │   ├── FileUploadService.java    # 文件上传和解压服务
│   │   │   │   ├── PromptGeneratorService.java # 提示词生成服务
│   │   │   │   ├── TrainingService.java      # AI训练服务
│   │   │   │   └── TaskManagerService.java   # 任务管理服务
│   │   │   │
│   │   │   ├── model/                         # 数据模型
│   │   │   │   ├── TrainingTask.java         # 训练任务模型
│   │   │   │   ├── TrainingConfig.java       # 训练配置模型
│   │   │   │   ├── ImagePrompt.java          # 图片提示词模型
│   │   │   │   └── TaskStatus.java           # 任务状态枚举
│   │   │   │
│   │   │   ├── config/                        # 配置类
│   │   │   │   ├── AppConfig.java            # 应用配置
│   │   │   │   └── TrainerProperties.java    # 训练器属性配置
│   │   │   │
│   │   │   ├── util/                          # 工具类
│   │   │   │   ├── FileUtil.java             # 文件处理工具
│   │   │   │   └── YamlUtil.java             # YAML工具
│   │   │   │
│   │   │   ├── exception/                     # 异常处理
│   │   │   │   ├── TrainingException.java    # 训练异常
│   │   │   │   ├── FileProcessException.java # 文件处理异常
│   │   │   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   │   │   │
│   │   │   └── AiModelTrainerApplication.java # 主应用类
│   │   │
│   │   └── resources/
│   │       ├── application.yml                # 应用配置文件
│   │       ├── logback-spring.xml            # 日志配置
│   │       └── banner.txt                    # 启动横幅
│   │
│   └── test/
│       └── java/com/ai/trainer/
│           ├── util/
│           │   └── FileUtilTest.java         # 文件工具测试
│           └── service/
│               └── TaskManagerServiceTest.java # 任务管理测试
│
├── data/                                      # 数据目录（运行时创建）
│   ├── uploads/                              # 上传文件临时存储
│   ├── datasets/                             # 解压后的数据集
│   ├── outputs/                              # 训练输出模型
│   ├── configs/                              # 生成的配置文件
│   └── logs/                                 # 训练日志
│
├── logs/                                      # 应用日志目录
│
├── pom.xml                                   # Maven项目配置
├── .gitignore                                # Git忽略文件
├── Dockerfile                                # Docker镜像构建文件
├── docker-compose.yml                        # Docker Compose配置
│
├── start.bat                                 # Windows启动脚本
├── start.sh                                  # Linux/Mac启动脚本
│
├── README.md                                 # 项目说明文档
├── QUICKSTART.md                             # 快速开始指南
├── API-TEST.md                               # API测试文档
├── DEPLOYMENT.md                             # 部署指南
├── CHANGELOG.md                              # 更新日志
├── PROJECT-STRUCTURE.md                      # 本文件
└── example-config.yaml                       # 示例配置文件
```

## 核心模块说明

### 1. Controller层（控制器）

负责处理HTTP请求，定义REST API接口。

| 文件 | 功能 | 主要端点 |
|------|------|----------|
| FileController | 文件上传和提示词管理 | `/api/files/upload`, `/api/files/prompts` |
| TrainingController | 训练任务管理 | `/api/training/tasks`, `/api/training/tasks/{id}/start` |
| ConfigController | 配置管理 | `/api/config/yaml`, `/api/config/template` |

### 2. Service层（服务）

包含核心业务逻辑。

| 文件 | 职责 |
|------|------|
| FileUploadService | 处理文件上传、解压、图片提取 |
| PromptGeneratorService | 生成和管理图片提示词 |
| TrainingService | 执行AI模型训练、进程管理 |
| TaskManagerService | 管理训练任务生命周期 |

### 3. Model层（模型）

定义数据结构。

| 文件 | 说明 |
|------|------|
| TrainingTask | 训练任务完整信息 |
| TrainingConfig | 训练参数配置 |
| ImagePrompt | 图片及其提示词 |
| TaskStatus | 任务状态枚举 |

### 4. Util层（工具）

通用工具类。

| 文件 | 功能 |
|------|------|
| FileUtil | 文件压缩/解压、目录管理 |
| YamlUtil | YAML文件读写 |

### 5. Exception层（异常）

异常定义和处理。

| 文件 | 说明 |
|------|------|
| TrainingException | 训练过程异常 |
| FileProcessException | 文件处理异常 |
| GlobalExceptionHandler | 统一异常处理 |

## 配置文件说明

### application.yml

主配置文件，包含：
- 服务器配置（端口、上下文路径）
- 文件上传配置（大小限制）
- 训练器配置（路径、参数）
- 日志配置

### logback-spring.xml

日志配置，定义：
- 控制台输出格式
- 文件输出策略
- 日志级别
- 滚动策略

### example-config.yaml

ai-toolkit训练配置示例，包含：
- 任务配置
- 模型配置
- 训练参数
- 数据集配置
- LoRA配置

## 数据流程

### 1. 文件上传流程

```
用户上传ZIP文件
    ↓
FileController.uploadImageArchive()
    ↓
FileUploadService.uploadAndExtractImages()
    ↓
FileUtil.extractArchive() → 解压文件
    ↓
FileUtil.filterImageFiles() → 过滤图片
    ↓
PromptGeneratorService.generatePrompts() → 生成提示词
    ↓
返回ImagePrompt列表
```

### 2. 训练任务流程

```
用户创建任务
    ↓
TrainingController.createTask()
    ↓
TaskManagerService.createTask() → 创建任务记录
    ↓
用户启动任务
    ↓
TrainingController.startTask()
    ↓
TrainingService.startTraining() [异步]
    ↓
├─ 生成配置文件 (YamlUtil)
├─ 准备输出目录
├─ 构建训练命令
└─ 执行训练进程
    ↓
监控进度并更新任务状态
    ↓
训练完成
```

### 3. 配置管理流程

```
用户请求配置模板
    ↓
ConfigController.getTrainingTemplate()
    ↓
返回默认配置
    ↓
用户修改配置
    ↓
ConfigController.saveYamlConfig()
    ↓
YamlUtil.writeYamlFromMap()
    ↓
保存到文件系统
```

## 依赖关系图

```
AiModelTrainerApplication
    │
    ├─► Controller层
    │   ├─► FileController
    │   │   ├─► FileUploadService
    │   │   └─► PromptGeneratorService
    │   │
    │   ├─► TrainingController
    │   │   ├─► TrainingService
    │   │   └─► TaskManagerService
    │   │
    │   └─► ConfigController
    │       └─► YamlUtil
    │
    ├─► Service层
    │   ├─► FileUploadService → FileUtil
    │   ├─► PromptGeneratorService → FileUtil
    │   ├─► TrainingService
    │   │   ├─► TaskManagerService
    │   │   ├─► YamlUtil
    │   │   └─► FileUtil
    │   └─► TaskManagerService
    │
    ├─► Config层
    │   ├─► AppConfig
    │   └─► TrainerProperties
    │
    └─► Exception层
        └─► GlobalExceptionHandler
```

## 技术栈详解

### 后端框架
- **Spring Boot 3.2.2**: 应用框架
- **Spring Web**: REST API支持
- **Spring Validation**: 数据验证

### 文件处理
- **Apache Commons Compress**: 压缩文件处理
- **Apache Commons IO**: IO操作

### 配置管理
- **SnakeYAML**: YAML解析
- **Jackson**: JSON/YAML序列化

### 日志
- **SLF4J**: 日志门面
- **Logback**: 日志实现

### 工具
- **Lombok**: 减少样板代码
- **Maven**: 项目构建

### AI训练
- **ai-toolkit**: Python模型训练工具
- **Process API**: Java进程管理

## 扩展点

### 1. 添加新的文件格式支持

在 `FileUtil.java` 中添加新的解压方法：

```java
public static List<String> unRar(File rarFile, String destDir) {
    // 实现RAR解压
}
```

### 2. 集成AI提示词生成

在 `PromptGeneratorService.java` 中实现：

```java
private String generatePromptWithAI(ImagePrompt imagePrompt) {
    // 调用BLIP-2、LLaVA等模型
    // 生成图片描述
}
```

### 3. 添加数据库持久化

创建Repository层：

```java
@Repository
public interface TrainingTaskRepository extends JpaRepository<TrainingTask, String> {
    // 数据库操作
}
```

### 4. 添加用户认证

集成Spring Security：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    // 配置认证
}
```

## 性能考虑

### 1. 异步处理
- 训练任务异步执行，避免阻塞API
- 使用 `@Async` 注解和线程池

### 2. 资源管理
- 及时清理临时文件
- 限制并发训练任务数量

### 3. 内存优化
- 流式处理大文件
- 避免一次性加载全部数据到内存

### 4. 日志管理
- 日志文件自动滚动
- 定期清理旧日志

## 安全考虑

### 当前状态
- ⚠️ 无用户认证
- ⚠️ 无权限控制
- ⚠️ 无请求限流

### 建议改进
- 添加JWT认证
- 实现RBAC权限控制
- 添加API限流
- 输入验证和清理
- HTTPS支持

## 维护建议

### 日常维护
- 定期清理 `data/uploads/` 临时文件
- 监控 `data/outputs/` 磁盘使用
- 查看 `logs/` 错误日志

### 性能监控
- 监控JVM内存使用
- 跟踪训练任务队列
- 检查磁盘IO

### 备份策略
- 定期备份 `data/outputs/` 训练结果
- 备份配置文件
- 导出任务历史记录

## 开发指南

### 添加新API端点

1. 在Controller中定义：
```java
@GetMapping("/api/new-endpoint")
public ResponseEntity<?> newEndpoint() {
    // 实现逻辑
}
```

2. 在Service中实现业务逻辑
3. 添加测试用例
4. 更新API文档

### 修改配置

1. 修改 `application.yml`
2. 如需新配置属性，更新 `TrainerProperties.java`
3. 重启应用使配置生效

### 调试技巧

- 使用 `DEBUG` 日志级别：修改 `application.yml` 中的 `logging.level`
- 查看训练日志：`data/logs/training_*.log`
- 监控进程：`jps` 查看Java进程，`nvidia-smi` 查看GPU

## 总结

本项目采用经典的三层架构（Controller-Service-Model），结构清晰，易于扩展。通过Spring Boot提供的各种特性，实现了完整的AI模型训练工作流程。

主要优势：
- ✅ 模块化设计
- ✅ 易于测试
- ✅ 配置灵活
- ✅ 文档完善

可改进方向：
- 数据库持久化
- 用户认证
- Web界面
- 分布式支持
