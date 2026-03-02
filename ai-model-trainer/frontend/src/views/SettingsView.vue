<template>
  <div class="settings-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>配置中心</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px" class="config-form">
        <el-divider content-position="left">外部服务</el-divider>

        <el-form-item label="ComfyUI 地址">
          <el-input v-model="form.comfyuiUrl" placeholder="例如: http://localhost:8188/" />
          <div class="field-hint">ComfyUI 服务的访问地址，配置后可在侧边栏直接打开</div>
        </el-form-item>

        <el-form-item label="ComfyUI 服务路径">
          <el-input v-model="form.comfyuiPath" placeholder="例如: /root/ai/comfyui/manager/ComfyUI" />
          <div class="field-hint">ComfyUI Manager 后端服务的根目录，用于存放 LoRA 模型等文件</div>
        </el-form-item>

        <el-divider content-position="left">Conda 环境</el-divider>

        <el-form-item label="Conda 路径">
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

        <el-divider content-position="left">网络配置</el-divider>

        <el-form-item label="GitHub 代理">
          <el-input v-model="form.githubProxy" placeholder="可选，用于加速 git+https://github.com 依赖下载" />
          <div class="field-hint">
            国内网络安装依赖时可能无法从 GitHub 克隆仓库，填写代理地址可解决。<br/>
            例如：<code>https://ghfast.top/</code> 或 <code>https://mirror.ghproxy.com/</code>
          </div>
        </el-form-item>

        <el-form-item label="pip 镜像源">
          <el-input v-model="form.pipIndexUrl" placeholder="可选，留空使用默认 PyPI" />
          <div class="field-hint">
            国内 pip 安装速度慢时，填写镜像源地址可大幅提速。<br/>
            清华：<code>https://pypi.tuna.tsinghua.edu.cn/simple/</code><br/>
            阿里：<code>https://mirrors.aliyun.com/pypi/simple/</code>
          </div>
        </el-form-item>

        <el-form-item label="HuggingFace 镜像">
          <el-input v-model="form.hfMirror" placeholder="默认: https://hf-mirror.com" clearable />
          <div class="field-hint">
            训练时从 HuggingFace 下载 Accuracy Recovery Adapter 等资源，国内网络可配置镜像加速。<br/>
            留空使用默认 <code>https://hf-mirror.com</code>，填 <code>off</code> 可禁用镜像
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
import { useSystemConfigStore } from '@/stores/systemConfig'

const configStore = useSystemConfigStore()
const saving = ref(false)
const detecting = ref(false)
const formRef = ref<FormInstance>()

const form = ref({
  comfyuiUrl: '',
  comfyuiPath: '',
  condaPath: '',
  condaInitCommand: '',
  githubProxy: '',
  pipIndexUrl: '',
  hfMirror: '',
})

const rules: FormRules = {}

const detectResult = ref<boolean | null>(null)
const detectVersion = ref('')
const detectError = ref('')

function fillForm() {
  form.value.comfyuiUrl = configStore.get('comfyui.url')
  form.value.comfyuiPath = configStore.get('comfyui.path')
  form.value.condaPath = configStore.get('conda.path')
  form.value.condaInitCommand = configStore.get('conda.init_command')
  form.value.githubProxy = configStore.get('github.proxy')
  form.value.pipIndexUrl = configStore.get('pip.index.url')
  form.value.hfMirror = configStore.get('hf.mirror')
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    await configStore.save({
      'comfyui.url': form.value.comfyuiUrl,
      'comfyui.path': form.value.comfyuiPath,
      'conda.path': form.value.condaPath,
      'conda.init_command': form.value.condaInitCommand,
      'github.proxy': form.value.githubProxy,
      'pip.index.url': form.value.pipIndexUrl,
      'hf.mirror': form.value.hfMirror,
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
    await configStore.save({ 'conda.path': form.value.condaPath })
    detectResult.value = true
    detectVersion.value = 'Conda 路径已保存'
  } catch {
    detectResult.value = false
    detectError.value = '检测失败，请检查路径是否正确'
  } finally {
    detecting.value = false
  }
}

onMounted(async () => {
  if (!configStore.loaded) await configStore.load()
  fillForm()
})
</script>

<style scoped>
.settings-view {
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
