<template>
  <div class="config-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>配置管理</span>
          <div>
            <el-button @click="handleLoadTemplate">加载模板</el-button>
            <el-button type="primary" @click="handleSave">保存配置</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="可视化编辑" name="visual">
          <el-form :model="config" label-width="160px">
            <el-divider content-position="left">任务配置</el-divider>

            <el-form-item label="任务名称">
              <el-input v-model="config.job.name" />
            </el-form-item>

            <el-form-item label="设备">
              <el-input v-model="config.job.device" placeholder="cuda:0" />
            </el-form-item>

            <el-form-item label="触发词">
              <el-input v-model="config.job.trigger_word" />
            </el-form-item>

            <el-divider content-position="left">模型配置</el-divider>

            <el-form-item label="模型路径">
              <el-input v-model="config.model.name_or_path" />
            </el-form-item>

            <el-form-item label="是否Flux模型">
              <el-switch v-model="config.model.is_flux" />
            </el-form-item>

            <el-divider content-position="left">训练配置</el-divider>

            <el-form-item label="数据类型">
              <el-select v-model="config.train.dtype">
                <el-option label="FP16" value="fp16" />
                <el-option label="BF16" value="bf16" />
                <el-option label="FP32" value="fp32" />
              </el-select>
            </el-form-item>

            <el-form-item label="训练步数">
              <el-input-number v-model="config.train.train_steps" :min="100" />
            </el-form-item>

            <el-form-item label="学习率">
              <el-input-number
                v-model="config.train.learning_rate"
                :min="0.00001"
                :max="0.001"
                :step="0.00001"
                :precision="5"
              />
            </el-form-item>

            <el-form-item label="批次大小">
              <el-input-number v-model="config.train.batch_size" :min="1" />
            </el-form-item>

            <el-form-item label="优化器">
              <el-select v-model="config.train.optimizer">
                <el-option label="AdamW 8bit" value="adamw8bit" />
                <el-option label="AdamW" value="adamw" />
                <el-option label="SGD" value="sgd" />
              </el-select>
            </el-form-item>

            <el-form-item label="学习率调度器">
              <el-select v-model="config.train.lr_scheduler">
                <el-option label="Constant" value="constant" />
                <el-option label="Linear" value="linear" />
                <el-option label="Cosine" value="cosine" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">数据集配置</el-divider>

            <div v-for="(dataset, index) in config.datasets" :key="index" style="margin-bottom: 20px;">
              <el-form-item label="数据集路径">
                <el-input v-model="dataset.folder_path" />
              </el-form-item>

              <el-form-item label="标注文件扩展名">
                <el-input v-model="dataset.caption_ext" />
              </el-form-item>

              <el-form-item label="分辨率">
                <el-input-number v-model="dataset.resolution" :min="256" :step="64" />
              </el-form-item>
            </div>

            <el-divider content-position="left">网络配置 (LoRA)</el-divider>

            <el-form-item label="类型">
              <el-select v-model="config.network.type">
                <el-option label="LoRA" value="lora" />
                <el-option label="LyCORIS" value="lycoris" />
              </el-select>
            </el-form-item>

            <el-form-item label="Rank">
              <el-input-number v-model="config.network.rank" :min="1" />
            </el-form-item>

            <el-form-item label="Alpha">
              <el-input-number v-model="config.network.alpha" :min="1" :step="0.1" />
            </el-form-item>

            <el-divider content-position="left">保存配置</el-divider>

            <el-form-item label="保存频率">
              <el-input-number v-model="config.save.save_every" :min="100" :step="100" />
            </el-form-item>

            <el-form-item label="保留检查点数">
              <el-input-number v-model="config.save.max_step_saves_to_keep" :min="1" />
            </el-form-item>

            <el-divider content-position="left">采样配置 (可选)</el-divider>

            <el-form-item label="采样器">
              <el-input v-model="config.sample.sampler" />
            </el-form-item>

            <el-form-item label="采样频率">
              <el-input-number v-model="config.sample.sample_every" :min="0" />
            </el-form-item>

            <el-form-item label="宽度">
              <el-input-number v-model="config.sample.width" :min="256" :step="64" />
            </el-form-item>

            <el-form-item label="高度">
              <el-input-number v-model="config.sample.height" :min="256" :step="64" />
            </el-form-item>

            <el-form-item label="提示词">
              <el-select
                v-model="config.sample.prompts"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入提示词后按回车添加"
                style="width: 100%"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="YAML编辑" name="yaml">
          <el-input
            v-model="yamlText"
            type="textarea"
            :rows="30"
            placeholder="YAML配置内容"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTrainingTemplate, saveYamlConfig } from '@/api/config'
import type { ConfigTemplate } from '@/types'

const activeTab = ref('visual')
const yamlText = ref('')

const config = ref<ConfigTemplate>({
  job: {
    name: 'my_training',
    device: 'cuda:0',
    trigger_word: ''
  },
  model: {
    name_or_path: 'runwayml/stable-diffusion-v1-5',
    is_flux: false
  },
  train: {
    dtype: 'fp16',
    train_steps: 1000,
    learning_rate: 0.0001,
    batch_size: 1,
    optimizer: 'adamw8bit',
    lr_scheduler: 'constant',
    gradient_accumulation_steps: 1
  },
  datasets: [
    {
      folder_path: './data/datasets',
      caption_ext: 'txt',
      resolution: 512
    }
  ],
  network: {
    type: 'lora',
    rank: 4,
    alpha: 4.0
  },
  save: {
    save_every: 500,
    max_step_saves_to_keep: 3
  },
  sample: {
    sampler: 'ddpm',
    sample_every: 100,
    width: 512,
    height: 512,
    prompts: ['a photo']
  }
})

// 监听config变化，更新yaml文本
watch(
  config,
  (newVal) => {
    if (activeTab.value === 'visual') {
      yamlText.value = JSON.stringify(newVal, null, 2)
    }
  },
  { deep: true }
)

// 监听yaml文本变化，更新config
watch(yamlText, (newVal) => {
  if (activeTab.value === 'yaml') {
    try {
      config.value = JSON.parse(newVal)
    } catch (error) {
      // 忽略JSON解析错误
    }
  }
})

const handleLoadTemplate = async () => {
  try {
    const response = await getTrainingTemplate()
    if (response.data?.template) {
      config.value = response.data.template as ConfigTemplate
      ElMessage.success('模板加载成功')
    }
  } catch (error) {
    console.error('Load template failed:', error)
  }
}

const handleSave = async () => {
  try {
    const filePath = `./data/configs/${config.value.job.name}_${Date.now()}.yaml`
    await saveYamlConfig(filePath, config.value)
    ElMessage.success(`配置已保存到: ${filePath}`)
  } catch (error) {
    console.error('Save config failed:', error)
  }
}

// 初始化时加载模板
handleLoadTemplate()
</script>

<style scoped>
.config-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
