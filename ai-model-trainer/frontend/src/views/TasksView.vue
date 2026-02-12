<template>
  <div class="tasks-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <div>
            <el-button @click="loadTasks" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="showCreateDialog">
              <el-icon><plus /></el-icon>创建任务
            </el-button>
          </div>
        </div>
      </template>

      <!-- 任务统计 -->
      <div class="task-stats">
        <el-statistic title="总任务" :value="tasks.length" />
        <el-statistic title="运行中" :value="runningCount" />
        <el-statistic title="已完成" :value="completedCount" />
        <el-statistic title="失败" :value="failedCount" />
      </div>

      <!-- 任务列表 -->
      <el-table :data="tasks" stripe v-loading="loading" empty-text="暂无任务">
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column label="数据集" min-width="120">
          <template #default="{ row }">
            {{ row.datasetName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="训练器" min-width="120">
          <template #default="{ row }">
            {{ row.trainerName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <div v-if="row.status === 'RUNNING' || row.status === 'PREPARING'">
              <el-progress :percentage="Math.round(row.progress || 0)" :status="row.status === 'RUNNING' ? '' : 'warning'" />
              <span class="step-info">{{ row.currentStep || 0 }} / {{ row.totalSteps || 0 }} 步</span>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="handleStart(row.taskId)">启动</el-button>
            <el-button v-if="row.status === 'RUNNING'" size="small" type="warning" @click="handleStop(row.taskId)">停止</el-button>
            <el-button size="small" @click="handleView(row)">详情</el-button>
            <el-button size="small" type="danger" :disabled="row.status === 'RUNNING'" @click="handleDelete(row.taskId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建任务弹窗 -->
    <el-dialog v-model="createVisible" title="创建任务" width="650px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="createForm" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="createForm.taskName" placeholder="请输入任务名称" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="选择数据集" prop="datasetId">
          <el-select v-model="createForm.datasetId" placeholder="请选择数据集" style="width: 100%" @change="onDatasetChange">
            <el-option
              v-for="ds in datasets"
              :key="ds.id"
              :label="`${ds.name}（${ds.imageCount} 张图片）`"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="选择训练器" prop="trainerId">
          <el-select v-model="createForm.trainerId" placeholder="请选择训练器" style="width: 100%" @change="onTrainerChange">
            <el-option
              v-for="t in trainers"
              :key="t.id"
              :label="`${t.name}（Python ${t.pythonVersion}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="YAML 配置" prop="yamlConfig">
          <YamlEditor v-model="createForm.yamlConfig" height="380px" />
        </el-form-item>

        <el-form-item>
          <el-button size="small" @click="loadTemplate">加载默认模板</el-button>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情弹窗 -->
    <el-dialog v-model="detailVisible" title="任务详情" width="650px">
      <el-form v-if="currentTask" label-width="100px" class="task-detail">
        <el-form-item label="任务名称">
          <el-input :model-value="currentTask.taskName" disabled />
        </el-form-item>

        <el-form-item label="数据集">
          <el-input :model-value="currentTask.datasetName || '-'" disabled />
        </el-form-item>

        <el-form-item label="训练器">
          <el-input :model-value="currentTask.trainerName || '-'" disabled />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-tag :type="getStatusType(currentTask.status)">{{ getStatusText(currentTask.status) }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进度">
              <el-progress
                v-if="currentTask.status === 'RUNNING' || currentTask.status === 'PREPARING'"
                :percentage="Math.round(currentTask.progress || 0)"
                style="width: 100%"
              />
              <span v-else>{{ Math.round(currentTask.progress || 0) }}%</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="当前步数">
              <el-input :model-value="`${currentTask.currentStep || 0} / ${currentTask.totalSteps || 0}`" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图片数量">
              <el-input :model-value="String(currentTask.imageCount || '-')" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="数据集路径">
          <el-input :model-value="currentTask.datasetPath || '-'" disabled />
        </el-form-item>

        <el-form-item label="训练器路径">
          <el-input :model-value="currentTask.trainerPath || '-'" disabled />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="创建时间">
              <el-input :model-value="formatTime(currentTask.createdAt)" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-input :model-value="formatTime(currentTask.startedAt)" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item v-if="currentTask.errorMessage" label="错误信息">
          <el-alert type="error" :closable="false" style="width: 100%">{{ currentTask.errorMessage }}</el-alert>
        </el-form-item>

        <el-form-item label="YAML 配置">
          <YamlEditor v-if="currentTask.yamlConfig" :model-value="currentTask.yamlConfig" :readonly="true" height="380px" />
          <span v-else class="no-data">暂无配置</span>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAllTasks, createTask, startTask, stopTask, deleteTask, getTask } from '@/api/training'
import YamlEditor from '@/components/YamlEditor.vue'
import { getDatasets } from '@/api/file'
import { getTrainers } from '@/api/trainer'
import type { TrainingTask, TaskStatus, Dataset, Trainer, CreateTaskRequest } from '@/types'

// 列表
const tasks = ref<TrainingTask[]>([])
const loading = ref(false)
let refreshTimer: number | null = null

// 创建
const createVisible = ref(false)
const formRef = ref()
const submitting = ref(false)
const datasets = ref<Dataset[]>([])
const trainers = ref<Trainer[]>([])
const createForm = ref<CreateTaskRequest>({ taskName: '', datasetId: '', trainerId: '', yamlConfig: '' })

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  datasetId: [{ required: true, message: '请选择数据集', trigger: 'change' }],
  trainerId: [{ required: true, message: '请选择训练器', trigger: 'change' }],
  yamlConfig: [{ required: true, message: '请填写 YAML 配置', trigger: 'blur' }]
}

// 详情
const detailVisible = ref(false)
const currentTask = ref<TrainingTask | null>(null)

// 统计
const runningCount = computed(() => tasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PREPARING').length)
const completedCount = computed(() => tasks.value.filter(t => t.status === 'COMPLETED').length)
const failedCount = computed(() => tasks.value.filter(t => t.status === 'FAILED').length)

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getAllTasks()
    tasks.value = res.tasks || []
  } catch { tasks.value = [] }
  finally { loading.value = false }
}

const loadDatasets = async () => {
  try {
    const res = await getDatasets()
    datasets.value = res.data ?? []
  } catch { datasets.value = [] }
}

const loadTrainers = async () => {
  try {
    const res = await getTrainers()
    trainers.value = res.data ?? []
  } catch { trainers.value = [] }
}

const showCreateDialog = async () => {
  createForm.value = { taskName: '', datasetId: '', trainerId: '', yamlConfig: '' }
  await Promise.all([loadDatasets(), loadTrainers()])
  createVisible.value = true
}

const getDatasetPath = () => {
  const ds = datasets.value.find(d => d.id === createForm.value.datasetId)
  return ds?.datasetPath || ''
}

const replaceDatasetPlaceholder = (yaml: string) => {
  const dsPath = getDatasetPath()
  if (dsPath) return yaml.replace(/\{\{DATASET_PATH}}/g, dsPath)
  return yaml
}

const onTrainerChange = (trainerId: string) => {
  const trainer = trainers.value.find(t => t.id === trainerId)
  if (trainer?.defaultYamlConfig) {
    createForm.value.yamlConfig = replaceDatasetPlaceholder(trainer.defaultYamlConfig)
  }
}

const onDatasetChange = () => {
  const yaml = createForm.value.yamlConfig
  if (!yaml) return
  createForm.value.yamlConfig = replaceDatasetPlaceholder(yaml)
}

const loadTemplate = () => {
  const trainer = trainers.value.find(t => t.id === createForm.value.trainerId)
  if (trainer?.defaultYamlConfig) {
    createForm.value.yamlConfig = replaceDatasetPlaceholder(trainer.defaultYamlConfig)
  } else {
    ElMessage.warning('请先选择训练器，或在训练器管理中配置默认模板')
  }
}

const handleCreate = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    await createTask(createForm.value)
    ElMessage.success('任务创建成功')
    createVisible.value = false
    await loadTasks()
  } catch (e: any) {
    if (e !== false) console.error('Create task failed:', e)
  } finally { submitting.value = false }
}

