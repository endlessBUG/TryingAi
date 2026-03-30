<template>
  <el-dialog
    v-model="visible"
    :title="'功能测试 - ' + config?.name"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="test-container">
      <!-- 左侧：输入表单 -->
      <div class="input-section">
        <!-- 文本生成 -->
        <el-form v-if="config?.serviceType === 'text'" :model="form" label-width="100px">
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="4"
              placeholder="请输入测试提示词..."
            />
          </el-form-item>
        </el-form>

        <!-- 图片生成 -->
        <el-form v-else-if="config?.serviceType === 'image'" :model="form" label-width="100px">
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="4"
              placeholder="描述你想生成的图片..."
            />
          </el-form-item>
          <el-form-item label="负面提示词">
            <el-input
              v-model="form.negativePrompt"
              type="textarea"
              :rows="2"
              placeholder="不想出现的内容（可选）..."
            />
            <div class="form-tip">描述不想在图片中出现的内容</div>
          </el-form-item>
          <el-form-item label="分辨率">
            <div class="resolution-inputs">
              <el-input-number v-model="form.width" :min="256" :max="2048" :step="64" placeholder="宽" />
              <span class="resolution-x">x</span>
              <el-input-number v-model="form.height" :min="256" :max="2048" :step="64" placeholder="高" />
            </div>
          </el-form-item>
          <el-form-item label="采样步数">
            <el-input-number v-model="form.steps" :min="1" :max="100" style="width: 100%" />
            <div class="form-tip">步数越多质量越好，但速度越慢。推荐: 20-50</div>
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="form.seed" :min="-1" :max="999999999" style="width: 100%" />
            <div class="form-tip">-1 表示随机，相同种子+相同参数可复现结果</div>
          </el-form-item>
        </el-form>

        <!-- 图生图 -->
        <el-form v-else-if="config?.serviceType === 'image_to_image'" :model="form" label-width="100px">
          <el-form-item label="输入图片" required>
            <el-upload
              class="image-uploader i2i-uploader"
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*"
            >
              <img v-if="form.imageUrl" :src="form.imageUrl" class="uploaded-image" />
              <el-icon v-else class="upload-icon"><Plus /></el-icon>
            </el-upload>
            <el-button
              v-if="form.imageUrl"
              type="primary"
              link
              size="small"
              class="i2i-preview-btn"
              @click.stop="openFullscreen('image', form.imageUrl)"
            >
              点击预览大图
            </el-button>
          </el-form-item>
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="4"
              placeholder="描述你想转换的效果..."
            />
          </el-form-item>
          <el-form-item label="负面提示词">
            <el-input
              v-model="form.negativePrompt"
              type="textarea"
              :rows="2"
              placeholder="不想出现的内容（可选）..."
            />
          </el-form-item>
          <el-form-item label="分辨率">
            <div class="resolution-inputs">
              <el-input-number v-model="form.width" :min="256" :max="2048" :step="64" />
              <span class="resolution-x">x</span>
              <el-input-number v-model="form.height" :min="256" :max="2048" :step="64" />
            </div>
          </el-form-item>
          <el-form-item label="采样步数">
            <el-input-number v-model="form.steps" :min="1" :max="100" style="width: 100%" />
            <div class="form-tip">步数越多质量越好。推荐: 20-50</div>
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="form.seed" :min="-1" :max="999999999" style="width: 100%" />
            <div class="form-tip">-1 表示随机</div>
          </el-form-item>
        </el-form>

        <!-- 视频生成 (文生视频) -->
        <el-form v-else-if="config?.serviceType === 'video'" :model="form" label-width="100px">
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="4"
              placeholder="描述视频内容和动作..."
            />
          </el-form-item>
          <el-form-item label="负面提示词">
            <el-input
              v-model="form.negativePrompt"
              type="textarea"
              :rows="2"
              placeholder="不想出现的内容..."
            />
          </el-form-item>
          <el-form-item label="分辨率">
            <div class="resolution-inputs">
              <el-input-number v-model="form.width" :min="256" :max="2048" :step="64" />
              <span class="resolution-x">x</span>
              <el-input-number v-model="form.height" :min="256" :max="2048" :step="64" />
            </div>
          </el-form-item>
          <el-form-item label="时长(秒)">
            <el-input-number v-model="form.durationSeconds" :min="1" :max="30" style="width: 100%" />
            <div class="form-tip">视频时长，帧数 = 秒数 × 16</div>
          </el-form-item>
          <el-form-item label="采样步数">
            <el-input-number v-model="form.steps" :min="1" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="form.seed" :min="-1" :max="999999999" style="width: 100%" />
          </el-form-item>
        </el-form>

        <!-- 首尾帧视频 -->
        <el-form v-else-if="config?.serviceType === 'video_frame'" :model="form" label-width="100px">
          <div class="frame-upload-row">
            <div class="frame-item">
              <div class="frame-label">首帧图片 *</div>
              <el-upload
                class="image-uploader frame"
                :show-file-list="false"
                :before-upload="(file: File) => handleFrameUpload(file, 'first')"
                accept="image/*"
              >
                <img v-if="form.firstFrameUrl" :src="form.firstFrameUrl" class="uploaded-image" />
                <el-icon v-else class="upload-icon"><Plus /></el-icon>
              </el-upload>
              <el-button
                v-if="form.firstFrameUrl"
                type="primary"
                link
                size="small"
                class="frame-preview-btn"
                @click.stop="openFullscreen('image', form.firstFrameUrl)"
              >
                点击预览大图
              </el-button>
            </div>
            <div class="frame-item">
              <div class="frame-label">尾帧图片 *</div>
              <el-upload
                class="image-uploader frame"
                :show-file-list="false"
                :before-upload="(file: File) => handleFrameUpload(file, 'last')"
                accept="image/*"
              >
                <img v-if="form.lastFrameUrl" :src="form.lastFrameUrl" class="uploaded-image" />
                <el-icon v-else class="upload-icon"><Plus /></el-icon>
              </el-upload>
              <el-button
                v-if="form.lastFrameUrl"
                type="primary"
                link
                size="small"
                class="frame-preview-btn"
                @click.stop="openFullscreen('image', form.lastFrameUrl)"
              >
                点击预览大图
              </el-button>
            </div>
          </div>
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="3"
              placeholder="描述过渡效果..."
            />
          </el-form-item>
          <el-form-item label="负面提示词">
            <el-input
              v-model="form.negativePrompt"
              type="textarea"
              :rows="2"
              placeholder="不想出现的内容..."
            />
          </el-form-item>
          <el-form-item label="分辨率">
            <div class="resolution-inputs">
              <el-input-number v-model="form.width" :min="256" :max="2048" :step="64" />
              <span class="resolution-x">x</span>
              <el-input-number v-model="form.height" :min="256" :max="2048" :step="64" />
            </div>
          </el-form-item>
          <el-form-item label="时长(秒)">
            <el-input-number v-model="form.durationSeconds" :min="1" :max="10" style="width: 100%" />
            <div class="form-tip">视频时长，帧数 = {{ form.durationSeconds }} × 16 + 1 = {{ form.durationSeconds * 16 + 1 }} 帧</div>
          </el-form-item>
          <el-form-item label="帧数">
            <el-input-number v-model="form.frames" :min="16" :max="160" :step="16" style="width: 100%" disabled />
            <div class="form-tip">自动计算：帧数 = 时长 × 16fps</div>
          </el-form-item>
          <el-form-item label="采样步数">
            <el-input-number v-model="form.steps" :min="1" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="form.seed" :min="-1" :max="999999999" style="width: 100%" />
          </el-form-item>
        </el-form>

        <!-- 语音图片转视频 -->
        <el-form v-else-if="config?.serviceType === 'sound_to_video'" :model="form" label-width="100px">
          <el-form-item label="输入图片" required>
            <el-upload
              class="image-uploader i2i-uploader"
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*"
            >
              <img v-if="form.imageUrl" :src="form.imageUrl" class="uploaded-image" />
              <el-icon v-else class="upload-icon"><Plus /></el-icon>
            </el-upload>
            <el-button
              v-if="form.imageUrl"
              type="primary"
              link
              size="small"
              class="i2i-preview-btn"
              @click.stop="openFullscreen('image', form.imageUrl)"
            >
              点击预览大图
            </el-button>
          </el-form-item>
          <el-form-item label="输入音频" required>
            <el-upload
              class="audio-uploader"
              :show-file-list="false"
              :before-upload="handleAudioUpload"
              accept="audio/*,.wav,.mp3,.m4a"
            >
              <div v-if="form.audioUrl" class="uploaded-audio">
                <el-icon><Headset /></el-icon>
                <span>音频已上传</span>
              </div>
              <div v-else class="upload-placeholder">
                <el-icon class="upload-icon"><Plus /></el-icon>
                <span>点击上传音频</span>
              </div>
            </el-upload>
          </el-form-item>
          <el-form-item label="提示词" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="3"
              placeholder="描述视频动作和表情..."
            />
          </el-form-item>
          <el-form-item label="负面提示词">
            <el-input
              v-model="form.negativePrompt"
              type="textarea"
              :rows="2"
              placeholder="不想出现的内容..."
            />
          </el-form-item>
          <el-form-item label="分辨率">
            <div class="resolution-inputs">
              <el-input-number v-model="form.width" :min="256" :max="2048" :step="64" />
              <span class="resolution-x">x</span>
              <el-input-number v-model="form.height" :min="256" :max="2048" :step="64" />
            </div>
          </el-form-item>
          <el-form-item label="时长(秒)">
            <el-input-number v-model="form.durationSeconds" :min="1" :max="30" style="width: 100%" />
            <div class="form-tip">视频时长，每秒约16帧，当前 {{ form.durationSeconds }} 秒 ≈ {{ form.durationSeconds * 16 }} 帧</div>
          </el-form-item>
          <el-form-item label="采样步数">
            <el-input-number v-model="form.steps" :min="1" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="随机种子">
            <el-input-number v-model="form.seed" :min="-1" :max="999999999" style="width: 100%" />
          </el-form-item>
        </el-form>

        <!-- 文本转语音 -->
        <el-form v-else-if="config?.serviceType === 'text_to_speech'" :model="form" label-width="100px">
          <el-form-item label="TTS模式">
            <el-radio-group v-model="form.ttsMode">
              <el-radio value="speech">普通合成</el-radio>
              <el-radio value="clone">音色克隆</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="输入文本" required>
            <el-input
              v-model="form.prompt"
              type="textarea"
              :rows="4"
              placeholder="请输入要转换为语音的文本..."
            />
          </el-form-item>
          <el-form-item v-if="form.ttsMode === 'speech'" label="音色">
            <el-input v-model="form.voice" placeholder="可选，输入音色ID或名称" />
            <div class="form-tip">OpenAI 支持 alloy/echo/fable/onyx/nova/shimmer</div>
          </el-form-item>
          <template v-else-if="form.ttsMode === 'clone'">
            <el-form-item label="参考音频" required>
              <el-upload
                class="audio-uploader"
                :show-file-list="false"
                :before-upload="handleReferenceAudioUpload"
                accept="audio/*,.wav,.mp3,.m4a"
              >
                <div v-if="form.referenceAudioUrl" class="uploaded-audio">
                  <el-icon><Headset /></el-icon>
                  <span>参考音频已上传</span>
                </div>
                <div v-else class="upload-placeholder">
                  <el-icon class="upload-icon"><Plus /></el-icon>
                  <span>点击上传参考音频</span>
                </div>
              </el-upload>
            </el-form-item>
            <el-form-item label="参考文本">
              <el-input
                v-model="form.referenceText"
                type="textarea"
                :rows="2"
                placeholder="参考音频对应的文本（可选）..."
              />
            </el-form-item>
          </template>
        </el-form>
      </div>

      <!-- 右侧：结果预览 -->
      <div class="preview-section">
        <div class="preview-header">
          <span>测试结果</span>
          <el-tag v-if="result?.taskId && !hasVideoResult && !result.imageUrl && !result.text" type="warning">
            处理中
          </el-tag>
        </div>

        <div class="preview-content">
          <div v-if="!result && !loading" class="preview-empty">
            <el-icon class="empty-icon"><Document /></el-icon>
            <span>暂无测试结果</span>
          </div>

          <div v-else-if="loading" class="preview-loading">
            <el-icon class="loading-icon is-loading"><Loading /></el-icon>
            <span>生成中...</span>
          </div>

          <div v-else-if="result?.text" class="preview-text">
            <p>{{ result.text }}</p>
          </div>

          <div v-else-if="result?.imageUrl" class="preview-image" @click="openFullscreen('image', result.imageUrl)">
            <img :src="result.imageUrl" alt="Generated Image" />
            <div class="fullscreen-hint">点击全屏查看</div>
          </div>

          <div v-else-if="hasVideoResult" class="preview-video" @click="openFullscreen('video', videoStreamUrl)">
            <video :src="videoStreamUrl" controls autoplay loop />
            <div class="fullscreen-hint">点击全屏查看</div>
          </div>

          <div v-else-if="result?.audioUrl" class="preview-audio">
            <audio :src="result.audioUrl" controls />
          </div>

          <div v-else-if="result?.taskId" class="preview-task">
            <p>任务ID: {{ result.taskId }}</p>
            <el-button size="small" @click="pollTaskStatus" :loading="polling">
              刷新状态
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleTest" :loading="loading">
        开始测试
      </el-button>
    </template>
  </el-dialog>

  <!-- 全屏预览 Dialog -->
  <el-dialog
    v-model="fullscreenVisible"
    :title="fullscreenType === 'image' ? '图片预览' : '视频预览'"
    width="90%"
    top="5vh"
    destroy-on-close
  >
    <div class="fullscreen-content">
      <img v-if="fullscreenType === 'image'" :src="fullscreenUrl" alt="Full Image" />
      <video v-else-if="fullscreenType === 'video'" :src="fullscreenUrl" controls autoplay loop />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Document, Loading, Headset } from '@element-plus/icons-vue'
