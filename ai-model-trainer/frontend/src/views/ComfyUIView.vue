<template>
  <div class="comfyui-container">
    <div v-if="loading" class="comfyui-loading" v-loading="true" element-loading-text="加载中..." />
    <div v-else-if="!comfyUrl" class="comfyui-empty">
      <el-empty description="未配置 ComfyUI 地址">
        <el-button type="primary" @click="$router.push('/settings')">前往配置</el-button>
      </el-empty>
    </div>
    <iframe
      v-else
      :src="comfyUrl"
      class="comfyui-iframe"
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
const comfyUrl = computed(() => configStore.get('comfyui.url'))
</script>

<style scoped>
.comfyui-container {
  width: 100%;
  height: calc(100vh - 60px);
  overflow: hidden;
}

.comfyui-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.comfyui-loading,
.comfyui-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
</style>
