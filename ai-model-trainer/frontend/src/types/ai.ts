/**
 * AI服务相关类型定义
 */

export type AIServiceType =
  | 'text'
  | 'image'
  | 'image_to_image'
  | 'video'
  | 'video_frame'
  | 'sound_to_video'
  | 'camera_control'
  | 'text_to_speech'

/**
 * AI配置
 */
export interface AIConfig {
  id: number
  serviceType: AIServiceType
  provider?: string
  name: string
  baseUrl: string
  apiKey: string
  model: string[]
  endpoint?: string
  priority: number
  isDefault: boolean
  isActive: boolean
  settings?: string
  createdAt: string
  updatedAt: string
}

/**
 * 创建AI配置请求
 */
export interface CreateAIConfigRequest {
  serviceType: AIServiceType
  provider?: string
  name: string
  baseUrl: string
  apiKey: string
  model: string[]
  endpoint?: string
  priority?: number
  isDefault?: boolean
  settings?: string
}

/**
 * 更新AI配置请求
 */
export interface UpdateAIConfigRequest {
  name?: string
  provider?: string
  baseUrl?: string
  apiKey?: string
  model?: string[]
  endpoint?: string
  priority?: number
  isDefault?: boolean
  isActive?: boolean
  settings?: string
}

/**
 * 测试连接请求
 */
export interface TestConnectionRequest {
  baseUrl: string
  apiKey: string
  model: string[]
  provider?: string
  serviceType?: AIServiceType
  endpoint?: string
}

/**
 * 测试生成请求
 */
export interface TestGenerateRequest {
  prompt: string
  imageUrl?: string
  audioUrl?: string
  firstFrameUrl?: string
  lastFrameUrl?: string
  size?: string
  width?: number
  height?: number
  negativePrompt?: string
  voice?: string
  steps?: number
  seed?: number
  duration?: number
  frames?: number
  ttsMode?: 'speech' | 'clone'
  referenceAudioUrl?: string
  referenceText?: string
  cameraPose?: string  // 相机镜头动作
}

/**
 * 测试生成结果
 */
export interface TestGenerateResult {
  text?: string
  imageUrl?: string
  videoUrl?: string
  audioUrl?: string
  taskId?: string
  status?: string
  // 视频流下载所需字段
  videoFilename?: string
  videoSubfolder?: string
  videoFileType?: string
  configId?: number
}

/**
 * 服务类型显示名称
 */
export const SERVICE_TYPE_LABELS: Record<AIServiceType, string> = {
  text: '文本生成',
  image: '图片生成',
  image_to_image: '图生图',
  video: '视频生成',
  video_frame: '首尾帧视频',
  sound_to_video: '语音图片转视频',
  camera_control: '镜头控制',
  text_to_speech: '文本转语音'
}

/**
 * 厂商配置
 */
export interface ProviderConfig {
  id: string
  name: string
  models: string[]
}

/**
 * 各服务类型的厂商配置
 */
export const PROVIDER_CONFIGS: Record<AIServiceType, ProviderConfig[]> = {
  text: [
    { id: 'chatfire', name: 'ChatFire', models: ['gemini-3-flash-preview', 'claude-sonnet-4-5-20250929'] },
    { id: 'openai', name: 'OpenAI', models: ['gpt-4', 'gpt-4-turbo', 'gpt-3.5-turbo'] },
    { id: 'gemini', name: 'Google Gemini', models: ['gemini-2.5-pro', 'gemini-3-flash-preview'] }
  ],
  image: [
    { id: 'comfyui', name: 'ComfyUI', models: ['stable-diffusion', 'sdxl', 'flux'] },
    { id: 'chatfire', name: 'ChatFire', models: ['doubao-seedream-4-5-251128'] },
    { id: 'openai', name: 'OpenAI', models: ['dall-e-3', 'dall-e-2'] }
  ],
  image_to_image: [
    { id: 'comfyui', name: 'ComfyUI', models: ['flux2_dev', 'flux-dev', 'sdxl'] }
  ],
  video: [
    { id: 'comfyui', name: 'ComfyUI', models: ['wan2.2-t2v'] },
    { id: 'chatfire', name: 'ChatFire', models: ['doubao-seedance-1-5-pro-251215'] },
    { id: 'openai', name: 'OpenAI', models: ['sora-2', 'sora-2-pro'] }
  ],
  video_frame: [
    { id: 'comfyui', name: 'ComfyUI', models: ['wan2.2-flf2v'] }
  ],
  sound_to_video: [
    { id: 'comfyui', name: 'ComfyUI', models: ['wan2.2-s2v'] }
  ],
  camera_control: [
    { id: 'comfyui', name: 'ComfyUI', models: ['wan2.2-camera'] }
  ],
  text_to_speech: [
    { id: 'fishaudio', name: 'FishAudio', models: ['s2-pro'] },
    { id: 'openai', name: 'OpenAI', models: ['tts-1', 'tts-1-hd'] }
  ]
}