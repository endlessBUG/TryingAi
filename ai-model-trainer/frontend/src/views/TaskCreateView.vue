<template>
  <div class="task-create-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>创建训练任务</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
      >
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="任务名称" prop="taskName">
          <el-input
            v-model="form.taskName"
            placeholder="请输入任务名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="数据集路径" prop="datasetPath">
          <el-input
            v-model="form.datasetPath"
            placeholder="数据集路径（从上传页面自动获取）"
            readonly
          />
        </el-form-item>

        <el-form-item label="图片数量">
          <el-input-number
            v-model="form.imageCount"
            :min="1"
            disabled
          />
        </el-form-item>

        <el-divider content-position="left">训练配置</el-divider>

        <el-form-item label="模型类型" prop="trainingConfig.modelType">
          <el-select v-model="form.trainingConfig.modelType" placeholder="请选择">
            <el-option label="LoRA" value="lora" />
            <el-option label="DreamBooth" value="dreambooth" />
          </el-select>
        </el-form-item>

        <el-form-item label="基础模型" prop="trainingConfig.baseModel">
          <el-input
            v-model="form.trainingConfig.baseModel"
            placeholder="例如: runwayml/stable-diffusion-v1-5"
          />
        </el-form-item>

        <el-form-item label="训练步数" prop="trainingConfig.steps">
          <el-input-number
            v-model="form.trainingConfig.steps"
            :min="100"
            :max="10000"
            :step="100"
          />
        </el-form-item>

        <el-form-item label="批次大小" prop="trainingConfig.batchSize">
          <el-input-number
            v-model="form.trainingConfig.batchSize"
            :min="1"
            :max="16"
          />
        </el-form-item>

        <el-form-item label="学习率" prop="trainingConfig.learningRate">
          <el-input-number
            v-model="form.trainingConfig.learningRate"
            :min="0.00001"
            :max="0.001"
            :step="0.00001"
            :precision="5"
          />
        </el-form-item>

        <el-form-item label="分辨率" prop="trainingConfig.resolution">
          <el-select v-model="form.trainingConfig.resolution">
            <el-option label="512" :value="512" />
            <el-option label="768" :value="768" />
            <el-option label="1024" :value="1024" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">LoRA配置</el-divider>

        <el-form-item label="LoRA Rank">
          <el-input-number
            v-model="form.trainingConfig.loraRank"
            :min="1"
            :max="128"
          />
        </el-form-item>

        <el-form-item label="LoRA Alpha">
          <el-input-number
            v-model="form.trainingConfig.loraAlpha"
            :min="1"
            :max="128"
            :step="0.1"
          />
        </el-form-item>

        <el-divider content-position="left">高级选项</el-divider>

        <el-form-item label="优化器">
          <el-select v-model="form.trainingConfig.optimizer">
            <el-option label="AdamW 8bit" value="adamw8bit" />
            <el-option label="AdamW" value="adamw" />
            <el-option label="SGD" value="sgd" />
          </el-select>
        </el-form-item>

        <el-form-item label="学习率调度器">
          <el-select v-model="form.trainingConfig.lrScheduler">
            <el-option label="Constant" value="constant" />
            <el-option label="Linear" value="linear" />
            <el-option label="Cosine" value="cosine" />
          </el-select>
        </el-form-item>

        <el-form-item label="保存频率">
          <el-input-number
            v-model="form.trainingConfig.saveEvery"
            :min="100"
            :max="1000"
            :step="100"
          />
          <span style="margin-left: 10px; color: #909399;">每N步保存一次</span>
        </el-form-item>

        <el-form-item label="采样频率">
          <el-input-number
            v-model="form.trainingConfig.sampleEvery"
            :min="0"
            :max="500"
            :step="50"
          />
          <span style="margin-left: 10px; color: #909399;">0表示不采样</span>
        </el-form-item>

        <el-form-item
          v-if="form.trainingConfig.sampleEvery && form.trainingConfig.sampleEvery > 0"
          label="采样提示词"
        >
          <el-input
            v-model="form.trainingConfig.samplePrompt"
            placeholder="用于生成样本的提示词"
          />
        </el-form-item>

        <el-form-item label="混合精度">
          <el-select v-model="form.trainingConfig.mixedPrecision">
            <el-option label="FP16" value="fp16" />
            <el-option label="BF16" value="bf16" />
            <el-option label="FP32" value="fp32" />
          </el-select>
        </el-form-item>

        <el-form-item label="梯度累积步数">
          <el-input-number
            v-model="form.trainingConfig.gradientAccumulationSteps"
            :min="1"
            :max="16"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            创建任务
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTask } from '@/api/training'
import type { TrainingTask } from '@/types'
import { useTaskStore } from '@/stores/task'

const router = useRouter()
const taskStore = useTaskStore()
const formRef = ref()
const submitting = ref(false)

const form = reactive<TrainingTask>({
  taskName: '',
  datasetPath: '',
  imageCount: 0,
  trainingConfig: {
    modelType: 'lora',
    baseModel: 'runwayml/stable-diffusion-v1-5',
    steps: 1000,
    batchSize: 1,
    learningRate: 0.0001,
    resolution: 512,
    loraRank: 4,
    loraAlpha: 4.0,
    optimizer: 'adamw8bit',
    lrScheduler: 'constant',
    saveEvery: 500,
    sampleEvery: 100,
    samplePrompt: 'a photo',
    mixedPrecision: 'fp16',
    gradientAccumulationSteps: 1,
    use8bitAdam: true,
    useXformers: false
  }
})

const rules = {
  taskName: [
    { required: true, message: '请输入任务名称', trigger: 'blur' }
  ],
  datasetPath: [
    { required: true, message: '请先上传数据集', trigger: 'blur' }
  ],
  'trainingConfig.modelType': [
    { required: true, message: '请选择模型类型', trigger: 'change' }
  ],
  'trainingConfig.baseModel': [
    { required: true, message: '请输入基础模型', trigger: 'blur' }
  ],
  'trainingConfig.steps': [
    { required: true, message: '请输入训练步数', trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    
    submitting.value = true
    const response = await createTask(form)
    
    ElMessage.success('任务创建成功')
    
    // 清空store
    taskStore.clearDatasetInfo()
    
    // 跳转到任务列表
    router.push('/tasks')
  } catch (error: any) {
    if (error !== false) {
      console.error('Create task failed:', error)
    }
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  // 从store加载数据集信息
  const datasetInfo = taskStore.getDatasetInfo()
  if (datasetInfo) {
    form.datasetPath = datasetInfo.datasetPath
    form.imageCount = datasetInfo.imageCount
  } else {
    ElMessage.warning('请先上传数据集')
  }
})
</script>

<style scoped>
.task-create-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
