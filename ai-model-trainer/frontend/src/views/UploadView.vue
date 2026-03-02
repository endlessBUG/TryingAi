<template>
  <div class="dataset-container">
    <!-- 数据集列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据集管理</span>
          <div>
            <el-button type="primary" @click="showAddDialog">
              <el-icon><plus /></el-icon>新增数据集
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="datasets" stripe v-loading="loading" empty-text="暂无数据集">
        <el-table-column prop="name" label="数据集名称" min-width="180" />
        <el-table-column prop="imageCount" label="图片数量" width="100" align="center" />
        <el-table-column label="总大小" width="120" align="center">
          <template #default="{ row }">
            {{ formatFileSize(row.totalSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="datasetPath" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button size="small" type="primary" @click="showPipelineDialog(row)">一键训练</el-button>
            <el-button size="small" type="success" @click="goTrainingHistory(row)">训练历史</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增数据集弹窗 -->
    <el-dialog v-model="addDialogVisible" title="新增数据集" width="600px" :close-on-click-modal="false">
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :action="''"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        accept=".zip,.tar.gz,.rar"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽压缩包到此处或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 ZIP、RAR 和 TAR.GZ 格式，最大 500MB</div>
        </template>
      </el-upload>

      <div v-if="uploading" class="upload-progress">
        <el-progress :percentage="uploadProgress" :status="uploadStatus" />
      </div>

      <template #footer>
        <el-button @click="closeAddDialog">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
          {{ uploading ? '上传中...' : '开始上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 数据集详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="currentDataset?.name" size="70%">
      <div v-if="currentDataset" class="detail-header">
        <el-tag type="success">{{ currentDataset.imageCount }} 张图片</el-tag>
        <div>
          <el-button size="small" @click="refreshDetail" :loading="detailLoading">刷新</el-button>
          <el-button size="small" @click="showGenerateDialog">生成提示词</el-button>
          <el-button size="small" type="success" @click="showOptimizeDialog">优化提示词</el-button>
          <el-button size="small" type="warning" @click="showQualityDialog">质量评估</el-button>
          <el-button size="small" @click="showPreprocessDialog">预处理</el-button>
          <el-button size="small" type="primary" @click="handleSavePrompts">保存提示词</el-button>
        </div>
      </div>

      <div v-loading="detailLoading">
        <div ref="gridScrollRef" class="virtual-grid-scroll">
          <div :style="{ height: `${totalSize}px`, position: 'relative', width: '100%' }">
            <div
              v-for="vRow in virtualRows"
              :key="vRow.key"
              :style="{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${vRow.start}px)`,
              }"
            >
              <div class="image-row" :style="{ gridTemplateColumns: `repeat(${columnCount}, 1fr)` }">
                <div
                  v-for="img in getRowImages(vRow.index)"
                  :key="img.imageName"
                  class="image-card"
                >
                  <el-image
                    :src="getImageUrl(img.imageName)"
                    fit="cover"
                    lazy
                    :preview-src-list="previewList"
                    :initial-index="getGlobalIndex(vRow.index, img)"
                    class="card-img"
                  />
                  <div class="card-body">
                    <el-tooltip :content="img.imageName" placement="top" :show-after="300">
                      <div class="card-name">{{ img.imageName }}</div>
                    </el-tooltip>
                    <div class="card-meta">
                      {{ img.width }}x{{ img.height }}
                      <el-tag
                        v-if="img.qualityScore != null"
                        :type="img.qualityScore >= 7 ? 'success' : img.qualityScore >= 4 ? 'warning' : 'danger'"
                        size="small"
                        style="margin-left: 4px"
                      >{{ img.qualityScore }}/10</el-tag>
                    </div>
                    <el-tooltip
                      v-if="img.prompt"
                      :content="img.prompt"
                      placement="top"
                      :show-after="300"
                      effect="light"
                      max-width="360"
                    >
                      <div class="card-prompt">{{ img.prompt }}</div>
                    </el-tooltip>
                    <div v-else class="card-prompt card-prompt--empty">暂无提示词</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-if="!detailLoading && currentImages.length === 0" description="暂无图片" />
      </div>
    </el-drawer>

    <!-- 选择生成器对话框 -->
    <el-dialog v-model="generateDialogVisible" title="选择提示词生成器" width="480px">
      <el-alert
        v-if="generatorList.length === 0"
        title="暂无可用的生成器，请先到「提示词生成器」页面添加配置"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="80px">
        <el-form-item label="生成器">
          <el-select v-model="selectedGeneratorId" placeholder="请选择生成器" style="width: 100%">
            <el-option
              v-for="g in enabledGenerators"
              :key="g.id"
              :label="g.name"
              :value="g.id"
            >
              <span>{{ g.name }}</span>
              <el-tag size="small" style="margin-left: 8px" :type="g.type === 'LOCAL_MODEL' ? 'success' : 'primary'">
                {{ g.type === 'LOCAL_MODEL' ? '本地' : '远程' }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!selectedGeneratorId"
          @click="handleRegeneratePrompts"
        >
          开始生成
        </el-button>
      </template>
    </el-dialog>
    <!-- 图片预处理对话框 -->
    <el-dialog v-model="preprocessDialogVisible" title="图片预处理" width="480px">
      <el-alert
        title="自动缩放超大图片到训练分辨率，去除 EXIF 信息，检测重复图片。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="目标分辨率">
          <el-select v-model="preprocessResolution" style="width: 100%">
            <el-option :value="512" label="512 px" />
            <el-option :value="768" label="768 px" />
            <el-option :value="1024" label="1024 px" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="preprocessResult"
        :title="`处理完成：共 ${preprocessResult.total} 张，缩放 ${preprocessResult.resized} 张${preprocessResult.duplicates.length > 0 ? '，发现 ' + preprocessResult.duplicates.length + ' 组重复' : ''}`"
        :type="preprocessResult.duplicates.length > 0 ? 'warning' : 'success'"
        :closable="false"
        style="margin-top: 12px"
      >
        <div v-if="preprocessResult.duplicates.length > 0" style="margin-top: 8px; font-size: 12px; color: #909399">
          <div v-for="d in preprocessResult.duplicates" :key="d">{{ d }}</div>
        </div>
      </el-alert>
      <template #footer>
        <el-button @click="preprocessDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="preprocessing" @click="handlePreprocess">开始预处理</el-button>
      </template>
    </el-dialog>

    <!-- 一键训练对话框 -->
    <el-dialog v-model="pipelineDialogVisible" title="一键训练" width="900px" top="5vh">
      <el-alert
        title="选择训练器后可预览并编辑 YAML 配置，确认后直接创建任务并启动训练。"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="数据集">
          <el-input :model-value="pipelineDataset?.name" disabled />
        </el-form-item>
        <el-form-item label="训练器">
          <el-select v-model="pipelineTrainerId" placeholder="请选择" style="width: 100%" @change="onPipelineTrainerChange">
            <el-option
              v-for="t in pipelineTrainers"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="基础模型">
          <el-select
            v-model="pipelineBaseModel"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入 HuggingFace 模型 ID"
            style="width: 100%"
            @change="onBaseModelChange"
          >
            <el-option
              v-for="m in baseModelPresets"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            选择后自动更新 YAML 中的 name_or_path，未缓存的模型会在训练前自动下载
          </div>
        </el-form-item>
        <el-form-item label="触发词">
          <el-input v-model="pipelineTriggerWord" placeholder="可选，如 ohwx、sks" clearable />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            填写后将自动注入 YAML，采样提示词中可用 [trigger] 占位符引用
          </div>
        </el-form-item>
        <el-form-item v-if="pipelineYamlConfig" label="YAML 配置">
          <YamlEditor v-model="pipelineYamlConfig" height="380px" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            占位符 <code v-pre>&#123;&#123;DATASET_PATH&#125;&#125;</code> 已自动替换为数据集路径，可手动调整其他参数
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pipelineDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="pipelineLoading"
          :disabled="!pipelineTrainerId"
          @click="handleAutoPipeline"
        >
          开始一键训练
        </el-button>
      </template>
    </el-dialog>

    <!-- 提示词优化对话框 -->
    <el-dialog v-model="optimizeDialogVisible" title="提示词优化" width="480px">
      <el-alert
        title="自动优化提示词：检测 trigger word、去重、格式统一。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="Trigger Word">
          <el-input v-model="optimizeTriggerWord" placeholder="可选，如 sks, ohwx" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            填写后会自动在未包含该词的提示词前添加
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="optimizeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="optimizing" @click="handleOptimizePrompts">开始优化</el-button>
      </template>
    </el-dialog>

    <!-- 质量评估对话框 -->
    <el-dialog v-model="qualityDialogVisible" title="图片质量评估" width="480px">
      <el-alert
        title="使用视觉模型对数据集中的图片进行质量评分（1-10分），帮助筛选不适合训练的低质量图片。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="80px">
        <el-form-item label="生成器">
          <el-select v-model="qualityGeneratorId" placeholder="请选择生成器" style="width: 100%">
            <el-option
              v-for="g in enabledGenerators"
              :key="g.id"
              :label="g.name"
              :value="g.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qualityDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="evaluating"
          :disabled="!qualityGeneratorId"
          @click="handleEvaluateQuality"
        >
          开始评估
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import { useVirtualizer } from '@tanstack/vue-virtual'
import {
  getDatasets, deleteDataset, uploadImageArchive,
  getDatasetDetail, updatePrompts, regeneratePrompts, evaluateQuality, optimizePrompts, preprocessImages
} from '@/api/file'
import { getPromptGenerators } from '@/api/promptGenerator'
import { autoPipeline } from '@/api/training'
import { getTrainers } from '@/api/trainer'
import YamlEditor from '@/components/YamlEditor.vue'
import type { Dataset, ImagePrompt, PromptGenerator, Trainer } from '@/types'
import { useSystemConfigStore } from '@/stores/systemConfig'

const router = useRouter()

// 列表相关
const datasets = ref<Dataset[]>([])
const loading = ref(false)

// 上传相关
const addDialogVisible = ref(false)
const uploadRef = ref()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref<'' | 'success' | 'exception'>('')

// 详情相关
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDataset = ref<Dataset | null>(null)
const currentImages = ref<ImagePrompt[]>([])

// 虚拟滚动相关
const CARD_MIN_WIDTH = 140
const CARD_GAP = 12
const ROW_HEIGHT = 240
const gridScrollRef = ref<HTMLElement | null>(null)
const columnCount = ref(4)
let resizeObserver: ResizeObserver | null = null

const rowCount = computed(() => Math.ceil(currentImages.value.length / columnCount.value))

const rowVirtualizer = useVirtualizer(computed(() => ({
  count: rowCount.value,
  getScrollElement: () => gridScrollRef.value,
  estimateSize: () => ROW_HEIGHT,
  overscan: 3,
})))

const virtualRows = computed(() => rowVirtualizer.value.getVirtualItems())
const totalSize = computed(() => rowVirtualizer.value.getTotalSize())

const getRowImages = (rowIndex: number) => {
  const start = rowIndex * columnCount.value
  return currentImages.value.slice(start, start + columnCount.value)
}

const getGlobalIndex = (rowIndex: number, img: ImagePrompt) => {
  return rowIndex * columnCount.value + getRowImages(rowIndex).indexOf(img)
}

const updateColumnCount = () => {
  if (!gridScrollRef.value) return
  const width = gridScrollRef.value.clientWidth
  columnCount.value = Math.max(1, Math.floor((width + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP)))
}

watch(detailVisible, (visible) => {
  if (visible) {
    setTimeout(() => {
      updateColumnCount()
      if (gridScrollRef.value && !resizeObserver) {
        resizeObserver = new ResizeObserver(updateColumnCount)
        resizeObserver.observe(gridScrollRef.value)
      }
    }, 100)
  } else {
    resizeObserver?.disconnect()
    resizeObserver = null
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

// 生成器相关
const generateDialogVisible = ref(false)
const generatorList = ref<PromptGenerator[]>([])
const selectedGeneratorId = ref('')
const generating = ref(false)

// 一键训练相关
const pipelineDialogVisible = ref(false)
const pipelineDataset = ref<Dataset | null>(null)
const pipelineTrainerId = ref('')
const pipelineTrainers = ref<Trainer[]>([])
const pipelineLoading = ref(false)
const pipelineYamlConfig = ref('')
const pipelineTriggerWord = ref('')
const pipelineBaseModel = ref('')

const sysConfig = useSystemConfigStore()
const modelDir = computed(() => sysConfig.get('model.dir', '/root/ai/trainer/models'))

const baseModelRaw = [
  { label: 'Wan 2.2 14B (T2V)', id: 'ai-toolkit/Wan2.2-T2V-A14B-Diffusers-bf16', name: 'Wan2.2-T2V-A14B-Diffusers-bf16' },
  { label: 'Wan 2.2 14B (I2V)', id: 'ai-toolkit/Wan2.2-I2V-14B-480P-Diffusers', name: 'Wan2.2-I2V-14B-480P-Diffusers' },
  { label: 'FLUX.1 Dev', id: 'black-forest-labs/FLUX.1-dev', name: 'FLUX.1-dev' },
  { label: 'FLUX.1 Schnell', id: 'black-forest-labs/FLUX.1-schnell', name: 'FLUX.1-schnell' },
]

const baseModelPresets = computed(() =>
  baseModelRaw.map(m => ({ label: m.label, value: `${modelDir.value}/${m.name}` }))
)

// 图片预处理相关
const preprocessDialogVisible = ref(false)
const preprocessResolution = ref(512)
const preprocessing = ref(false)
const preprocessResult = ref<{ total: number; resized: number; duplicates: string[] } | null>(null)

// 提示词优化相关
const optimizeDialogVisible = ref(false)
const optimizeTriggerWord = ref('')
const optimizing = ref(false)

// 质量评估相关
const qualityDialogVisible = ref(false)
const qualityGeneratorId = ref('')
const evaluating = ref(false)

const enabledGenerators = computed(() =>
  generatorList.value.filter(g => g.enabled !== false)
)

const loadDatasets = async () => {
  loading.value = true
  try {
    const res = await getDatasets()
    datasets.value = res.data ?? []
  } catch {
    datasets.value = []
  } finally {
    loading.value = false
  }
}

const showAddDialog = () => {
  addDialogVisible.value = true
}

const closeAddDialog = () => {
  addDialogVisible.value = false
  resetUpload()
}

const resetUpload = () => {
  uploadRef.value?.clearFiles()
  selectedFile.value = null
  uploadProgress.value = 0
  uploadStatus.value = ''
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
}

const handleUpload = async () => {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 0
  uploadStatus.value = ''

  const timer = setInterval(() => {
    if (uploadProgress.value < 90) uploadProgress.value += 10
  }, 500)

  try {
    const response = await uploadImageArchive(selectedFile.value)
    clearInterval(timer)
    uploadProgress.value = 100
    uploadStatus.value = 'success'
    ElMessage.success(`数据集上传成功，共 ${response.dataset?.imageCount ?? 0} 张图片`)
    closeAddDialog()
    await loadDatasets()
  } catch {
    clearInterval(timer)
    uploadStatus.value = 'exception'
  } finally {
    uploading.value = false
  }
}

const handleView = async (row: Dataset) => {
  currentDataset.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getDatasetDetail(row.id)
    currentImages.value = res.data?.images ?? []
  } catch {
    currentImages.value = []
  } finally {
    detailLoading.value = false
  }
}

const goTrainingHistory = (row: Dataset) => {
  router.push({ path: '/tasks', query: { datasetName: row.name } })
}

const handleDelete = async (row: Dataset) => {
  await ElMessageBox.confirm(`确定要删除数据集「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  try {
    await deleteDataset(row.id)
    ElMessage.success('删除成功')
    await loadDatasets()
  } catch {
    // error handled by request interceptor
  }
}

const handleSavePrompts = async () => {
  try {
    await updatePrompts(currentImages.value)
    ElMessage.success('提示词保存成功')
  } catch {
    // error handled by request interceptor
  }
}

const showGenerateDialog = async () => {
  await loadGenerators()
  selectedGeneratorId.value = ''
  generateDialogVisible.value = true
}

const loadGenerators = async () => {
  try {
    const res = await getPromptGenerators()
    generatorList.value = res.data || []
  } catch {
    generatorList.value = []
  }
}

const handleRegeneratePrompts = async () => {
  if (!selectedGeneratorId.value || !currentDataset.value) return
  generating.value = true
  try {
    await regeneratePrompts(currentDataset.value.id, selectedGeneratorId.value)
    ElMessage.success('提示词生成已开始，请稍后点击刷新查看进度')
    generateDialogVisible.value = false
  } catch (e) {
    console.error(e)
  } finally {
    generating.value = false
  }
}

const refreshDetail = async () => {
  if (!currentDataset.value) return
  detailLoading.value = true
  try {
    const res = await getDatasetDetail(currentDataset.value.id)
    currentImages.value = res.data?.images ?? []
  } catch {
    // error handled by request interceptor
  } finally {
    detailLoading.value = false
  }
}

const showPreprocessDialog = () => {
  preprocessResult.value = null
  preprocessResolution.value = 512
  preprocessDialogVisible.value = true
}

const handlePreprocess = async () => {
  if (!currentDataset.value) return
  preprocessing.value = true
  try {
    const res = await preprocessImages(currentDataset.value.id, preprocessResolution.value)
    preprocessResult.value = res.data || null
    // 刷新图片列表
    const detail = await getDatasetDetail(currentDataset.value.id)
    currentImages.value = detail.data?.images ?? []
  } catch (e) {
    console.error(e)
  } finally {
    preprocessing.value = false
  }
}

const showPipelineDialog = async (row: Dataset) => {
  pipelineDataset.value = row
  pipelineTrainerId.value = ''
  pipelineYamlConfig.value = ''
  pipelineTriggerWord.value = row.name || ''
  pipelineBaseModel.value = ''
  if (!sysConfig.loaded.value) await sysConfig.load()
  await loadPipelineTrainers()
  pipelineDialogVisible.value = true
}

const loadPipelineTrainers = async () => {
  try {
    const res = await getTrainers()
    pipelineTrainers.value = res.data ?? []
  } catch {
    pipelineTrainers.value = []
  }
}

const onPipelineTrainerChange = (trainerId: string) => {
  const trainer = pipelineTrainers.value.find(t => t.id === trainerId)
  if (!trainer?.defaultYamlConfig) {
    pipelineYamlConfig.value = ''
    pipelineBaseModel.value = ''
    return
  }
  const dsPath = pipelineDataset.value?.datasetPath || ''
  pipelineYamlConfig.value = trainer.defaultYamlConfig.replace(/\{\{DATASET_PATH}}/g, dsPath)
  const extracted = extractNameOrPath(pipelineYamlConfig.value)
  const absolutePath = modelIdToAbsolutePath(extracted)
  pipelineBaseModel.value = absolutePath || extracted
  if (absolutePath && absolutePath !== extracted) {
    pipelineYamlConfig.value = pipelineYamlConfig.value.replace(
      /^(\s*name_or_path:\s*)"?[^"\n]*"?/m,
      `$1"${absolutePath}"`
    )
  }
  applyTriggerWord()
}

const extractNameOrPath = (yaml: string): string => {
  const match = yaml.match(/name_or_path:\s*"?([^"\n]+)"?/)
  return match ? match[1].trim() : ''
}

const modelIdToAbsolutePath = (modelId: string): string => {
  if (!modelId) return ''
  const preset = baseModelRaw.find(m => m.id === modelId || modelId === m.name || modelId.endsWith('/' + m.name))
  return preset ? `${modelDir.value}/${preset.name}` : modelId
}

const onBaseModelChange = (modelId: string) => {
  if (!pipelineYamlConfig.value || !modelId) return
  pipelineYamlConfig.value = pipelineYamlConfig.value.replace(
    /^(\s*name_or_path:\s*)"?[^"\n]*"?/m,
    `$1"${modelId}"`
  )
}

const applyTriggerWord = () => {
  if (!pipelineYamlConfig.value) return
  let yaml = pipelineYamlConfig.value.replace(/^[ \t]*trigger_word:.*\n?/m, '')
  const word = pipelineTriggerWord.value.trim()
  if (word) {
    yaml = yaml.replace(
      /^([ \t]*)(device:.*\n)/m,
      `$1$2$1trigger_word: "${word}"\n`
    )
  }
  pipelineYamlConfig.value = yaml
}

watch(pipelineTriggerWord, applyTriggerWord)

const handleAutoPipeline = async () => {
  if (!pipelineDataset.value || !pipelineTrainerId.value) return
  pipelineLoading.value = true
  try {
    await autoPipeline(
      pipelineDataset.value.id,
      pipelineTrainerId.value,
      pipelineYamlConfig.value || undefined
    )
    ElMessage.success('一键训练流水线已启动，请在任务列表查看进度')
    pipelineDialogVisible.value = false
  } catch (e) {
    console.error(e)
  } finally {
    pipelineLoading.value = false
  }
}

const showOptimizeDialog = () => {
  optimizeTriggerWord.value = ''
  optimizeDialogVisible.value = true
}

const handleOptimizePrompts = async () => {
  if (!currentDataset.value) return
  optimizing.value = true
  try {
    const res = await optimizePrompts(currentDataset.value.id, optimizeTriggerWord.value || undefined)
    if (res.data?.images) {
      currentImages.value = res.data.images
    }
    ElMessage.success('提示词优化完成')
    optimizeDialogVisible.value = false
  } catch (e) {
    console.error(e)
  } finally {
    optimizing.value = false
  }
}

const showQualityDialog = async () => {
  await loadGenerators()
  qualityGeneratorId.value = ''
  qualityDialogVisible.value = true
}

const handleEvaluateQuality = async () => {
  if (!qualityGeneratorId.value || !currentDataset.value) return
  evaluating.value = true
  try {
    const res = await evaluateQuality(currentDataset.value.id, qualityGeneratorId.value)
    if (res.data?.images) {
      currentImages.value = res.data.images
    }
    ElMessage.success('质量评估完成')
    qualityDialogVisible.value = false
  } catch (e) {
    console.error(e)
  } finally {
    evaluating.value = false
  }
}

const getImageUrl = (imageName: string): string => {
  if (!currentDataset.value) return ''
  return `/api/files/datasets/${currentDataset.value.id}/images/${encodeURIComponent(imageName)}`
}

const previewList = computed(() =>
  currentImages.value.map(img => getImageUrl(img.imageName))
)

const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatTime = (time?: string): string => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(async () => {
  if (!sysConfig.loaded.value) await sysConfig.load()
  loadDatasets()
})
</script>

<style scoped>
.dataset-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.upload-area {
  width: 100%;
}
.upload-progress {
  margin-top: 16px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.virtual-grid-scroll {
  height: calc(100vh - 140px);
  overflow-y: auto;
}
.image-row {
  display: grid;
  gap: 12px;
}
.image-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow 0.2s;
  cursor: default;
}
.image-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
.card-img {
  width: 100%;
  aspect-ratio: 1;
  display: block;
  background: #f5f5f5;
}
.card-body {
  padding: 8px 10px 10px;
}
.card-name {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.card-prompt {
  margin-top: 4px;
  font-size: 11px;
  color: #606266;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
}
.card-prompt--empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
