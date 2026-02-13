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
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button size="small" type="success" @click="handleCreateTask(row)">训练</el-button>
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
        accept=".zip,.tar.gz"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽压缩包到此处或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 ZIP 和 TAR.GZ 格式，最大 500MB</div>
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
          <el-button size="small" @click="showGenerateDialog">生成提示词</el-button>
          <el-button size="small" type="primary" @click="handleSavePrompts">保存提示词</el-button>
        </div>
      </div>

      <div v-loading="detailLoading" class="image-grid">
        <div v-for="(img, idx) in currentImages" :key="idx" class="image-card">
          <el-image
            :src="getImageUrl(img.imageName)"
            fit="cover"
            :preview-src-list="previewList"
            :initial-index="idx"
            class="card-img"
          />
          <div class="card-body">
            <el-tooltip :content="img.imageName" placement="top" :show-after="300">
              <div class="card-name">{{ img.imageName }}</div>
            </el-tooltip>
            <div class="card-meta">{{ img.width }}x{{ img.height }}</div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import {
  getDatasets, deleteDataset, uploadImageArchive,
  getDatasetDetail, updatePrompts, regeneratePrompts
} from '@/api/file'
import { getPromptGenerators } from '@/api/promptGenerator'
import type { Dataset, ImagePrompt, PromptGenerator } from '@/types'
import { useTaskStore } from '@/stores/task'

const router = useRouter()
const taskStore = useTaskStore()

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

// 生成器相关
const generateDialogVisible = ref(false)
const generatorList = ref<PromptGenerator[]>([])
const selectedGeneratorId = ref('')
const generating = ref(false)

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

const handleCreateTask = (row: Dataset) => {
  taskStore.setDatasetInfo({
    datasetPath: row.datasetPath,
    imageCount: row.imageCount,
    images: row.images ?? []
  })
  router.push('/tasks/create')
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
  if (!selectedGeneratorId.value) return
  generating.value = true
  try {
    const res = await regeneratePrompts(currentImages.value, selectedGeneratorId.value)
    if (res.data?.images) {
      currentImages.value = res.data.images
      ElMessage.success('提示词生成成功')
    }
    generateDialogVisible.value = false
  } catch (e) {
    console.error(e)
  } finally {
    generating.value = false
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

onMounted(loadDatasets)
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
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
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
