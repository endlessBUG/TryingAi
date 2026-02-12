# 🧪 项目测试指南

## 项目状态检查 ✅

### 前端文件验证
```
✅ frontend/package.json - 存在
✅ frontend/src/views/UploadView.vue - 存在
✅ frontend/src/views/TasksView.vue - 存在
✅ frontend/src/views/TaskCreateView.vue - 存在
✅ frontend/src/views/ConfigView.vue - 存在
✅ frontend/README.md - 存在
```

### 文档文件验证
```
✅ PROJECT-COMPLETE.md
✅ FRONTEND-SETUP.md
✅ QUICKSTART.md
✅ API-TEST.md
✅ DEPLOYMENT.md
✅ 共9个文档文件
```

## 🚀 测试步骤

### 步骤1️⃣：验证后端环境

**检查Java版本：**
```bash
java -version
```
应该显示Java 17或更高版本。

**检查Maven：**
```bash
mvn -version
```

### 步骤2️⃣：测试后端编译

**编译项目：**
```bash
cd ai-model-trainer
mvn clean compile
```

**如果缺少pom.xml，请确保：**
- 检查是否在正确的目录
- pom.xml文件已创建

### 步骤3️⃣：测试前端环境

**检查Node.js：**
```bash
node -v
npm -v
```

**安装前端依赖：**
```bash
cd frontend
npm install
```

**预期输出：**
- 安装Element Plus、Vue 3、TypeScript等依赖
- 生成node_modules目录

### 步骤4️⃣：启动前端开发服务器

```bash
npm run dev
```

**预期结果：**
```
VITE v5.0.11  ready in XXX ms

➜  Local:   http://localhost:3000/
➜  Network: use --host to expose
```

**访问测试：**
打开浏览器访问 http://localhost:3000

**应该看到：**
- AI Model Trainer界面
- 左侧导航菜单
- 顶部显示"AI Toolkit 未配置"（正常，因为后端未启动）

### 步骤5️⃣：测试前端功能（无后端）

**可以验证的前端功能：**
1. ✅ 页面路由切换
2. ✅ 上传页面UI显示
3. ✅ 任务列表页面显示
4. ✅ 创建任务表单显示
5. ✅ 配置管理页面显示

**预期行为：**
- 页面正常显示
- 点击菜单可切换页面
- 表单输入框正常工作
- 但API调用会失败（因为后端未启动）

### 步骤6️⃣：启动后端服务

**方法1：使用启动脚本**
```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

**方法2：使用Maven**
```bash
mvn spring-boot:run
```

**预期输出：**
```
==============================================
AI Model Trainer Application Started Successfully!
==============================================
```

**验证后端：**
```bash
curl http://localhost:8080/api/training/validate
```

### 步骤7️⃣：完整功能测试

**前提条件：**
- 后端已启动（端口8080）
- 前端已启动（端口3000）
- 浏览器已打开 http://localhost:3000

**测试流程：**

#### Test 1: 页面导航
```
1. 访问 http://localhost:3000
2. 应自动跳转到 /upload
3. 点击各菜单项，验证页面切换
4. 顶部状态应显示"AI Toolkit"状态
```

#### Test 2: 配置管理
```
访问：http://localhost:3000/config
1. 点击"加载模板"
2. 应该加载默认配置
3. 修改一些参数
4. 在"可视化编辑"和"YAML编辑"之间切换
```

#### Test 3: 任务列表（空）
```
访问：http://localhost:3000/tasks
1. 应显示任务列表（初始为空）
2. 任务统计显示全0
3. 点击"刷新"按钮
4. 点击"创建新任务"跳转到创建页面
```

#### Test 4: 创建任务（无数据集）
```
访问：http://localhost:3000/tasks/create
1. 应显示警告："请先上传数据集"
2. 表单字段显示但数据集路径为空
3. 点击"返回"回到任务列表
```

## 🎯 模拟完整流程测试

### 准备测试数据

**创建测试图片：**
1. 准备5-10张测试图片（JPG/PNG）
2. 将图片放入文件夹
3. 压缩为ZIP文件（如：test-images.zip）

### 完整测试流程

#### 1. 上传测试
```
页面：http://localhost:3000/upload

