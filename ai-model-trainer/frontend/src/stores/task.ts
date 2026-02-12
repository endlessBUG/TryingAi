/**
 * 任务Store
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ImagePrompt } from '@/types'

interface DatasetInfo {
  datasetPath: string
  imageCount: number
  images: ImagePrompt[]
}

export const useTaskStore = defineStore('task', () => {
  const datasetInfo = ref<DatasetInfo | null>(null)

  function setDatasetInfo(info: DatasetInfo) {
    datasetInfo.value = info
  }

  function getDatasetInfo() {
    return datasetInfo.value
  }

  function clearDatasetInfo() {
    datasetInfo.value = null
  }

  return {
    datasetInfo,
    setDatasetInfo,
    getDatasetInfo,
    clearDatasetInfo
  }
})