import { aiConfigAPI } from '@/api/aiConfig'
import type { AIConfig, TestGenerateResult } from '@/types/ai'

const props = defineProps<{
  modelValue: boolean
  config: AIConfig | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const polling = ref(false)
const result = ref<TestGenerateResult | null>(null)

// 全屏预览
const fullscreenVisible = ref(false)
const fullscreenType = ref<'image' | 'video'>('image')
const fullscreenUrl = ref('')

// 计算视频流URL
const videoStreamUrl = computed(() => {
  if (result.value?.videoFilename && result.value?.configId) {
    const params = new URLSearchParams({
      filename: result.value.videoFilename,
      type: result.value.videoFileType || 'output'
    })
    if (result.value.videoSubfolder) {
      params.append('subfolder', result.value.videoSubfolder)
    }
    return `/api/ai-configs/${result.value.configId}/video-stream?${params.toString()}`
  }
  return result.value?.videoUrl || ''
})

// 是否有视频结果
const hasVideoResult = computed(() => {
  return result.value?.videoFilename || result.value?.videoUrl
})

const openFullscreen = (type: 'image' | 'video', url: string) => {
  fullscreenType.value = type
  fullscreenUrl.value = url
  fullscreenVisible.value = true
}

const form = reactive({
  prompt: '',
  imageUrl: '',
  audioUrl: '',
  firstFrameUrl: '',
  lastFrameUrl: '',
  width: 640,
  height: 640,
  voice: '',
  steps: 4,  // 默认4步，resetForm会根据配置更新
  seed: -1,
  durationSeconds: 9,  // 默认9秒
  frames: 144,  // 9秒 * 16帧
  negativePrompt: '色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，背景人很多，倒着走',
  ttsMode: 'speech' as 'speech' | 'clone',
  referenceAudioUrl: '',
  referenceText: ''
})

// 帧数自动计算联动（帧数 = 秒数 * 16 + 1，Wan模型推荐）
watch(() => form.durationSeconds, (val) => {
  form.frames = val * 16 + 1
})

watch(() => props.modelValue, (val) => {
  if (val) {
    resetForm()
    result.value = null
  }
})

const resetForm = () => {
  form.prompt = ''
  form.imageUrl = ''
  form.audioUrl = ''
  form.firstFrameUrl = ''
  form.lastFrameUrl = ''
  form.width = 640
  form.height = 640
  form.voice = ''
  form.steps = getDefaultSteps(props.config)
  form.seed = -1
  form.durationSeconds = 9  // 默认9秒
  form.frames = 144  // 9秒 * 16帧
  form.negativePrompt = '色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，背景人很多，倒着走'
  form.ttsMode = 'speech'
  form.referenceAudioUrl = ''
  form.referenceText = ''
}

/**
 * 工作流默认步数配置
 * key: 工作流文件名包含的关键字
 * value: 默认步数
 */
const WORKFLOW_DEFAULT_STEPS: Record<string, number> = {
  'turbo': 8,      // Turbo 快速模型
  'lightning': 4,  // Lightning 超快模型
  'flux2': 4,      // FLUX.2 模型
  'klein': 4,      // FLUX.2 Klein 模型
  'sdxl': 20,      // SDXL 标准模型
  'flux': 20,      // Flux 模型
  'wan22_t2v': 4,  // Wan2.2 文生视频 (4步蒸馏版)
  'wan22_s2v': 4,  // Wan2.2 语音图片转视频
  'wan': 4,        // Wan 其他视频模型 (默认4步)
}

/**
 * 根据配置获取默认步数
 */
const getDefaultSteps = (config: AIConfig | null): number => {
  if (!config?.settings) return 4  // 默认4步

  try {
    const parsed = JSON.parse(config.settings)
    const filename = (parsed.workflow_filename || '').toLowerCase()

    console.log('workflow_filename:', filename)  // 调试日志

    // 匹配工作流关键字
    for (const [keyword, steps] of Object.entries(WORKFLOW_DEFAULT_STEPS)) {
      if (filename.includes(keyword)) {
        console.log('matched keyword:', keyword, '-> steps:', steps)  // 调试日志
        return steps
      }
    }
  } catch {
    // ignore
  }

  return 4  // 默认4步
}

const handleImageUpload = (file: File) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.imageUrl = e.target?.result as string
  }
  reader.readAsDataURL(file)
  return false
}

