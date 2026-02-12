# 快速开始指南

本指南将帮助您在5分钟内启动并运行AI Model Trainer。

## 第一步：环境准备

### 1.1 检查Java环境

```bash
java -version
```

需要JDK 17或更高版本。如果未安装：

**Windows:** 下载并安装 [Oracle JDK 17](https://www.oracle.com/java/technologies/downloads/)

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

**Mac:**
```bash
brew install openjdk@17
```

### 1.2 安装ai-toolkit

```bash
# 克隆ai-toolkit仓库
git clone https://github.com/ostris/ai-toolkit.git

# 进入目录
cd ai-toolkit

# 安装依赖
pip install -r requirements.txt
# 或使用 pip3
pip3 install -r requirements.txt
```

## 第二步：配置应用

### 2.1 修改配置文件

编辑 `src/main/resources/application.yml`：

```yaml
trainer:
  # 修改为你的ai-toolkit路径
  ai-toolkit-path: /path/to/ai-toolkit
  
  # 根据你的系统选择 python 或 python3
  python-path: python
```

**重要提示：** 将 `/path/to/ai-toolkit` 替换为你实际的ai-toolkit安装路径。

## 第三步：启动应用

### 方法1：使用启动脚本（推荐）

**Windows:**
```cmd
start.bat
```

**Linux/Mac:**
```bash
chmod +x start.sh
./start.sh
```

### 方法2：使用Maven

```bash
# 首次运行需要编译
mvn clean package -DskipTests

# 启动应用
mvn spring-boot:run
```

### 方法3：直接运行JAR

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/ai-model-trainer-1.0.0-SNAPSHOT.jar
```

## 第四步：验证安装

应用启动后，你应该看到类似的输出：

```
==============================================
AI Model Trainer Application Started Successfully!
==============================================
```

### 4.1 测试API

打开新的终端窗口：

```bash
# 验证ai-toolkit配置
curl http://localhost:8080/api/training/validate
```

如果配置正确，你会看到：

```json
{
  "success": true,
  "message": "ai-toolkit配置正确"
}
```

### 4.2 获取配置模板

```bash
curl http://localhost:8080/api/config/template/training
```

## 第五步：开始训练

### 5.1 准备训练数据

1. 收集训练图片（10-100张）
2. 将图片打包成ZIP文件

**示例目录结构：**
```
training-images/
├── image1.jpg
├── image2.jpg
├── image3.jpg
└── ...
```

**打包命令：**
```bash
# Windows
Compress-Archive -Path training-images\* -DestinationPath training-images.zip

# Linux/Mac
zip -r training-images.zip training-images/
```

### 5.2 上传图片

**使用curl (Linux/Mac):**
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@training-images.zip" \
  -F "generatePrompts=true"
```

**使用PowerShell (Windows):**
```powershell
$uri = "http://localhost:8080/api/files/upload"
$filePath = "training-images.zip"
$form = @{
    file = Get-Item -Path $filePath
    generatePrompts = "true"
}
$response = Invoke-RestMethod -Uri $uri -Method Post -Form $form
$response | ConvertTo-Json
```

**记录响应中的数据：**
- `datasetPath`: 数据集路径（后面需要用）
- `imageCount`: 图片数量

### 5.3 创建训练任务

**创建请求体文件 `create-task.json`：**

```json
{
  "taskName": "my_first_training",
  "datasetPath": "从上一步获取的datasetPath",
  "imageCount": 10,
  "trainingConfig": {
    "modelType": "lora",
    "baseModel": "runwayml/stable-diffusion-v1-5",
    "steps": 100,
    "batchSize": 1,
    "learningRate": 0.0001,
    "resolution": 512,
    "loraRank": 4,
    "loraAlpha": 4.0,
    "optimizer": "adamw8bit",
    "lrScheduler": "constant",
    "saveEvery": 50,
    "mixedPrecision": "fp16"
  }
}
```

**发送请求：**

```bash
# Linux/Mac
curl -X POST http://localhost:8080/api/training/tasks \
  -H "Content-Type: application/json" \
  -d @create-task.json
```

```powershell
# Windows PowerShell
$body = Get-Content create-task.json -Raw
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/training/tasks" `
  -Method Post -Body $body -ContentType "application/json"
$response | ConvertTo-Json
```

**记录返回的 `taskId`**

### 5.4 启动训练

```bash
# 替换 YOUR_TASK_ID 为实际的taskId
curl -X POST http://localhost:8080/api/training/tasks/YOUR_TASK_ID/start
```

```powershell
# Windows
$taskId = "YOUR_TASK_ID"
Invoke-RestMethod -Uri "http://localhost:8080/api/training/tasks/$taskId/start" -Method Post
```

### 5.5 监控训练进度

```bash
# 查看任务状态
curl http://localhost:8080/api/training/tasks/YOUR_TASK_ID
```

```powershell
# Windows
$taskId = "YOUR_TASK_ID"
Invoke-RestMethod -Uri "http://localhost:8080/api/training/tasks/$taskId"
```

**或者使用循环监控：**

```bash
# Linux/Mac - 每5秒检查一次
while true; do
  curl -s http://localhost:8080/api/training/tasks/YOUR_TASK_ID | jq '.task | {status, progress, currentStep}'
  sleep 5
done
```

```powershell
# Windows
while ($true) {
    $status = Invoke-RestMethod -Uri "http://localhost:8080/api/training/tasks/$taskId"
    $status.task | Select-Object status, progress, currentStep | ConvertTo-Json
    Start-Sleep -Seconds 5
}
```

## 训练结果

训练完成后，模型文件将保存在：

```
data/outputs/output_my_first_training_XXXXXX/
```

您可以在这里找到训练好的LoRA模型文件。

## 常见问题

### Q1: "ai-toolkit未找到"错误

**解决方案：**
1. 确认ai-toolkit已正确安装
2. 检查 `application.yml` 中的 `ai-toolkit-path` 配置
3. 使用绝对路径而不是相对路径

### Q2: 端口8080已被占用

**解决方案：**

在 `application.yml` 中修改端口：

```yaml
server:
  port: 8081  # 改为其他端口
```

### Q3: 文件上传失败

**解决方案：**
1. 检查文件大小（默认限制500MB）
2. 确认文件格式（支持ZIP、TAR.GZ）
3. 查看日志文件：`logs/application.log`

### Q4: 训练失败

**解决方案：**
1. 检查Python环境和ai-toolkit依赖
2. 查看训练日志：`data/logs/training_*.log`
3. 确认GPU驱动（如使用GPU训练）
4. 验证基础模型路径

### Q5: 内存不足

**解决方案：**

增加JVM内存：

```bash
export JAVA_OPTS="-Xmx4g -Xms1g"
java $JAVA_OPTS -jar target/ai-model-trainer-1.0.0-SNAPSHOT.jar
```

## 下一步

现在您已经成功运行了第一个训练任务！接下来可以：

1. 📖 阅读完整的 [README.md](README.md) 了解更多功能
2. 🚀 查看 [DEPLOYMENT.md](DEPLOYMENT.md) 了解生产部署
3. 🧪 使用 [API-TEST.md](API-TEST.md) 进行更多API测试
4. ⚙️ 调整 `example-config.yaml` 中的训练参数

## 获取帮助

- 📝 查看文档：[README.md](README.md)
- 🐛 报告问题：提交GitHub Issue
- 💬 社区讨论：加入讨论组

祝您训练愉快！🎉
