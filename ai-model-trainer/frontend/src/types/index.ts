/**
 * 类型定义
 */

// 任务状态
export enum TaskStatus {
  PENDING = 'PENDING',
  PREPARING = 'PREPARING',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

// 图片提示词
export interface ImagePrompt {
  imageName: string
  imagePath: string
  prompt: string
  width?: number
  height?: number
  fileSize?: number
}

// 训练配置
export interface TrainingConfig {
  modelType?: string
  baseModel?: string
  steps?: number
  batchSize?: number
  learningRate?: number
  resolution?: number
  loraRank?: number
  loraAlpha?: number
  optimizer?: string
  lrScheduler?: string
  saveEvery?: number
  sampleEvery?: number
  samplePrompt?: string
  mixedPrecision?: string
  gradientAccumulationSteps?: number
  use8bitAdam?: boolean
  useXformers?: boolean
  extraArgs?: string
}

// 创建任务请求参数
export interface CreateTaskRequest {
  taskName: string
  datasetId: string
  trainerId: string
  yamlConfig: string
}

// 训练任务
export interface TrainingTask {
  taskId?: string
  taskName: string
  status?: TaskStatus
  datasetId?: string
  datasetName?: string
  datasetPath?: string
  trainerId?: string
  trainerName?: string
  trainerPath?: string
  imageCount?: number
  configPath?: string
  outputPath?: string
  yamlConfig?: string
  trainingConfig?: TrainingConfig
  progress?: number
  currentStep?: number
  totalSteps?: number
  processId?: number
  errorMessage?: string
  logPath?: string
  createdAt?: string
  startedAt?: string
  completedAt?: string
  prompts?: ImagePrompt[]
}

// API响应
export interface ApiResponse<T = any> {
  success: boolean
  message?: string
  data?: T
  timestamp?: number
}

// 数据集
export interface Dataset {
  id: string
  name: string
  datasetPath: string
  imageCount: number
  totalSize?: number
  createdAt?: string
  images?: ImagePrompt[]
}

// 文件上传响应
export interface UploadResponse {
  success: boolean
  message: string
  dataset: Dataset
}

// 任务响应
export interface TaskResponse {
  success: boolean
  message?: string
  task: TrainingTask
}

// 任务列表响应
export interface TaskListResponse {
  success: boolean
  tasks: TrainingTask[]
}

// 训练器
export interface Trainer {
  id?: string
  name: string
  path?: string
  gitUrl?: string
  pythonVersion: string
  defaultYamlConfig?: string
  createdAt?: string
}

// 配置模板
export interface ConfigTemplate {
  job: {
    name: string
    device: string
    trigger_word?: string
  }
  model: {
    name_or_path: string
    is_flux?: boolean
  }
  train: {
    dtype: string
    train_steps: number
    learning_rate: number
    batch_size: number
    optimizer: string
    lr_scheduler: string
    gradient_accumulation_steps?: number
  }
  datasets: Array<{
    folder_path: string
    caption_ext: string
    resolution: number
  }>
  network?: {
    type: string
    rank: number
    alpha: number
  }
  save: {
    save_every: number
    max_step_saves_to_keep: number
  }
  sample?: {
    sampler: string
    sample_every: number
    width: number
    height: number
    prompts: string[]
  }
}