const handleStart = async (taskId: string) => {
  try {
    await startTask(taskId)
    ElMessage.success('任务已启动')
    await loadTasks()
  } catch (e) { console.error(e) }
}

const handleStop = async (taskId: string) => {
  await ElMessageBox.confirm('确定要停止此任务吗？', '提示', { type: 'warning' })
  try {
    await stopTask(taskId)
    ElMessage.success('任务已停止')
    await loadTasks()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleDelete = async (taskId: string) => {
  await ElMessageBox.confirm('确定要删除此任务吗？', '提示', { type: 'warning' })
  try {
    await deleteTask(taskId)
    ElMessage.success('任务已删除')
    await loadTasks()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

const handleView = async (task: TrainingTask) => {
  try {
    const res = await getTask(task.taskId!)
    currentTask.value = res.task
    detailVisible.value = true
  } catch (e) { console.error(e) }
}

const getStatusType = (status?: TaskStatus | string) => {
  const map: Record<string, string> = { RUNNING: 'primary', PREPARING: 'primary', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }
  return map[status || ''] || 'warning'
}

const getStatusText = (status?: TaskStatus | string) => {
  const map: Record<string, string> = { PENDING: '等待中', PREPARING: '准备中', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }
  return map[status || ''] || '未知'
}

const formatTime = (time?: string) => time ? new Date(time).toLocaleString('zh-CN') : '-'

onMounted(() => {
  loadTasks()
  refreshTimer = setInterval(loadTasks, 5000) as unknown as number
})
onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<style scoped>
.tasks-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-stats {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}
.step-info {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: block;
}
.task-detail {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
}
.no-data {
  color: #909399;
  font-size: 13px;
}
</style>
