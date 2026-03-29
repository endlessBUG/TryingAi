<template>
  <div class="ai-config-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI服务配置</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            添加配置
          </el-button>
        </div>
      </template>

      <!-- 服务类型标签页 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="(label, type) in SERVICE_TYPE_LABELS"
          :key="type"
          :label="label"
          :name="type"
        >
          <ConfigList
            :configs="filteredConfigs"
            :loading="configStore.loading"
            @edit="handleEdit"
            @delete="handleDelete"
            @toggle-active="handleToggleActive"
            @test="handleTest"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑配置' : '添加配置'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：OpenAI GPT-4" />
        </el-form-item>

        <el-form-item label="厂商" prop="provider">
          <el-select
            v-model="form.provider"
            placeholder="请选择厂商"
            @change="handleProviderChange"
            style="width: 100%"
          >
            <el-option
              v-for="provider in availableProviders"
              :key="provider.id"
              :label="provider.name"
              :value="provider.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-input-number
            v-model="form.priority"
            :min="0"
            :max="100"
            style="width: 100%"
          />
          <div class="form-tip">数值越大优先级越高</div>
        </el-form-item>

        <el-form-item label="模型" prop="model">
          <el-select
            v-model="form.model"
            placeholder="选择或输入模型名称"
            multiple
            filterable
            allow-create
            default-first-option
            collapse-tags
            collapse-tags-tooltip
            style="width: 100%"
          >
            <el-option
              v-for="model in availableModels"
              :key="model"
              :label="model"
              :value="model"
            />
          </el-select>
          <div class="form-tip">可直接输入模型名称或从列表选择，支持多个模型</div>
        </el-form-item>

        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
          <div class="form-tip">
            完整端点: {{ fullEndpointExample }}
          </div>
        </el-form-item>

        <el-form-item label="生成端点" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="可选，留空自动推断" />
          <div class="form-tip">生成请求的 API 端点路径，如 /chat/completions</div>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            placeholder="sk-..."
          />
          <div class="form-tip" v-if="form.provider === 'comfyui'">ComfyUI 可不填 API Key</div>
        </el-form-item>

        <!-- ComfyUI 工作流配置 -->
        <el-form-item v-if="form.provider === 'comfyui'" label="工作流" prop="workflowFilename">
          <el-select
            v-model="form.workflowFilename"
            placeholder="请选择工作流文件"
            style="width: 100%"
          >
            <el-option
              v-for="workflow in availableWorkflows"
              :key="workflow.filename"
              :label="workflow.name"
              :value="workflow.filename"
            />
          </el-select>
          <div class="form-tip">选择 ComfyUI 工作流 JSON 文件</div>
        </el-form-item>

        <el-form-item v-if="isEdit" label="启用状态">
          <el-switch v-model="form.isActive" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <AITestDialog v-model="testDialogVisible" :config="testingConfig" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useAIConfigStore } from '@/stores/aiConfig'
import { aiConfigAPI } from '@/api/aiConfig'
import { SERVICE_TYPE_LABELS, PROVIDER_CONFIGS } from '@/types/ai'
import type { AIConfig, AIServiceType, CreateAIConfigRequest, UpdateAIConfigRequest } from '@/types/ai'
import ConfigList from '@/components/ConfigList.vue'
import AITestDialog from '@/components/AITestDialog.vue'

const configStore = useAIConfigStore()

const activeTab = ref<AIServiceType>('text')
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number>()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const testDialogVisible = ref(false)
const testingConfig = ref<AIConfig | null>(null)

// ComfyUI 工作流列表（从目录读取的文件）
const comfyuiWorkflows = ref<{ filename: string; name: string; path: string }[]>([])

// 当前可用的工作流列表
const availableWorkflows = computed(() => {
  if (form.provider !== 'comfyui') return []
  return comfyuiWorkflows.value
})

