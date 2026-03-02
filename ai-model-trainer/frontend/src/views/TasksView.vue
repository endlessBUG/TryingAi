<template>
  <div class="tasks-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <div>
            <el-button
              v-if="selectedTasks.length >= 2"
              type="warning"
              @click="showCompareDialog"
            >
              对比 ({{ selectedTasks.length }})
            </el-button>
            <el-button @click="loadTasks" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="showCreateDialog">
              <el-icon><plus /></el-icon>创建任务
            </el-button>
          </div>
        </div>
      </template>

      <!-- 任务统计 -->
      <div class="task-stats">
        <div class="stat-card stat-total">
          <div class="stat-icon"><el-icon :size="22"><document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ tasks.length }}</div>
            <div class="stat-label">总任务</div>
          </div>
        </div>
        <div class="stat-card stat-running">
          <div class="stat-icon"><el-icon :size="22"><refresh /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ runningCount }}</div>
            <div class="stat-label">运行中</div>
          </div>
        </div>
        <div class="stat-card stat-completed">
          <div class="stat-icon"><el-icon :size="22"><circle-check /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ completedCount }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </div>
        <div class="stat-card stat-failed">
          <div class="stat-icon"><el-icon :size="22"><circle-close /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ failedCount }}</div>
            <div class="stat-label">失败</div>
          </div>
        </div>
      </div>

      <!-- 筛选提示 -->
      <div v-if="filterDatasetName" style="margin-bottom: 12px">
        <el-tag closable @close="clearFilter">
          数据集: {{ filterDatasetName }}
        </el-tag>
      </div>

      <!-- 任务列表 -->
      <el-table :data="tasks" stripe v-loading="loading" empty-text="暂无任务" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="40" />
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
            <el-button v-if="['COMPLETED','FAILED','CANCELLED'].includes(row.status)" size="small" type="success" @click="handleRestart(row.taskId)">重新训练</el-button>
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
          <el-button size="small" type="success" :loading="recommending" @click="handleRecommend">智能推荐参数</el-button>
        </el-form-item>

        <el-alert v-if="recommendResult" type="info" :closable="true" style="margin-bottom: 12px" @close="recommendResult = null">
          <template #title>
            <span style="font-weight: 600">推荐参数</span>（{{ recommendResult.imageCount }} 张图片，平均分辨率 {{ recommendResult.avgResolution }}px）
          </template>
          <div style="margin-top: 4px; line-height: 1.8; font-size: 13px">
            Steps: <b>{{ recommendResult.steps }}</b> |
            学习率: <b>{{ recommendResult.learningRate }}</b> |
            Rank: <b>{{ recommendResult.networkRank }}</b> |
            Alpha: <b>{{ recommendResult.networkAlpha }}</b> |
            Batch: <b>{{ recommendResult.batchSize }}</b> |
            分辨率: <b>{{ recommendResult.resolution }}</b><br/>
            <span style="color: #909399">{{ recommendResult.reason }}</span>
          </div>
        </el-alert>
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

        <el-form-item label="虚拟环境">
          <el-input :model-value="currentTask.condaEnvName || '-'" disabled />
        </el-form-item>

        <el-form-item label="执行命令">
          <el-input :model-value="currentTask.executeCommand || '-'" disabled type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" />
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

        <el-form-item v-if="lossChartData.length > 0" label="Loss 曲线">
          <div class="loss-chart-wrapper">
            <v-chart :option="lossChartOption" autoresize style="width: 100%; height: 260px" />
          </div>
        </el-form-item>

        <el-form-item label="日志文件">
          <div v-if="currentTask.logPath" style="display: flex; align-items: center; gap: 8px; width: 100%">
            <el-input :model-value="currentTask.logPath" disabled style="flex: 1" />
            <el-button type="primary" size="small" @click="openLogViewer">查看日志</el-button>
          </div>
          <span v-else class="no-data">暂无日志文件</span>
        </el-form-item>

        <el-form-item v-if="currentTask.errorMessage" label="错误信息">
          <el-alert type="error" :closable="false" style="width: 100%">
            {{ currentTask.errorMessage }}
            <el-button v-if="currentTask.logPath" type="danger" size="small" link style="margin-left: 8px" @click="openLogViewer">
              查看完整日志
            </el-button>
          </el-alert>
        </el-form-item>

        <el-form-item label="YAML 配置">
          <YamlEditor v-if="currentTask.yamlConfig" :model-value="currentTask.yamlConfig" :readonly="true" height="380px" />
          <span v-else class="no-data">暂无配置</span>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 日志查看弹窗 -->
    <el-dialog v-model="logVisible" title="训练日志" width="900px" top="5vh" @close="closeLogViewer">
      <div v-loading="logLoading" class="log-viewer" ref="logViewerRef">
        <pre>{{ logContent }}</pre>
      </div>
      <template #footer>
        <el-checkbox v-model="logAutoScroll" style="margin-right: auto">自动滚动</el-checkbox>
        <el-button @click="logVisible = false">关闭</el-button>
        <el-button type="primary" @click="refreshLog">刷新</el-button>
      </template>
    </el-dialog>

    <!-- 实验对比弹窗 -->
    <el-dialog v-model="compareVisible" title="实验对比" width="900px">
      <el-table :data="compareTasks" border stripe size="small">
        <el-table-column prop="taskName" label="任务名称" min-width="120" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="步数" width="80" align="center">
          <template #default="{ row }">{{ row.totalSteps || '-' }}</template>
        </el-table-column>
        <el-table-column label="最终 Loss" width="100" align="center">
          <template #default="{ row }">{{ getLastLoss(row) }}</template>
        </el-table-column>
        <el-table-column label="训练器" width="100">
          <template #default="{ row }">{{ row.trainerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="compareChartData.length > 0" class="loss-chart-wrapper" style="margin-top: 16px">
        <v-chart :option="compareChartOption" autoresize style="width: 100%; height: 300px" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Document, Refresh, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { getAllTasks, createTask, startTask, stopTask, restartTask, deleteTask, getTask, recommendParams, getTaskLog } from '@/api/training'
