<template>
  <div class="trainer-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>训练器管理</span>
          <el-button type="primary" @click="handleAdd">新增训练器</el-button>
        </div>
      </template>

      <el-table :data="trainerList" v-loading="loading" stripe>
        <el-table-column prop="name" label="训练器名称" min-width="140" />
        <el-table-column prop="gitUrl" label="Git 地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="存放地址" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.path || '（训练时自动下载）' }}
          </template>
        </el-table-column>
        <el-table-column prop="pythonVersion" label="Python 版本" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该训练器吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑训练器' : '新增训练器'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="训练器名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入训练器名称" />
        </el-form-item>
        <el-form-item label="Git 地址" prop="gitUrl">
          <el-input v-model="form.gitUrl" placeholder="例如: https://github.com/ostris/ai-toolkit.git" />
        </el-form-item>
        <el-form-item label="存放地址">
          <el-input v-model="form.path" placeholder="可选，留空则训练时自动从 Git 下载" />
          <div class="field-hint">填写绝对路径可跳过 Git 下载，如 C:\ai-toolkit 或 /opt/ai-toolkit</div>
        </el-form-item>
        <el-form-item label="Python 版本" prop="pythonVersion">
          <el-input v-model="form.pythonVersion" placeholder="例如: 3.10" />
        </el-form-item>
        <el-form-item label="默认YAML配置">
          <YamlEditor v-model="form.defaultYamlConfig" height="420px" />
          <div class="yaml-hint">数据集路径请使用 <code>{{DATASET_PATH}}</code> 占位符</div>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="loadDefaultTemplate">加载默认模板</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { getTrainers, createTrainer, updateTrainer, deleteTrainer } from '@/api/trainer'
import type { Trainer } from '@/types'
import YamlEditor from '@/components/YamlEditor.vue'

const loading = ref(false)
const trainerList = ref<Trainer[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = ref<Trainer>({ name: '', path: '', gitUrl: '', pythonVersion: '', defaultYamlConfig: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入训练器名称', trigger: 'blur' }],
  gitUrl: [{ validator: (_r: any, _v: any, cb: any) => {
    if (!form.value.gitUrl && !form.value.path) cb(new Error('Git 地址和存放地址至少填一个'))
    else cb()
  }, trigger: 'blur' }],
  pythonVersion: [{ required: true, message: '请输入Python版本', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await getTrainers()
    trainerList.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  editingId.value = ''
  form.value = { name: '', path: '', gitUrl: '', pythonVersion: '', defaultYamlConfig: '' }
  dialogVisible.value = true
}

function handleEdit(row: Trainer) {
  isEdit.value = true
  editingId.value = row.id || ''
  form.value = { name: row.name, path: row.path || '', gitUrl: row.gitUrl || '', pythonVersion: row.pythonVersion, defaultYamlConfig: row.defaultYamlConfig || '' }
  dialogVisible.value = true
}

const DEFAULT_YAML_TEMPLATE = `---
job: extension
config:
  name: "my_first_wan22_14b_lora_v1"
  process:
    - type: 'sd_trainer'
      training_folder: "output"
      device: cuda:0
      network:
        type: "lora"
        linear: 32
        linear_alpha: 32
      save:
        dtype: float16
        save_every: 250
        max_step_saves_to_keep: 4
      datasets:
        - folder_path: "{{DATASET_PATH}}"
          caption_ext: "txt"
          caption_dropout_rate: 0.05
          num_frames: 1
          resolution: [512, 768, 1024]
      train:
        batch_size: 1
        steps: 2000
        gradient_accumulation: 1
        train_unet: true
        train_text_encoder: false
        gradient_checkpointing: true
        noise_scheduler: "flowmatch"
        timestep_type: 'linear'
        optimizer: "adamw8bit"
        lr: 1e-4
        optimizer_params:
          weight_decay: 1e-4
        dtype: bf16
        switch_boundary_every: 10
        cache_text_embeddings: true
      model:
        name_or_path: "ai-toolkit/Wan2.2-T2V-A14B-Diffusers-bf16"
        arch: 'wan22_14b'
        quantize: true
        qtype: "uint4|ostris/accuracy_recovery_adapters/wan22_14b_t2i_torchao_uint4.safetensors"
        quantize_te: true
        qtype_te: "qfloat8"
        low_vram: true
        model_kwargs:
          train_high_noise: true
          train_low_noise: true
      sample:
        sampler: "flowmatch"
        sample_every: 250
        width: 1024
        height: 1024
        num_frames: 1
        fps: 16
        prompts:
          - "woman with red hair, playing chess at the park, bomb going off in the background"
          - "a woman holding a coffee cup, in a beanie, sitting at a cafe"
          - "a horse is a DJ at a night club, fish eye lens, smoke machine, lazer lights, holding a martini"
        neg: ""
        seed: 42
        walk_seed: true
        guidance_scale: 3.5
        sample_steps: 25
meta:
  name: "[name]"
  version: '1.0'
`

function loadDefaultTemplate() {
  form.value.defaultYamlConfig = DEFAULT_YAML_TEMPLATE
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTrainer(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createTrainer(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: string) {
  await deleteTrainer(id)
  ElMessage.success('删除成功')
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.trainer-view {
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
  line-height: 1.4;
}
.yaml-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.yaml-hint code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  color: #e6a23c;
}
</style>
