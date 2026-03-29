<template>
  <div class="config-list">
    <el-table :data="configs" v-loading="loading" stripe>
      <el-table-column prop="name" label="配置名称" min-width="150" />
      <el-table-column prop="provider" label="厂商" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.provider || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="model" label="模型" min-width="150">
        <template #default="{ row }">
          <span>{{ formatModels(row.model) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" align="center" />
      <el-table-column prop="isActive" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
            {{ row.isActive ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="$emit('test', row)">测试</el-button>
          <el-button size="small" @click="$emit('edit', row)">编辑</el-button>
          <el-button
            size="small"
            :type="row.isActive ? 'warning' : 'success'"
            @click="$emit('toggle-active', row)"
          >
            {{ row.isActive ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="$emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && configs.length === 0" description="暂无配置，点击上方按钮添加" />
  </div>
</template>

<script setup lang="ts">
import type { AIConfig } from '@/types/ai'

defineProps<{
  configs: AIConfig[]
  loading: boolean
}>()

defineEmits<{
  (e: 'edit', config: AIConfig): void
  (e: 'delete', config: AIConfig): void
  (e: 'toggle-active', config: AIConfig): void
  (e: 'test', config: AIConfig): void
}>()

const formatModels = (models: string[] | string): string => {
  if (!models) return '-'
  if (Array.isArray(models)) {
    return models.length > 0 ? models.slice(0, 2).join(', ') + (models.length > 2 ? '...' : '') : '-'
  }
  return models
}
</script>

<style scoped>
.config-list {
  min-height: 200px;
}
</style>