import YamlEditor from '@/components/YamlEditor.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, MarkLineComponent, LegendComponent } from 'echarts/components'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, MarkLineComponent, LegendComponent])
import { getDatasets } from '@/api/file'
import { getTrainers } from '@/api/trainer'
import type { TrainingTask, TaskStatus, Dataset, Trainer, CreateTaskRequest } from '@/types'

const route = useRoute()
const router = useRouter()

// 列表
const allTasks = ref<TrainingTask[]>([])
const loading = ref(false)
let refreshTimer: number | null = null
const filterDatasetName = ref((route.query.datasetName as string) || '')

const tasks = computed(() => {
  if (!filterDatasetName.value) return allTasks.value
  return allTasks.value.filter(t => t.datasetName === filterDatasetName.value)
})

const clearFilter = () => {
  filterDatasetName.value = ''
  router.replace({ query: {} })
}

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

// 推荐
const recommending = ref(false)
const recommendResult = ref<Record<string, any> | null>(null)

// 详情
const detailVisible = ref(false)
const currentTask = ref<TrainingTask | null>(null)

// 日志查看
const logVisible = ref(false)
const logContent = ref('')
const logLoading = ref(false)
const logAutoScroll = ref(true)
const logViewerRef = ref<HTMLElement | null>(null)
let logEventSource: EventSource | null = null

// 选择对比
const selectedTasks = ref<TrainingTask[]>([])
const compareVisible = ref(false)
const compareTasks = ref<TrainingTask[]>([])

const handleSelectionChange = (val: TrainingTask[]) => {
  selectedTasks.value = val
}

const scrollLogToBottom = () => {
  if (!logAutoScroll.value || !logViewerRef.value) return
  nextTick(() => {
    logViewerRef.value!.scrollTop = logViewerRef.value!.scrollHeight
  })
}

const openLogViewer = async () => {
  if (!currentTask.value?.taskId) return
  logVisible.value = true
  await fetchLog(currentTask.value.taskId)
  if (currentTask.value?.status === 'RUNNING') {
    startLogStream(currentTask.value.taskId)
  }
}

const startLogStream = (taskId: string) => {
  closeLogStream()
  logEventSource = new EventSource(`/api/training/tasks/${taskId}/log/stream`)
  logEventSource.onmessage = (event) => {
    logContent.value += event.data + '\n'
    scrollLogToBottom()
  }
  logEventSource.addEventListener('done', () => {
    closeLogStream()
  })
  logEventSource.onerror = () => {
    closeLogStream()
  }
}

const closeLogStream = () => {
  if (logEventSource) {
    logEventSource.close()
    logEventSource = null
  }
}

const closeLogViewer = () => {
  closeLogStream()
  logVisible.value = false
}

const refreshLog = async () => {
  if (!currentTask.value?.taskId) return
  await fetchLog(currentTask.value.taskId)
}

const fetchLog = async (taskId: string) => {
  logLoading.value = true
  try {
    const res = await getTaskLog(taskId)
    logContent.value = (res as any).content || '暂无日志'
    scrollLogToBottom()
  } catch {
    logContent.value = '读取日志失败'
  } finally {
    logLoading.value = false
  }
}

const showCompareDialog = () => {
  compareTasks.value = [...selectedTasks.value]
  compareVisible.value = true
}

