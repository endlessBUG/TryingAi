# AI Model Trainer - Frontend

AI模型训练系统的Vue 3前端界面。

## 技术栈

- **Vue 3** - 渐进式JavaScript框架
- **TypeScript** - 类型安全
- **Vite** - 快速构建工具
- **Element Plus** - UI组件库
- **Vue Router** - 路由管理
- **Pinia** - 状态管理
- **Axios** - HTTP客户端

## 功能特性

### 1. 文件上传
- 拖拽上传ZIP/TAR.GZ压缩包
- 自动解压和图片提取
- 提示词自动生成
- 提示词批量编辑

### 2. 任务管理
- 创建训练任务
- 任务列表查看
- 实时进度监控
- 任务启动/停止/删除
- 任务详情查看

### 3. 配置管理
- 可视化配置编辑
- YAML直接编辑
- 配置模板加载
- 配置保存导出

## 快速开始

### 1. 安装依赖

```bash
npm install
# 或
pnpm install
# 或
yarn install
```

### 2. 启动开发服务器

```bash
npm run dev
```

应用将在 http://localhost:3000 启动。

### 3. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist` 目录。

### 4. 预览生产构建

```bash
npm run preview
```

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API接口
│   │   ├── file.ts       # 文件管理API
│   │   ├── training.ts   # 训练任务API
│   │   └── config.ts     # 配置管理API
│   ├── assets/           # 静态资源
│   ├── components/       # 公共组件
│   │   └── Layout.vue    # 布局组件
│   ├── router/           # 路由配置
│   │   └── index.ts
│   ├── stores/           # 状态管理
│   │   └── task.ts       # 任务Store
│   ├── types/            # 类型定义
│   │   └── index.ts
│   ├── utils/            # 工具函数
│   │   └── request.ts    # HTTP请求
│   ├── views/            # 页面组件
│   │   ├── UploadView.vue      # 上传页面
│   │   ├── TasksView.vue       # 任务列表
│   │   ├── TaskCreateView.vue  # 创建任务
│   │   └── ConfigView.vue      # 配置管理
│   ├── App.vue           # 根组件
│   ├── main.ts           # 入口文件
│   └── env.d.ts          # 类型声明
├── index.html            # HTML模板
├── vite.config.ts        # Vite配置
├── tsconfig.json         # TypeScript配置
├── package.json          # 项目配置
└── README.md             # 本文件
```

## 开发指南

### 代理配置

开发环境下，API请求会被代理到后端服务器：

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

### 添加新页面

1. 在 `src/views/` 创建新的Vue组件
2. 在 `src/router/index.ts` 添加路由配置
3. 在 `src/components/Layout.vue` 添加菜单项

### 添加新API

1. 在 `src/types/index.ts` 定义类型
2. 在 `src/api/` 创建API函数
3. 在组件中导入使用

## 使用说明

### 上传训练数据

1. 点击"上传图片"菜单
2. 拖拽或点击上传ZIP文件
3. 等待上传和解压完成
4. 编辑提示词（可选）
5. 点击"创建训练任务"

### 创建训练任务

1. 从上传页面跳转，或直接访问"创建任务"
2. 填写任务名称
3. 配置训练参数：
   - 基础模型路径
   - 训练步数
   - 学习率
   - LoRA参数等
4. 点击"创建任务"

### 管理训练任务

1. 访问"任务列表"页面
2. 查看所有任务状态
3. 操作：
   - 启动待处理任务
   - 停止运行中任务
   - 查看任务详情
   - 删除已完成任务
4. 页面自动每5秒刷新

### 配置管理

1. 访问"配置管理"页面
2. 使用可视化编辑或YAML编辑
3. 加载默认模板
4. 修改配置
5. 保存到服务器

## 组件说明

### UploadView
- 文件上传界面
- 支持拖拽上传
- 显示图片列表
- 提示词编辑

### TasksView
- 任务列表展示
- 任务统计
- 实时进度显示
- 任务操作按钮

### TaskCreateView
- 任务创建表单
- 参数配置
- 表单验证

### ConfigView
- 配置编辑器
- 可视化/YAML双模式
- 模板加载

### Layout
- 应用布局
- 侧边导航
- 状态显示

## API接口

### 文件API
- `POST /api/files/upload` - 上传文件
- `PUT /api/files/prompts` - 更新提示词
- `POST /api/files/prompts/regenerate` - 重新生成提示词

### 训练API
- `GET /api/training/tasks` - 获取任务列表
- `POST /api/training/tasks` - 创建任务
- `GET /api/training/tasks/:id` - 获取任务详情
- `POST /api/training/tasks/:id/start` - 启动任务
- `POST /api/training/tasks/:id/stop` - 停止任务
- `DELETE /api/training/tasks/:id` - 删除任务
- `GET /api/training/validate` - 验证ai-toolkit

### 配置API
- `GET /api/config/template/training` - 获取模板
- `POST /api/config/yaml` - 保存配置
- `GET /api/config/yaml` - 读取配置

## 常见问题

### Q: 如何修改后端地址？
A: 修改 `vite.config.ts` 中的 proxy.target

### Q: 上传文件大小限制？
A: 默认500MB，可在后端配置修改

### Q: 如何添加新的模型类型？
A: 在 `TaskCreateView.vue` 的模型类型下拉框中添加

### Q: 任务进度不更新？
A: 检查后端是否正常运行，页面会每5秒自动刷新

### Q: 如何自定义主题？
A: Element Plus支持主题定制，参考官方文档

## 开发规范

### 代码风格
- 使用TypeScript
- 遵循Vue 3 Composition API
- 使用 `<script setup>` 语法
- 类型定义完整

### 命名规范
- 组件：PascalCase (例如：UploadView.vue)
- 文件：camelCase (例如：request.ts)
- 变量：camelCase
- 常量：UPPER_SNAKE_CASE

### Git提交
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式
- refactor: 重构
- test: 测试
- chore: 构建/工具

## 性能优化

- 路由懒加载
- 组件按需导入
- 图片懒加载（可添加）
- 请求防抖（可添加）
- 虚拟滚动（大数据列表可添加）

## 浏览器兼容性

- Chrome >= 87
- Firefox >= 78
- Safari >= 14
- Edge >= 88

## 许可证

MIT License

## 联系方式

如有问题，请提交Issue或联系开发团队。
