<template>
  <div class="jupyter-container">
    <div v-if="loading" class="jupyter-loading" v-loading="true" element-loading-text="加载中..." />
    <div v-else-if="!jupyterUrl" class="jupyter-empty">
      <el-empty description="未配置 Jupyter Notebook 地址">
        <el-button type="primary" @click="$router.push('/settings')">前往配置</el-button>
      </el-empty>
    </div>
    <iframe
      v-else
      :src="jupyterUrl"
      class="jupyter-iframe"
      frameborder="0"
      allowfullscreen
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSystemConfigStore } from '@/stores/systemConfig'

const configStore = useSystemConfigStore()
const loading = computed(() => !configStore.loaded)
const jupyterUrl = computed(() => configStore.get('jupyter.url'))
</script>

<style scoped>
.jupyter-container {
  width: 100%;
  height: calc(100vh - 60px);
  overflow: hidden;
}

.jupyter-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.jupyter-loading,
.jupyter-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
</style>
