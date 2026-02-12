# 更新日志

所有重要的项目变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
并且本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2024-02-12

### 新增功能

#### 核心功能
- ✅ 图片压缩包上传功能（支持ZIP、TAR.GZ格式）
- ✅ 自动图片提示词生成
- ✅ 集成ai-toolkit进行模型训练
- ✅ 完整的训练任务生命周期管理
- ✅ YAML配置文件管理
- ✅ 实时训练进度监控

#### API接口
- ✅ 文件上传API (`/api/files/upload`)
- ✅ 提示词管理API (`/api/files/prompts`)
- ✅ 训练任务CRUD API (`/api/training/tasks`)
- ✅ 任务启动/停止API
- ✅ 配置管理API (`/api/config`)

#### 技术特性
- ✅ Spring Boot 3.2.2 框架
- ✅ 异步任务处理
- ✅ 全局异常处理
- ✅ 日志记录和追踪
- ✅ 文件大小和格式验证
- ✅ 进程管理和监控

#### 文档
- ✅ 完整的README文档
- ✅ API测试指南
- ✅ 部署文档
- ✅ 快速开始指南
- ✅ 示例配置文件

#### 部署支持
- ✅ Docker支持
- ✅ Docker Compose配置
- ✅ 启动脚本（Windows/Linux）
- ✅ Systemd服务配置示例

#### 测试
- ✅ 单元测试框架
- ✅ FileUtil测试用例
- ✅ TaskManager测试用例

### 已知限制

- ⚠️ 任务数据暂存内存中，未持久化到数据库
- ⚠️ 提示词生成基于文件名，未集成AI模型
- ⚠️ 无用户认证和权限控制
- ⚠️ 不支持分布式训练
- ⚠️ 训练进度解析依赖ai-toolkit输出格式

### 依赖项

- Java 17+
- Maven 3.6+
- Python 3.8+ (ai-toolkit)
- Spring Boot 3.2.2
- Apache Commons Compress 1.25.0
- SnakeYAML 2.2
- Jackson 2.16.1

## [未来计划]

### v1.1.0（计划中）
- [ ] 数据库持久化支持
- [ ] 用户认证和权限管理
- [ ] Web管理界面
- [ ] 训练队列管理
- [ ] 资源使用监控

### v1.2.0（计划中）
- [ ] 集成AI模型进行智能提示词生成
- [ ] 支持更多基础模型
- [ ] 模型评估和比较功能
- [ ] 训练结果可视化

### v2.0.0（计划中）
- [ ] 分布式训练支持
- [ ] 集群管理
- [ ] 自动调参
- [ ] 模型版本管理
- [ ] API网关集成

## 版本说明

### 版本号规则

- **主版本号**：不兼容的API修改
- **次版本号**：向下兼容的功能性新增
- **修订号**：向下兼容的问题修正

### 维护政策

- 最新主版本：完全支持，定期更新
- 上一个主版本：安全更新和关键bug修复
- 更早版本：不再维护

## 贡献指南

欢迎提交Pull Request！在提交之前，请：

1. 更新相关文档
2. 添加或更新测试用例
3. 确保所有测试通过
4. 遵循现有代码风格
5. 在本文件中记录变更

## 反馈和支持

- 🐛 Bug报告：[GitHub Issues](https://github.com/your-repo/issues)
- 💡 功能建议：[GitHub Discussions](https://github.com/your-repo/discussions)
- 📧 邮件支持：support@example.com

---

**注意：** 本项目正在积极开发中。API可能会在1.0正式版之前发生变化。
