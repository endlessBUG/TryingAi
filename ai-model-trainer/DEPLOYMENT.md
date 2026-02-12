# 部署指南

## 本地部署

### 前置条件

1. **Java环境**
   ```bash
   java -version  # 需要JDK 17+
   ```

2. **Maven**
   ```bash
   mvn -version  # 需要Maven 3.6+
   ```

3. **Python环境**（用于ai-toolkit）
   ```bash
   python --version  # 需要Python 3.8+
   ```

4. **ai-toolkit安装**
   ```bash
   git clone https://github.com/ostris/ai-toolkit.git
   cd ai-toolkit
   pip install -r requirements.txt
   ```

### 构建和运行

1. **克隆项目**
   ```bash
   cd ai-model-trainer
   ```

2. **修改配置**
   
   编辑 `src/main/resources/application.yml`：
   ```yaml
   trainer:
     ai-toolkit-path: /path/to/ai-toolkit  # 修改为实际路径
     python-path: python                    # 或 python3
   ```

3. **构建项目**
   ```bash
   mvn clean package -DskipTests
   ```

4. **运行应用**
   
   **Windows:**
   ```cmd
   start.bat
   ```
   
   **Linux/Mac:**
   ```bash
   chmod +x start.sh
   ./start.sh
   ```
   
   或直接运行：
   ```bash
   java -jar target/ai-model-trainer-1.0.0-SNAPSHOT.jar
   ```

5. **访问应用**
   
   打开浏览器访问：`http://localhost:8080`

## Docker部署

### 使用Docker Compose（推荐）

1. **构建镜像**
   ```bash
   docker-compose build
   ```

2. **启动服务**
   ```bash
   docker-compose up -d
   ```

3. **查看日志**
   ```bash
   docker-compose logs -f
   ```

4. **停止服务**
   ```bash
   docker-compose down
   ```

### 使用Docker（不使用Compose）

1. **构建镜像**
   ```bash
   mvn clean package -DskipTests
   docker build -t ai-model-trainer:latest .
   ```

2. **运行容器**
   ```bash
   docker run -d \
     --name ai-model-trainer \
     -p 8080:8080 \
     -v $(pwd)/data:/app/data \
     -v $(pwd)/ai-toolkit:/app/ai-toolkit \
     -e TRAINER_AI_TOOLKIT_PATH=/app/ai-toolkit \
     ai-model-trainer:latest
   ```

3. **GPU支持**
   ```bash
   docker run -d \
     --name ai-model-trainer \
     --gpus all \
     -p 8080:8080 \
     -v $(pwd)/data:/app/data \
     -v $(pwd)/ai-toolkit:/app/ai-toolkit \
     ai-model-trainer:latest
   ```

## 生产环境部署

### 系统要求

- **CPU**: 4核+
- **内存**: 8GB+ (16GB推荐)
- **存储**: 100GB+ SSD
- **GPU**: NVIDIA GPU (可选，用于训练加速)
- **操作系统**: Linux (Ubuntu 20.04+ 推荐)

### 部署步骤

1. **安装依赖**
   ```bash
   # Java
   sudo apt update
   sudo apt install openjdk-17-jdk -y
   
   # Python
   sudo apt install python3 python3-pip -y
   
   # CUDA (如果使用GPU)
   # 参考NVIDIA官方文档安装
   ```

2. **创建用户**
   ```bash
   sudo useradd -m -s /bin/bash aitrainer
   sudo su - aitrainer
   ```

3. **部署应用**
   ```bash
   cd /home/aitrainer
   git clone <your-repo>
   cd ai-model-trainer
   
   # 安装ai-toolkit
   git clone https://github.com/ostris/ai-toolkit.git
   cd ai-toolkit
   pip3 install -r requirements.txt
   cd ..
   
   # 构建应用
   mvn clean package -DskipTests
   ```

