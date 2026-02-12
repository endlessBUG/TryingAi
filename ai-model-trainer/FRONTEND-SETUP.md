# 前后端完整启动指南

本文档介绍如何同时启动后端Java服务和前端Vue应用。

## 项目结构

```
ai-model-trainer/
├── src/                    # Java后端代码
├── frontend/               # Vue前端代码
├── pom.xml                 # Maven配置
└── README.md
```

## 第一步：启动后端服务

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
java -jar target/ai-model-trainer-1.0.0-SNAPSHOT.jar
```

**验证后端启动：**
```bash
curl http://localhost:8080/api/training/validate
```

## 第二步：启动前端服务

### 进入前端目录

```bash
cd frontend
```

### 安装依赖（首次运行）

```bash
npm install
# 或使用pnpm（更快）
pnpm install
# 或使用yarn
yarn install
```

### 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 启动。

## 第三步：访问应用

打开浏览器访问：**http://localhost:3000**

您将看到AI Model Trainer的Web界面。

## 完整工作流程演示

### 1. 上传训练数据

```bash
# 页面：http://localhost:3000/upload

1. 准备图片并打包成ZIP
2. 在上传页面拖拽或点击上传
3. 等待上传和自动生成提示词
4. 编辑提示词（可选）
5. 点击"创建训练任务"
```

### 2. 创建训练任务

```bash
# 页面：http://localhost:3000/tasks/create

1. 填写任务名称（例如：my_first_lora）
2. 配置训练参数：
   - 基础模型：runwayml/stable-diffusion-v1-5
   - 训练步数：1000
   - 学习率：0.0001
   - 分辨率：512
   - LoRA Rank：4
3. 点击"创建任务"
```

### 3. 启动训练

```bash
# 页面：http://localhost:3000/tasks

1. 在任务列表中找到新创建的任务
2. 点击"启动"按钮
3. 观察进度条和状态变化
```

### 4. 监控进度

```bash
# 页面会每5秒自动刷新
# 显示：
- 当前状态（运行中/已完成/失败）
- 进度百分比
- 当前步数/总步数
```

### 5. 查看结果

训练完成后，模型文件保存在：
```
data/outputs/output_<taskname>_<id>/
```

## 端口配置

### 默认端口
- **后端**：8080
- **前端**：3000

### 修改后端端口

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8081  # 修改为其他端口
```

### 修改前端端口

编辑 `frontend/vite.config.ts`：

```typescript
server: {
  port: 3001,  // 修改为其他端口
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // 后端地址
      changeOrigin: true
    }
  }
}
```

## 生产环境部署

### 构建前端

```bash
cd frontend
npm run build
```

构建产物在 `frontend/dist/` 目录。

### 集成部署方案

#### 方案1：使用Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }
    
    # 后端API代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### 方案2：Spring Boot集成

将前端构建产物复制到 `src/main/resources/static/`：

```bash
# 构建前端
cd frontend
npm run build

# 复制到后端
cp -r dist/* ../src/main/resources/static/

# 重新构建后端
cd ..
mvn clean package
```

然后访问 http://localhost:8080 即可。

## 开发技巧

### 1. 热重载

前端支持热重载，修改代码后自动刷新。

后端使用Spring Boot DevTools可实现热重载（需要IDE支持）。

### 2. 调试

**前端调试：**
- 浏览器开发者工具
- Vue DevTools插件

**后端调试：**
- IDE断点调试
- 查看日志：`logs/application.log`
- 训练日志：`data/logs/training_*.log`

### 3. 并行开发

打开两个终端窗口：

```bash
# 终端1：后端
mvn spring-boot:run

# 终端2：前端
cd frontend && npm run dev
```

## 常见问题

### Q: 前端无法连接后端？

**检查：**
1. 后端是否启动：`curl http://localhost:8080/api/training/validate`
2. 端口是否正确
3. 防火墙设置

### Q: 上传文件失败？

**检查：**
1. 文件大小是否超过500MB
2. 文件格式是否为ZIP/TAR.GZ
3. 后端日志错误信息

### Q: 训练任务启动失败？

**检查：**
1. ai-toolkit是否安装
2. Python环境是否正确
3. 查看 `data/logs/training_*.log`

### Q: 前端构建失败？

**解决：**
```bash
# 清理依赖重新安装
rm -rf node_modules package-lock.json
npm install

# 或使用pnpm
pnpm install
```

### Q: 跨域问题？

前端开发环境已配置代理，生产环境使用Nginx或同源部署。

## 性能建议

### 开发环境
- 使用SSD硬盘
- 至少8GB内存
- 使用pnpm代替npm（更快）

### 生产环境
- 前端使用CDN
- 后端使用反向代理
- 启用Gzip压缩
- 配置合理的JVM参数

## 监控和日志

### 前端日志
浏览器控制台

### 后端日志
```bash
# 应用日志
tail -f logs/application.log

# 错误日志
tail -f logs/error.log

# 训练日志
tail -f data/logs/training_*.log
```

### 系统监控
```bash
# CPU和内存
top

# GPU（如果有）
nvidia-smi -l 1

# 磁盘空间
df -h
```

## 下一步

现在您已经成功启动了完整的AI训练系统！

1. 📚 阅读 [README.md](README.md) 了解更多功能
2. 📖 查看 [QUICKSTART.md](QUICKSTART.md) 快速入门
3. 🧪 参考 [API-TEST.md](API-TEST.md) 进行API测试
4. 🚀 查看 [DEPLOYMENT.md](DEPLOYMENT.md) 了解生产部署
5. 💻 阅读 [frontend/README.md](frontend/README.md) 前端文档

## 获取帮助

- 📝 查看文档
- 🐛 提交Issue
- 💬 加入讨论组

祝您训练愉快！🎉