const handleFrameUpload = (file: File, type: 'first' | 'last') => {
  const reader = new FileReader()
  reader.onload = (e) => {
    if (type === 'first') {
      form.firstFrameUrl = e.target?.result as string
    } else {
      form.lastFrameUrl = e.target?.result as string
    }
  }
  reader.readAsDataURL(file)
  return false
}

const handleAudioUpload = (file: File) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.audioUrl = e.target?.result as string
  }
  reader.readAsDataURL(file)
  return false
}

const handleReferenceAudioUpload = (file: File) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.referenceAudioUrl = e.target?.result as string
  }
  reader.readAsDataURL(file)
  return false
}

const handleTest = async () => {
  if (!props.config) return

  // 验证必填字段
  if (!form.prompt) {
    ElMessage.warning('请输入提示词')
    return
  }

  if (props.config.serviceType === 'image_to_image' && !form.imageUrl) {
    ElMessage.warning('请上传输入图片')
    return
  }

  if (props.config.serviceType === 'video_frame') {
    if (!form.firstFrameUrl || !form.lastFrameUrl) {
      ElMessage.warning('请上传首帧和尾帧图片')
      return
    }
  }

  if (props.config.serviceType === 'sound_to_video') {
    if (!form.imageUrl) {
      ElMessage.warning('请上传输入图片')
      return
    }
    if (!form.audioUrl) {
      ElMessage.warning('请上传音频')
      return
    }
  }

  if (props.config.serviceType === 'text_to_speech' && form.ttsMode === 'clone') {
    if (!form.referenceAudioUrl) {
      ElMessage.warning('请上传参考音频')
      return
    }
  }

  loading.value = true
  result.value = null

  try {
    const res = await aiConfigAPI.testGenerate(props.config.id, {
      prompt: form.prompt,
      imageUrl: form.imageUrl,
      audioUrl: form.audioUrl,
      firstFrameUrl: form.firstFrameUrl,
      lastFrameUrl: form.lastFrameUrl,
      width: form.width,
      height: form.height,
      voice: form.voice,
      steps: form.steps,
      seed: form.seed,
      duration: form.durationSeconds * 16 + 1, // 秒转帧，Wan模型需要 +1 帧
      frames: form.durationSeconds * 16 + 1,   // 与显示给用户的帧数一致
      negativePrompt: form.negativePrompt,
      ttsMode: form.ttsMode,
      referenceAudioUrl: form.referenceAudioUrl,
      referenceText: form.referenceText
    })
    console.log('API Response:', res)
    console.log('imageUrl:', res.data?.imageUrl)
    console.log('imageUrl length:', res.data?.imageUrl?.length)
    result.value = res.data
    ElMessage.success('测试成功')
  } catch (error: any) {
    ElMessage.error(error.message || '测试失败')
  } finally {
    loading.value = false
  }
}