const form = reactive<CreateAIConfigRequest & { isActive?: boolean; workflowFilename?: string }>({
  serviceType: 'text',
  provider: '',
  name: '',
  baseUrl: '',
  apiKey: '',
  model: [],
  endpoint: '',
  priority: 0,
  isActive: true,
  settings: '',
  workflowFilename: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' }
  ],
  apiKey: [
    {
      validator: (rule: any, value: any, callback: any) => {
        // ComfyUI 和 FishAudio 本地部署不需要 API Key
        if (form.provider !== 'comfyui' && form.provider !== 'fishaudio' && !value) {
          callback(new Error('请输入 API Key'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  model: [
    {
      validator: (rule: any, value: any, callback: any) => {
        if (form.provider !== 'comfyui' && (!value || value.length === 0)) {
          callback(new Error('请至少选择一个模型'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

// 当前服务类型对应的厂商列表
const availableProviders = computed(() => {
  return PROVIDER_CONFIGS[activeTab.value] || []
})

// 当前厂商对应的模型列表
const availableModels = computed(() => {
  if (!form.provider) return []
  const provider = availableProviders.value.find(p => p.id === form.provider)
  return provider?.models || []
})

// 完整端点示例
const fullEndpointExample = computed(() => {
  const baseUrl = form.baseUrl || 'https://api.example.com'
  const provider = form.provider
  const serviceType = activeTab.value

  let endpoint = ''
  if (provider === 'comfyui') {
    endpoint = '/prompt'
  } else if (provider === 'gemini') {
    endpoint = '/v1beta/models/{model}:generateContent'
  } else if (serviceType === 'text') {
    endpoint = '/chat/completions'
  } else if (serviceType === 'image') {
    endpoint = '/images/generations'
  } else if (serviceType === 'video') {
    endpoint = '/video/generations'
  } else if (serviceType === 'text_to_speech') {
    endpoint = '/audio/speech'
  }

  return baseUrl + endpoint
})

// 过滤当前服务类型的配置
const filteredConfigs = computed(() => {
  return configStore.configs.filter(c => c.serviceType === activeTab.value)
})

// 加载配置
const loadConfigs = () => {
  configStore.loadConfigs(activeTab.value)
}

// 标签页切换
const handleTabChange = (tabName: string | number) => {
  activeTab.value = tabName as AIServiceType
  loadConfigs()
}

// 厂商切换
const handleProviderChange = () => {
  form.model = []
  form.workflowFilename = ''

  // 设置默认URL和端点
  if (form.provider === 'comfyui') {
    form.baseUrl = 'http://127.0.0.1:8188'
    form.endpoint = '/prompt'
  } else if (form.provider === 'gemini') {
    form.baseUrl = 'https://api.chatfire.site'
    form.endpoint = ''
  } else if (form.provider === 'fishaudio') {
    form.baseUrl = 'https://fish.audio'
    form.endpoint = '/v1/tts'
  } else if (form.provider === 'volces') {
    form.baseUrl = 'https://api.volcengine.com'
    form.endpoint = activeTab.value === 'video' ? '/contents/generations/tasks' : '/chat/completions'
  } else {
    form.baseUrl = 'https://api.chatfire.site/v1'
    form.endpoint = ''
  }

  // 自动生成名称
  if (!isEdit.value) {
    const providerName = availableProviders.value.find(p => p.id === form.provider)?.name || form.provider
    const serviceName = SERVICE_TYPE_LABELS[activeTab.value]
    form.name = `${providerName}-${serviceName}-${Math.floor(Math.random() * 10000).toString().padStart(4, '0')}`
  }
}

// 显示创建对话框
const showCreateDialog = () => {
  isEdit.value = false
  editingId.value = undefined
  resetForm()
  form.serviceType = activeTab.value
  form.provider = 'chatfire'
  form.baseUrl = 'https://api.chatfire.site/v1'
  handleProviderChange()
  dialogVisible.value = true
}

// 编辑配置
const handleEdit = (config: AIConfig) => {
  isEdit.value = true
  editingId.value = config.id

  // 解析settings中的工作流文件名
  let workflowFilename = ''
  if (config.settings) {
    try {
      const settingsObj = JSON.parse(config.settings)
      workflowFilename = settingsObj.workflow_filename || ''
    } catch (e) {
      // ignore
    }
  }

  Object.assign(form, {
    serviceType: config.serviceType,
    provider: config.provider || 'chatfire',
    name: config.name,
    baseUrl: config.baseUrl,
    apiKey: config.apiKey,
    model: Array.isArray(config.model) ? config.model : [],
    endpoint: config.endpoint || '',
    priority: config.priority || 0,
    isActive: config.isActive,
    settings: config.settings || '',
    workflowFilename
  })
  dialogVisible.value = true
}

// 删除配置
const handleDelete = async (config: AIConfig) => {
  try {
    await ElMessageBox.confirm('确定要删除该配置吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await aiConfigAPI.delete(config.id)
    ElMessage.success('删除成功')
    loadConfigs()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 切换激活状态
const handleToggleActive = async (config: AIConfig) => {
  try {
    await aiConfigAPI.toggleActive(config.id)
    ElMessage.success(config.isActive ? '已禁用' : '已启用')
    loadConfigs()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 测试配置
const handleTest = (config: AIConfig) => {
  testingConfig.value = config
  testDialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 构建settings，ComfyUI需要保存工作流文件名
      let settings = form.settings
      if (form.provider === 'comfyui' && form.workflowFilename) {
        settings = JSON.stringify({ workflow_filename: form.workflowFilename })
      }

      if (isEdit.value && editingId.value) {
        const updateData: UpdateAIConfigRequest = {
          name: form.name,
          provider: form.provider,
          baseUrl: form.baseUrl,
          apiKey: form.apiKey,
          model: form.model,
          endpoint: form.endpoint,
          priority: form.priority,
          isActive: form.isActive,
          settings
        }
        await aiConfigAPI.update(editingId.value, updateData)
        ElMessage.success('更新成功')
      } else {
        const createData: CreateAIConfigRequest = {
          serviceType: form.serviceType,
          provider: form.provider,
          name: form.name,
          baseUrl: form.baseUrl,
          apiKey: form.apiKey,
          model: form.model,
          endpoint: form.endpoint,
          priority: form.priority,
          settings
        }
        await aiConfigAPI.create(createData)
        ElMessage.success('创建成功')
      }

      dialogVisible.value = false
      loadConfigs()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    serviceType: activeTab.value,
    provider: '',
    name: '',
    baseUrl: '',
    apiKey: '',
    model: [],
    endpoint: '',
    priority: 0,
    isActive: true,
    settings: '',
    workflowFilename: ''
  })
  formRef.value?.resetFields()
}

// 加载工作流配置
const loadWorkflows = async () => {
  try {
    const res = await aiConfigAPI.getComfyuiWorkflows()
    if (res.success && res.data) {
      comfyuiWorkflows.value = res.data as { filename: string; name: string; path: string }[]
    }
  } catch (e) {
    console.error('加载工作流列表失败', e)
  }
}

onMounted(() => {
  loadConfigs()
  loadWorkflows()
})
</script>

<style scoped>
.ai-config-view {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>