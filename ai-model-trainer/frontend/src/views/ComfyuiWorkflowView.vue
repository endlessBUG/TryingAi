<template>
  <div class="workflow-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>ComfyUI 工作流管理</span>
        </div>
      </template>

      <el-table :data="workflows" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleExecute(row)" :loading="row.executing">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑工作流' : '新增工作流'" width="900px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入工作流名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="文生图" value="text2img" />
            <el-option label="图生图" value="img2img" />
            <el-option label="视频生成" value="video" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="工作流描述" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="工作流JSON" prop="workflowJson">
          <el-input
            v-model="form.workflowJson"
            type="textarea"
            :rows="15"
            placeholder="请粘贴 ComfyUI 导出的工作流 JSON (API格式)"
          />
          <div class="field-hint">从 ComfyUI 界面导出 API 格式的工作流 JSON，可使用 {{param}} 占位符</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 执行对话框 -->
    <el-dialog v-model="executeDialogVisible" title="执行工作流" width="600px">
      <el-form label-width="100px">
        <el-form-item label="工作流">
          <el-input :model-value="currentWorkflow?.name" disabled />
        </el-form-item>
        <el-form-item label="参数">
          <div v-for="(param, key) in executeParams" :key="key" class="param-row">
            <el-input :model-value="key" disabled style="width: 120px" />
            <el-input v-model="executeParams[key]" :placeholder="`请输入 ${key}`" style="flex: 1" />
          </div>
          <el-empty v-if="Object.keys(executeParams).length === 0" description="无参数" :image-size="60" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="executing" @click="doExecute">执行</el-button>
      </template>
    </el-dialog>

    <!-- 结果对话框 -->
    <el-dialog v-model="resultDialogVisible" title="执行结果" width="800px">
      <div class="result-container">
        <div v-for="(images, nodeId) in resultImages" :key="nodeId" class="result-node">
          <div class="node-title">节点: {{ nodeId }}</div>
          <div class="result-images">
            <el-image
              v-for="img in images"
              :key="img.filename"
              :src="getImageUrl(img)"
              fit="contain"
              class="result-image"
              :preview-src-list="images.map(i => getImageUrl(i))"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { getWorkflows, createWorkflow, updateWorkflow, deleteWorkflow, executeWorkflow, getImageUrl } from '@/api/comfyui'

interface Workflow {
  id: string
  name: string
  category: string
  description: string
  workflowJson: string
  enabled: boolean
  executing?: boolean
  createdAt: string
}

const loading = ref(false)
const workflows = ref<Workflow[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = ref({
  name: '',
  category: '',
  description: '',
  workflowJson: '',
  enabled: true
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  workflowJson: [{ required: true, message: '请输入工作流JSON', trigger: 'blur' }]
}

const executeDialogVisible = ref(false)
const executing = ref(false)
const currentWorkflow = ref<Workflow | null>(null)
const executeParams = ref<Record<string, string>>({})

const resultDialogVisible = ref(false)
const resultImages = ref<Record<string, any[]>>({})

const loadWorkflows = async () => {
  loading.value = true
  try {
    const res = await getWorkflows()
    workflows.value = res || []
  } catch (e) {
    console.error('加载工作流失败', e)
    workflows.value = []
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  editingId.value = ''
  form.value = { name: '', category: '', description: '', workflowJson: '', enabled: true }
  dialogVisible.value = true
}

const handleEdit = (row: Workflow) => {
  isEdit.value = true
  editingId.value = row.id
  form.value = {
    name: row.name,
    category: row.category,
    description: row.description,
    workflowJson: row.workflowJson,
    enabled: row.enabled
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateWorkflow(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createWorkflow(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadWorkflows()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id: string) => {
  await deleteWorkflow(id)
  ElMessage.success('删除成功')
  await loadWorkflows()
}

const handleExecute = (row: Workflow) => {
  currentWorkflow.value = row
  // 解析占位符参数
  const params: Record<string, string> = {}
  const matches = row.workflowJson.match(/\{\{(\w+)\}\}/g) || []
  matches.forEach((m: string) => {
    const key = m.replace(/\{\{|\}\}/g, '')
    params[key] = ''
  })
  executeParams.value = params
  executeDialogVisible.value = true
}

const doExecute = async () => {
  if (!currentWorkflow.value) return
  executing.value = true
  currentWorkflow.value.executing = true
  try {
    const res = await executeWorkflow(currentWorkflow.value.id, executeParams.value)
    ElMessage.success('执行成功')
    executeDialogVisible.value = false
    // 显示结果
    resultImages.value = res.data || {}
    if (Object.keys(resultImages.value).length > 0) {
      resultDialogVisible.value = true
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '执行失败')
  } finally {
    executing.value = false
    currentWorkflow.value!.executing = false
  }
}

onMounted(loadWorkflows)
</script>

<style scoped>
.workflow-view {
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
}
.param-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.result-container {
  max-height: 60vh;
  overflow-y: auto;
}
.result-node {
  margin-bottom: 20px;
}
.node-title {
  font-weight: 600;
  margin-bottom: 10px;
  color: #606266;
}
.result-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.result-image {
  width: 200px;
  height: 200px;
  border-radius: 8px;
  cursor: pointer;
}
</style>