const pollTaskStatus = async () => {
  if (!props.config || !result.value?.taskId) return

  polling.value = true
  try {
    const res = await aiConfigAPI.getTaskStatus(props.config.id, result.value.taskId)
    result.value = res.data
  } catch (error: any) {
    ElMessage.error(error.message || '获取状态失败')
  } finally {
    polling.value = false
  }
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.test-container {
  display: flex;
  gap: 24px;
  min-height: 400px;
}

.input-section {
  flex: 1;
  min-width: 300px;
  max-height: 500px;
  overflow-y: auto;
}

.preview-section {
  width: 400px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}

.preview-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
}

.preview-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  overflow: auto;
  min-height: 300px;
}

.preview-empty,
.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
}

.loading-icon {
  font-size: 32px;
  color: #409eff;
}

.preview-text {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-image,
.preview-video {
  cursor: pointer;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  max-height: 100%;
}

.preview-image img {
  max-width: 100%;
  max-height: 400px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 6px;
}

.preview-video video {
  max-width: 100%;
  max-height: 400px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 6px;
}

.preview-image:hover .fullscreen-hint,
.preview-video:hover .fullscreen-hint {
  opacity: 1;
}

.fullscreen-hint {
  position: absolute;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}

.fullscreen-content {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  overflow: auto;
}

.fullscreen-content img {
  max-width: 100%;
  max-height: 85vh;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 8px;
}

.fullscreen-content video {
  max-width: 100%;
  max-height: 85vh;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 8px;
}

.preview-audio {
  display: flex;
  justify-content: center;
  width: 100%;
}

.image-uploader {
  width: 200px;
  height: 200px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  background: #fafafa;
}

.image-uploader.i2i-uploader {
  width: 100%;
  max-width: 360px;
  min-height: 200px;
  height: auto;
  aspect-ratio: auto;
}

.image-uploader.i2i-uploader .uploaded-image {
  width: 100%;
  height: auto;
  max-height: 300px;
  object-fit: contain;
}

.i2i-preview-btn {
  display: block;
  margin: 8px 0 0 0;
}

.image-uploader:hover {
  border-color: #409eff;
}

.uploaded-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #f5f7fa;
}

.upload-icon {
  font-size: 28px;
  color: #8c939d;
}

.audio-uploader {
  width: 100%;
  height: 80px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.audio-uploader:hover {
  border-color: #409eff;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #909399;
}

.upload-placeholder .upload-icon {
  font-size: 24px;
}

.uploaded-audio {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
}

.frame-upload-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.frame-item {
  flex: 1;
}

.frame-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.image-uploader.frame {
  width: 100%;
  min-height: 180px;
  height: auto;
  background: #fafafa;
}

.image-uploader.frame .uploaded-image {
  width: 100%;
  height: auto;
  max-height: 280px;
  object-fit: contain;
}

.frame-preview-btn {
  display: block;
  margin: 8px auto 0;
}

.resolution-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.resolution-x {
  font-size: 14px;
  color: #606266;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.preview-task {
  text-align: center;
}
</style>