import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getSystemConfig, saveSystemConfig } from '@/api/systemConfig'

export const useSystemConfigStore = defineStore('systemConfig', () => {
  const configs = ref<Record<string, string>>({})
  const loaded = ref(false)

  async function load() {
    const res = await getSystemConfig()
    configs.value = res.data || {}
    loaded.value = true
  }

  function get(key: string, fallback = ''): string {
    return configs.value[key] || fallback
  }

  async function save(data: Record<string, string>) {
    await saveSystemConfig(data)
    Object.assign(configs.value, data)
  }

  return { configs, loaded, load, get, save }
})