const getLastLoss = (task: TrainingTask) => {
  if (!task.lossHistory) return '-'
  const entries = task.lossHistory.split(',')
  const last = entries[entries.length - 1]
  return last ? last.split(':')[1] : '-'
}

const compareChartData = computed(() => {
  return compareTasks.value
    .filter(t => t.lossHistory)
    .map(t => ({
      name: t.taskName,
      data: t.lossHistory!.split(',').map(e => {
        const [step, loss] = e.split(':')
        return { step: Number(step), loss: Number(loss) }
      }).filter(d => !isNaN(d.step) && !isNaN(d.loss))
    }))
})

const COMPARE_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']

const compareChartOption = computed(() => {
  const allSteps = new Set<number>()
  compareChartData.value.forEach(s => s.data.forEach(d => allSteps.add(d.step)))
  const steps = Array.from(allSteps).sort((a, b) => a - b)

  return {
    tooltip: { trigger: 'axis' },
    legend: { data: compareChartData.value.map(s => s.name) },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: steps, name: 'Step' },
    yAxis: { type: 'value', name: 'Loss', min: 'dataMin' },
    series: compareChartData.value.map((s, i) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      symbol: 'none',
      data: steps.map(step => {
        const point = s.data.find(d => d.step === step)
        return point ? point.loss : null
      }),
      lineStyle: { width: 2, color: COMPARE_COLORS[i % COMPARE_COLORS.length] }
    }))
  }
})

// Loss 曲线
const lossChartData = computed(() => {
  if (!currentTask.value?.lossHistory) return []
  return currentTask.value.lossHistory.split(',').map(entry => {
    const [step, loss] = entry.split(':')
    return { step: Number(step), loss: Number(loss) }
  }).filter(d => !isNaN(d.step) && !isNaN(d.loss))
})

const lossChartOption = computed(() => ({
  tooltip: { trigger: 'axis', formatter: (p: any) => `Step ${p[0].axisValue}<br/>Loss: <b>${p[0].data}</b>` },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: lossChartData.value.map(d => d.step), name: 'Step' },
  yAxis: { type: 'value', name: 'Loss', min: 'dataMin' },
  series: [{
    type: 'line',
    data: lossChartData.value.map(d => d.loss),
    smooth: true,
    symbol: 'none',
    lineStyle: { width: 2, color: '#409eff' },
    areaStyle: { color: 'rgba(64,158,255,0.08)' },
    markLine: lossChartData.value.length > 10 ? {
      silent: true,
      data: [{ type: 'average', name: '平均' }],
      lineStyle: { color: '#e6a23c' }
    } : undefined
  }]
}))

// 统计
const runningCount = computed(() => tasks.value.filter(t => t.status === 'RUNNING' || t.status === 'PREPARING').length)
const completedCount = computed(() => tasks.value.filter(t => t.status === 'COMPLETED').length)
const failedCount = computed(() => tasks.value.filter(t => t.status === 'FAILED').length)

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getAllTasks()
    allTasks.value = res.tasks || []
  } catch { allTasks.value = [] }
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

const handleRecommend = async () => {
  if (!createForm.value.datasetId) {
    ElMessage.warning('请先选择数据集')
    return
  }
  recommending.value = true
  try {
    const res = await recommendParams(createForm.value.datasetId)
    recommendResult.value = res.data || {}
  } catch (e) {
    console.error(e)
    ElMessage.error('获取推荐参数失败')
  } finally {
    recommending.value = false
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

const handleRestart = async (taskId: string) => {
  await ElMessageBox.confirm('确定要重新训练此任务吗？', '提示', { type: 'warning' })
  try {
    await restartTask(taskId)
    ElMessage.success('任务已重新启动')
    await loadTasks()
  } catch (e) { if (e !== 'cancel') console.error(e) }
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
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  closeLogStream()
})
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
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-radius: 8px;
  transition: transform 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}
.stat-total {
  background: #f0f5ff;
}
.stat-total .stat-icon {
  background: #d6e4ff;
  color: #409eff;
}
.stat-total .stat-value {
  color: #409eff;
}
.stat-running {
  background: #fff7e6;
}
.stat-running .stat-icon {
  background: #ffe7ba;
  color: #e6a23c;
}
.stat-running .stat-value {
  color: #e6a23c;
}
.stat-completed {
  background: #f0f9eb;
}
.stat-completed .stat-icon {
  background: #d9f0c7;
  color: #67c23a;
}
.stat-completed .stat-value {
  color: #67c23a;
}
.stat-failed {
  background: #fef0f0;
}
.stat-failed .stat-icon {
  background: #fde2e2;
  color: #f56c6c;
}
.stat-failed .stat-value {
  color: #f56c6c;
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
.loss-chart-wrapper {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
  background: #fafafa;
}
.log-viewer {
  height: 60vh;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 16px;
}
.log-viewer pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