操作：
1. 拖拽test-images.zip到上传区
2. 选中"自动生成提示词"
3. 点击"开始上传"
4. 等待上传完成

验证：
✓ 显示上传进度条
✓ 上传成功后显示图片列表
✓ 每张图片有对应的提示词
✓ 可以编辑提示词
```

#### 2. 任务创建测试
```
点击"创建训练任务"按钮

验证：
✓ 自动跳转到创建页面
✓ 数据集路径已自动填充
✓ 图片数量已显示
✓ 填写任务名称
✓ 配置训练参数
✓ 点击"创建任务"
✓ 成功后跳转到任务列表
```

#### 3. 任务管理测试
```
页面：http://localhost:3000/tasks

验证：
✓ 任务列表显示新创建的任务
✓ 状态为"等待中"
✓ 点击"启动"按钮
✓ 状态变为"运行中"
✓ 显示进度条
✓ 页面每5秒自动刷新
✓ 点击"详情"查看任务信息
```

## 📊 测试检查清单

### 前端测试 ✅
- [ ] 安装依赖成功
- [ ] 开发服务器启动成功
- [ ] 所有页面可访问
- [ ] 页面路由正常
- [ ] UI组件显示正常
- [ ] 表单验证工作
- [ ] API请求可发送

### 后端测试 ✅
- [ ] 编译成功
- [ ] 应用启动成功
- [ ] API端点响应正常
- [ ] 文件上传功能
- [ ] 任务CRUD操作
- [ ] 配置管理功能

### 集成测试 ✅
- [ ] 前后端通信正常
- [ ] 文件上传完整流程
- [ ] 任务创建完整流程
- [ ] 任务管理完整流程
- [ ] 错误处理正确

## 🐛 常见问题排查

### 前端问题

**问题1：npm install失败**
```bash
# 清理缓存重试
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

**问题2：端口3000被占用**
```
修改 frontend/vite.config.ts:
server: {
  port: 3001  // 改为其他端口
}
```

**问题3：API请求失败**
```
检查：
1. 后端是否启动（http://localhost:8080）
2. vite.config.ts中的proxy配置是否正确
3. 浏览器控制台是否有错误
```

### 后端问题

**问题1：编译失败**
```
检查：
1. Java版本是否正确
2. Maven配置是否正确
3. 依赖是否能正常下载
```

**问题2：启动失败**
```
检查：
1. 端口8080是否被占用
2. application.yml配置是否正确
3. 查看日志文件
```

## 📝 测试报告模板

```
测试日期：____________________
测试人员：____________________

环境信息：
- 操作系统：__________________
- Java版本：__________________
- Node.js版本：_______________

测试结果：

1. 前端启动：[ ] 成功  [ ] 失败
2. 后端启动：[ ] 成功  [ ] 失败
3. 页面访问：[ ] 成功  [ ] 失败
4. API通信：[ ] 成功  [ ] 失败
5. 文件上传：[ ] 成功  [ ] 失败
6. 任务创建：[ ] 成功  [ ] 失败

问题记录：
_________________________________
_________________________________

备注：
_________________________________
```

## 🎓 下一步

测试完成后：
1. 📖 阅读 [FRONTEND-SETUP.md](FRONTEND-SETUP.md)
2. 🚀 参考 [QUICKSTART.md](QUICKSTART.md)
3. 📚 查看 [API-TEST.md](API-TEST.md)

## 🎉 测试成功！

如果所有测试通过，恭喜！您的AI Model Trainer系统已经准备就绪！

现在可以：
- 开始训练您的第一个模型
- 探索更多高级功能
- 根据需求定制开发

祝您使用愉快！🚀
