# API 测试指南

## 测试环境

- 基础URL: `http://localhost:8080`
- Content-Type: `application/json` (除文件上传外)

## 1. 文件上传测试

### 上传图片压缩包

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@/path/to/images.zip" \
  -F "generatePrompts=true" \
  -F "useAiGeneration=false"
```

**PowerShell (Windows):**

```powershell
$uri = "http://localhost:8080/api/files/upload"
$filePath = "C:\path\to\images.zip"
$form = @{
    file = Get-Item -Path $filePath
    generatePrompts = "true"
    useAiGeneration = "false"
}
Invoke-RestMethod -Uri $uri -Method Post -Form $form
```

### 更新提示词

```bash
curl -X PUT http://localhost:8080/api/files/prompts \
  -H "Content-Type: application/json" \
  -d '[
    {
      "imageName": "image1.jpg",
      "imagePath": "/path/to/image1.jpg",
      "prompt": "a beautiful sunset over mountains"
    }
  ]'
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/files/prompts"
$body = @(
    @{
        imageName = "image1.jpg"
        imagePath = "/path/to/image1.jpg"
        prompt = "a beautiful sunset over mountains"
    }
) | ConvertTo-Json

Invoke-RestMethod -Uri $uri -Method Put -Body $body -ContentType "application/json"
```

## 2. 训练任务测试

### 创建训练任务

```bash
curl -X POST http://localhost:8080/api/training/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "my_lora_training",
    "datasetPath": "./data/datasets/dataset_abc123",
    "imageCount": 10,
    "trainingConfig": {
      "modelType": "lora",
      "baseModel": "runwayml/stable-diffusion-v1-5",
      "steps": 1000,
      "batchSize": 1,
      "learningRate": 0.0001,
      "resolution": 512,
      "loraRank": 4,
      "loraAlpha": 4.0,
      "optimizer": "adamw8bit",
      "lrScheduler": "constant",
      "saveEvery": 500,
      "sampleEvery": 100,
      "samplePrompt": "a photo",
      "mixedPrecision": "fp16",
      "gradientAccumulationSteps": 1,
      "use8bitAdam": true,
      "useXformers": false
    }
  }'
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/training/tasks"
$body = @{
    taskName = "my_lora_training"
    datasetPath = "./data/datasets/dataset_abc123"
    imageCount = 10
    trainingConfig = @{
        modelType = "lora"
        baseModel = "runwayml/stable-diffusion-v1-5"
        steps = 1000
        batchSize = 1
        learningRate = 0.0001
        resolution = 512
        loraRank = 4
        loraAlpha = 4.0
        optimizer = "adamw8bit"
        lrScheduler = "constant"
        saveEvery = 500
        sampleEvery = 100
        samplePrompt = "a photo"
        mixedPrecision = "fp16"
        gradientAccumulationSteps = 1
        use8bitAdam = $true
        useXformers = $false
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri $uri -Method Post -Body $body -ContentType "application/json"
```

### 启动训练

```bash
# 替换 {taskId} 为实际的任务ID
curl -X POST http://localhost:8080/api/training/tasks/{taskId}/start
```

**PowerShell:**

```powershell
$taskId = "your-task-id-here"
$uri = "http://localhost:8080/api/training/tasks/$taskId/start"
Invoke-RestMethod -Uri $uri -Method Post
```

### 获取任务状态

```bash
curl -X GET http://localhost:8080/api/training/tasks/{taskId}
```

**PowerShell:**

```powershell
$taskId = "your-task-id-here"
$uri = "http://localhost:8080/api/training/tasks/$taskId"
Invoke-RestMethod -Uri $uri -Method Get
```

### 获取所有任务

```bash
curl -X GET http://localhost:8080/api/training/tasks
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/training/tasks"
Invoke-RestMethod -Uri $uri -Method Get
```

### 停止训练

```bash
curl -X POST http://localhost:8080/api/training/tasks/{taskId}/stop
```

**PowerShell:**

```powershell
$taskId = "your-task-id-here"
$uri = "http://localhost:8080/api/training/tasks/$taskId/stop"
Invoke-RestMethod -Uri $uri -Method Post
```

### 删除任务

```bash
curl -X DELETE http://localhost:8080/api/training/tasks/{taskId}
```

**PowerShell:**

```powershell
$taskId = "your-task-id-here"
$uri = "http://localhost:8080/api/training/tasks/$taskId"
Invoke-RestMethod -Uri $uri -Method Delete
```

## 3. 配置管理测试

### 获取训练配置模板

```bash
curl -X GET http://localhost:8080/api/config/template/training
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/config/template/training"
Invoke-RestMethod -Uri $uri -Method Get
```

### 保存YAML配置

```bash
curl -X POST "http://localhost:8080/api/config/yaml?filePath=./data/configs/my_config.yaml" \
  -H "Content-Type: application/json" \
  -d '{
    "job": {
      "name": "my_training",
      "device": "cuda:0"
    },
    "model": {
      "name_or_path": "runwayml/stable-diffusion-v1-5"
    }
  }'
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/config/yaml?filePath=./data/configs/my_config.yaml"
$body = @{
    job = @{
        name = "my_training"
        device = "cuda:0"
    }
    model = @{
        name_or_path = "runwayml/stable-diffusion-v1-5"
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri $uri -Method Post -Body $body -ContentType "application/json"
```

### 读取YAML配置

```bash
curl -X GET "http://localhost:8080/api/config/yaml?filePath=./data/configs/my_config.yaml"
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/config/yaml?filePath=./data/configs/my_config.yaml"
Invoke-RestMethod -Uri $uri -Method Get
```

## 4. 验证ai-toolkit

```bash
curl -X GET http://localhost:8080/api/training/validate
```

**PowerShell:**

```powershell
$uri = "http://localhost:8080/api/training/validate"
Invoke-RestMethod -Uri $uri -Method Get
```

## 完整工作流示例

### Bash脚本

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

echo "1. 上传图片压缩包..."
UPLOAD_RESPONSE=$(curl -s -X POST $BASE_URL/api/files/upload \
  -F "file=@./test-images.zip" \
  -F "generatePrompts=true")
echo $UPLOAD_RESPONSE | jq

DATASET_PATH=$(echo $UPLOAD_RESPONSE | jq -r '.images[0].imagePath' | xargs dirname)
IMAGE_COUNT=$(echo $UPLOAD_RESPONSE | jq -r '.imageCount')

echo ""
echo "2. 创建训练任务..."
TASK_RESPONSE=$(curl -s -X POST $BASE_URL/api/training/tasks \
  -H "Content-Type: application/json" \
  -d "{
    \"taskName\": \"test_training\",
    \"datasetPath\": \"$DATASET_PATH\",
    \"imageCount\": $IMAGE_COUNT,
    \"trainingConfig\": {
      \"modelType\": \"lora\",
      \"baseModel\": \"runwayml/stable-diffusion-v1-5\",
      \"steps\": 100,
      \"resolution\": 512
    }
  }")
echo $TASK_RESPONSE | jq

TASK_ID=$(echo $TASK_RESPONSE | jq -r '.task.taskId')

echo ""
echo "3. 启动训练..."
curl -s -X POST $BASE_URL/api/training/tasks/$TASK_ID/start | jq

echo ""
echo "4. 监控训练进度..."
for i in {1..10}; do
  sleep 5
  STATUS=$(curl -s -X GET $BASE_URL/api/training/tasks/$TASK_ID)
  echo $STATUS | jq '.task | {status, progress, currentStep, totalSteps}'
done
```

### PowerShell脚本

```powershell
$BaseUrl = "http://localhost:8080"

Write-Host "1. 上传图片压缩包..."
$uploadUri = "$BaseUrl/api/files/upload"
$filePath = ".\test-images.zip"
$form = @{
    file = Get-Item -Path $filePath
    generatePrompts = "true"
}
$uploadResponse = Invoke-RestMethod -Uri $uploadUri -Method Post -Form $form
$uploadResponse | ConvertTo-Json

$datasetPath = Split-Path $uploadResponse.images[0].imagePath
$imageCount = $uploadResponse.imageCount

Write-Host "`n2. 创建训练任务..."
$taskUri = "$BaseUrl/api/training/tasks"
$taskBody = @{
    taskName = "test_training"
    datasetPath = $datasetPath
    imageCount = $imageCount
    trainingConfig = @{
        modelType = "lora"
        baseModel = "runwayml/stable-diffusion-v1-5"
        steps = 100
        resolution = 512
    }
} | ConvertTo-Json -Depth 10

$taskResponse = Invoke-RestMethod -Uri $taskUri -Method Post -Body $taskBody -ContentType "application/json"
$taskResponse | ConvertTo-Json

$taskId = $taskResponse.task.taskId

Write-Host "`n3. 启动训练..."
$startUri = "$BaseUrl/api/training/tasks/$taskId/start"
Invoke-RestMethod -Uri $startUri -Method Post | ConvertTo-Json

Write-Host "`n4. 监控训练进度..."
for ($i = 1; $i -le 10; $i++) {
    Start-Sleep -Seconds 5
    $statusUri = "$BaseUrl/api/training/tasks/$taskId"
    $status = Invoke-RestMethod -Uri $statusUri -Method Get
    $status.task | Select-Object status, progress, currentStep, totalSteps | ConvertTo-Json
}
```

## 响应示例

### 成功响应

```json
{
  "success": true,
  "message": "操作成功",
  "data": {...}
}
```

### 错误响应

```json
{
  "success": false,
  "message": "错误描述",
  "timestamp": 1234567890
}
```

## 注意事项

1. 确保ai-toolkit已正确安装并配置
2. 检查Python环境是否正确
3. 训练需要GPU支持（可选）
4. 大文件上传可能需要较长时间
5. 训练任务运行时会占用较多资源

## 故障排查

### 上传失败
- 检查文件大小限制
- 确认压缩包格式（ZIP/TAR.GZ）
- 查看应用日志

### 训练失败
- 检查ai-toolkit配置
- 验证Python环境
- 查看训练日志文件
- 确认GPU可用性（如需要）

### 连接错误
- 确认应用已启动
- 检查端口是否被占用
- 验证防火墙设置
