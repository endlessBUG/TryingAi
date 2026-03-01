<template>
  <div class="generator-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>提示词生成器</span>
          <el-button type="primary" @click="handleAdd">新增生成器</el-button>
        </div>
      </template>

      <el-table :data="generatorList" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.type]?.tag ?? 'info'" size="small">
              {{ typeTagMap[row.type]?.label ?? row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="服务地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" :loading="testingId === row.id" @click="handleTest(row)">测试</el-button>
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该生成器吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

    <!-- 图片测试对话框（CogVLM2 需要） -->
    <el-dialog v-model="testImageDialogVisible" title="上传测试图片" width="420px">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept="image/*"
        :on-change="onTestImageChange"
        :on-remove="() => testImageFile = undefined"
      >
        <el-icon style="font-size: 40px; color: #c0c4cc"><upload-filled /></el-icon>
        <div style="margin-top: 8px;">拖拽图片到此处，或点击选择</div>
        <template #tip>
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">支持 jpg / png / webp</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="testImageDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!testImageFile" :loading="!!testingId" @click="submitImageTest">
          开始测试
        </el-button>
      </template>
    </el-dialog>

      <!-- 测试结果 -->
      <el-alert
        v-if="testResult !== null"
        :title="testResult.success ? '连接成功' : '连接失败'"
        :type="testResult.success ? 'success' : 'error'"
        :closable="true"
        show-icon
        style="margin-top: 16px"
        @close="testResult = null"
      >
        <div class="test-result-content">{{ testResult.message }}</div>
      </el-alert>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑生成器' : '新增生成器'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：CogVLM 本地模型" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="CogVLM2" value="COGVLM2" />
            <el-option label="JoyCaption" value="JOYCAPTION" />
            <el-option label="OpenAI Vision" value="OPENAI_VISION" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="例如：http://localhost:8000" />
          <div class="field-hint">兼容 OpenAI Vision API 格式，将自动拼接 /v1/chat/completions</div>
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="例如：cogvlm2-llama3-chat-19B" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="告诉模型如何描述图片，留空使用默认提示词"
          />
          <div class="field-hint">
            默认：Describe this image in detail for AI training. Output comma-separated English tags.
          </div>
        </el-form-item>
        <el-form-item label="最大 Token 数">
          <el-input-number v-model="form.maxTokens" :min="100" :max="8000" :step="100" style="width: 200px" />
          <div class="field-hint">控制生成描述的最大长度，详细描述建议 1500～2000，默认 1000</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance, FormRules, UploadInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  getPromptGenerators, createPromptGenerator,
  updatePromptGenerator, deletePromptGenerator, testPromptGenerator
} from '@/api/promptGenerator'
import type { PromptGenerator } from '@/types'
import { GeneratorType } from '@/types'

const loading = ref(false)
const generatorList = ref<PromptGenerator[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const testingId = ref('')
const testResult = ref<{ success: boolean; message: string } | null>(null)
const testImageDialogVisible = ref(false)
const testImageFile = ref<File | undefined>()
const testingRow = ref<PromptGenerator | null>(null)
const uploadRef = ref<UploadInstance>()

const typeTagMap: Record<string, { label: string; tag: string }> = {
  COGVLM2: { label: 'CogVLM2', tag: 'success' },
  JOYCAPTION: { label: 'JoyCaption', tag: 'warning' },
  OPENAI_VISION: { label: 'OpenAI Vision', tag: 'primary' }
}

const defaultForm = (): PromptGenerator => ({
  name: '',
  type: GeneratorType.COGVLM2,
  baseUrl: '',
  modelName: '',
  systemPrompt: '',
  maxTokens: 1000,
  enabled: true
})

const form = ref<PromptGenerator>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入服务地址', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await getPromptGenerators()
    generatorList.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  editingId.value = ''
  form.value = defaultForm()
  dialogVisible.value = true
}

function handleEdit(row: PromptGenerator) {
  isEdit.value = true
  editingId.value = row.id || ''
  form.value = {
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    modelName: row.modelName,
    systemPrompt: row.systemPrompt || '',
    maxTokens: row.maxTokens ?? 1000,
    enabled: row.enabled
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePromptGenerator(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createPromptGenerator(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: string) {
  await deletePromptGenerator(id)
  ElMessage.success('删除成功')
  await loadList()
}

function handleTest(row: PromptGenerator) {
  if (!row.id) return
  testResult.value = null
  if (row.type === 'COGVLM2' || row.type === 'JOYCAPTION') {
    testingRow.value = row
    testImageFile.value = undefined
    uploadRef.value?.clearFiles()
    testImageDialogVisible.value = true
  } else {
    doTest(row)
  }
}

function onTestImageChange(_file: any, fileList: any[]) {
  testImageFile.value = fileList[0]?.raw
}

async function submitImageTest() {
  if (!testingRow.value?.id || !testImageFile.value) return
  testImageDialogVisible.value = false
  await doTest(testingRow.value, testImageFile.value)
}

async function doTest(row: PromptGenerator, file?: File) {
  if (!row.id) return
  testingId.value = row.id
  testResult.value = null
  try {
    const res = await testPromptGenerator(row.id, file)
    if (res.success) {
      testResult.value = { success: true, message: '模型回复: ' + (res.data || '(空)') }
    } else {
      testResult.value = { success: false, message: res.message || '连接失败' }
    }
  } catch (e: any) {
    testResult.value = { success: false, message: e.message || '请求异常' }
  } finally {
    testingId.value = ''
  }
}

onMounted(loadList)
</script>

<style scoped>
.generator-view {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.test-result-content {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
