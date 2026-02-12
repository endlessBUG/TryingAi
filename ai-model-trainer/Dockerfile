# AI Model Trainer Dockerfile
FROM openjdk:17-slim

# 安装Python和必要的系统依赖
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    git \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制Maven构建产物
COPY target/ai-model-trainer-1.0.0-SNAPSHOT.jar /app/app.jar

# 创建数据目录
RUN mkdir -p /app/data/uploads \
    /app/data/datasets \
    /app/data/outputs \
    /app/data/configs \
    /app/data/logs \
    /app/logs

# 复制配置文件
COPY src/main/resources/application.yml /app/config/application.yml

# 暴露端口
EXPOSE 8080

# 设置环境变量
ENV JAVA_OPTS="-Xmx2g -Xms512m"
ENV SPRING_CONFIG_LOCATION=/app/config/application.yml

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