4. **配置systemd服务**
   
   创建 `/etc/systemd/system/ai-model-trainer.service`：
   ```ini
   [Unit]
   Description=AI Model Trainer Service
   After=network.target
   
   [Service]
   Type=simple
   User=aitrainer
   Group=aitrainer
   WorkingDirectory=/home/aitrainer/ai-model-trainer
   ExecStart=/usr/bin/java -jar /home/aitrainer/ai-model-trainer/target/ai-model-trainer-1.0.0-SNAPSHOT.jar
   Restart=always
   RestartSec=10
   StandardOutput=journal
   StandardError=journal
   
   # 资源限制
   LimitNOFILE=65536
   
   # 环境变量
   Environment="JAVA_OPTS=-Xmx4g -Xms1g"
   
   [Install]
   WantedBy=multi-user.target
   ```

5. **启动服务**
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable ai-model-trainer
   sudo systemctl start ai-model-trainer
   sudo systemctl status ai-model-trainer
   ```

6. **配置反向代理（可选）**
   
   使用Nginx：
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           
           # 文件上传大小限制
           client_max_body_size 500M;
       }
   }
   ```

### 性能优化

1. **JVM调优**
   ```bash
   export JAVA_OPTS="-Xmx8g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
   ```

2. **并发配置**
   
   修改 `application.yml`：
   ```yaml
   server:
     tomcat:
       threads:
         max: 200
         min-spare: 10
   ```

3. **文件上传优化**
   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 1GB
         max-request-size: 1GB
   ```

### 监控和日志

1. **应用日志**
   ```bash
   # systemd日志
   sudo journalctl -u ai-model-trainer -f
   
   # 应用日志
   tail -f logs/application.log
   ```

2. **训练日志**
   ```bash
   tail -f data/logs/training_*.log
   ```

3. **系统资源监控**
   ```bash
   # CPU和内存
   top
   
   # GPU使用
   nvidia-smi -l 1
   
   # 磁盘使用
   df -h
   ```

### 备份策略

1. **数据备份**
   ```bash
   # 备份数据目录
   tar -czf backup_$(date +%Y%m%d).tar.gz data/
   
   # 备份到远程
   rsync -avz data/ user@backup-server:/backups/ai-trainer/
   ```

2. **自动备份脚本**
   ```bash
   #!/bin/bash
   # /home/aitrainer/backup.sh
   
   BACKUP_DIR="/backups"
   DATE=$(date +%Y%m%d_%H%M%S)
   
   # 备份数据
   tar -czf $BACKUP_DIR/data_$DATE.tar.gz data/
   
   # 保留最近7天的备份
   find $BACKUP_DIR -name "data_*.tar.gz" -mtime +7 -delete
   ```

3. **添加到crontab**
   ```bash
   # 每天凌晨2点备份
   0 2 * * * /home/aitrainer/backup.sh
   ```

### 安全配置

1. **防火墙**
   ```bash
   sudo ufw allow 8080/tcp
   sudo ufw enable
   ```

2. **添加认证**（建议实现）
   - Spring Security
   - JWT Token
   - API Key

3. **HTTPS配置**
   - 使用Let's Encrypt证书
   - 配置Nginx SSL

### 故障恢复

1. **服务重启**
   ```bash
   sudo systemctl restart ai-model-trainer
   ```

2. **清理临时文件**
   ```bash
   rm -rf data/uploads/*
   rm -rf data/logs/*.log
   ```

3. **数据恢复**
   ```bash
   tar -xzf backup_20240212.tar.gz
   ```

## K8s部署（高级）

### 创建Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-model-trainer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ai-model-trainer
  template:
    metadata:
      labels:
        app: ai-model-trainer
    spec:
      containers:
      - name: ai-model-trainer
        image: ai-model-trainer:latest
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        volumeMounts:
        - name: data
          mountPath: /app/data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: ai-trainer-pvc
```

### 创建Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: ai-model-trainer
spec:
  selector:
    app: ai-model-trainer
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

## 常见问题

### Q: 启动失败
A: 检查Java版本、端口占用、ai-toolkit配置

### Q: 内存不足
A: 调整JVM参数，增加 `-Xmx` 值

### Q: 训练失败
A: 检查Python环境、ai-toolkit安装、GPU驱动

### Q: 文件上传超时
A: 增加上传超时时间和文件大小限制

## 联系支持

如遇到部署问题，请提交Issue或联系技术支持。
