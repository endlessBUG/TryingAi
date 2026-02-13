<template>
  <div class="conda-config-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Conda 环境配置</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" v-loading="loading" class="config-form">
        <el-form-item label="Conda 路径" prop="condaPath">
          <el-input v-model="form.condaPath" placeholder="请输入 conda 可执行文件的绝对路径" />
          <div class="field-hint">
            例如：<code>C:\Users\xxx\miniconda3\condabin\conda.bat</code> 或 <code>/home/xxx/miniconda3/bin/conda</code>
          </div>
        </el-form-item>

        <el-form-item label="Conda 初始化命令">
          <el-input v-model="form.condaInitCommand" placeholder="可选，如需额外初始化命令" />
          <div class="field-hint">
            一般无需填写。如遇 conda activate 失败，可填写如 <code>conda init bash</code>
          </div>
        </el-form-item>

        <el-divider content-position="left">网络配置</el-divider>

        <el-form-item label="GitHub 代理">
          <el-input v-model="form.githubProxy" placeholder="可选，用于加速 git+https://github.com 依赖下载" />
          <div class="field-hint">
            国内网络安装依赖时可能无法从 GitHub 克隆仓库，填写代理地址可解决。<br/>
            例如：<code>https://ghfast.top/</code> 或 <code>https://mirror.ghproxy.com/</code>
          </div>
        </el-form-item>

        <el-divider content-position="left">状态检测</el-divider>

        <el-form-item label="检测结果">
          <div v-if="detectResult === null" class="detect-status">
            <el-button size="small" @click="handleDetect" :loading="detecting">检测 Conda</el-button>
          </div>
          <div v-else-if="detectResult" class="detect-status">
            <el-tag type="success">可用</el-tag>
            <span class="detect-info">{{ detectVersion }}</span>
            <el-button size="small" link @click="handleDetect" :loading="detecting">重新检测</el-button>
          </div>
          <div v-else class="detect-status">
            <el-tag type="danger">不可用</el-tag>
            <span class="detect-info detect-error">{{ detectError }}</span>
            <el-button size="small" link @click="handleDetect" :loading="detecting">重新检测</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { getSystemConfig, saveSystemConfig } from '@/api/systemConfig'

const loading = ref(false)
const saving = ref(false)
const detecting = ref(false)
const formRef = ref<FormInstance>()

const form = ref({
  condaPath: '',
  condaInitCommand: '',
  githubProxy: ''
})

const rules: FormRules = {
  condaPath: [{ required: true, message: '请输入 Conda 路径', trigger: 'blur' }]
}

const detectResult = ref<boolean | null>(null)
const detectVersion = ref('')
const detectError = ref('')

async function loadConfig() {
  loading.value = true
  try {
    const res = await getSystemConfig()
    const data = res.data || {}
    form.value.condaPath = data['conda.path'] || ''
    form.value.condaInitCommand = data['conda.init_command'] || ''
    form.value.githubProxy = data['github.proxy'] || ''
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await saveSystemConfig({
      'conda.path': form.value.condaPath,
      'conda.init_command': form.value.condaInitCommand,
      'github.proxy': form.value.githubProxy
    })
    ElMessage.success('保存成功')
    detectResult.value = null
  } finally {
    saving.value = false
  }
}

async function handleDetect() {
  if (!form.value.condaPath) {
    ElMessage.warning('请先填写 Conda 路径')
    return
  }
  detecting.value = true
  detectResult.value = null
  try {
    await saveSystemConfig({ 'conda.path': form.value.condaPath })
    const res = await getSystemConfig()
    const data = res.data || {}
    if (data['conda.path']) {
      detectResult.value = true
      detectVersion.value = 'Conda 路径已保存'
    }
  } catch {
    detectResult.value = false
    detectError.value = '检测失败，请检查路径是否正确'
  } finally {
    detecting.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.conda-config-view {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.config-form {
  max-width: 700px;
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.field-hint code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  color: #e6a23c;
}
.detect-status {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detect-info {
  font-size: 13px;
  color: #67c23a;
}
.detect-error {
  color: #f56c6c;
}
</style>
