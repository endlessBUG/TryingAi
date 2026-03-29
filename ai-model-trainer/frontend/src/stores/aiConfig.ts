/**
 * AI配置状态管理
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { aiConfigAPI } from '@/api/aiConfig'
import type { AIConfig, AIServiceType, CreateAIConfigRequest, UpdateAIConfigRequest } from '@/types/ai'
import { ElMessage } from 'element-plus'

export const useAIConfigStore = defineStore('aiConfig', () => {
  const configs = ref<AIConfig[]>([])
  const loading = ref(false)
  const currentServiceType = ref<AIServiceType>('text')

  /**
   * 加载配置列表
   */
  async function loadConfigs(serviceType?: AIServiceType) {
    loading.value = true
    try {
      const res = await aiConfigAPI.list(serviceType)
      configs.value = res.data || []
      if (serviceType) {
        currentServiceType.value = serviceType
      }
    } catch (error: any) {
      ElMessage.error(error.message || '加载配置失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建配置
   */
  async function createConfig(data: CreateAIConfigRequest): Promise<AIConfig | null> {
    try {
      const res = await aiConfigAPI.create(data)
      ElMessage.success('创建成功')
      await loadConfigs(currentServiceType.value)
      return res.data
    } catch (error: any) {
      ElMessage.error(error.message || '创建失败')
      return null
    }
  }

  /**
   * 更新配置
   */
  async function updateConfig(id: number, data: UpdateAIConfigRequest): Promise<AIConfig | null> {
    try {
      const res = await aiConfigAPI.update(id, data)
      ElMessage.success('更新成功')
      await loadConfigs(currentServiceType.value)
      return res.data
    } catch (error: any) {
      ElMessage.error(error.message || '更新失败')
      return null
    }
  }

  /**
   * 删除配置
   */
  async function deleteConfig(id: number): Promise<boolean> {
    try {
      await aiConfigAPI.delete(id)
      ElMessage.success('删除成功')
      await loadConfigs(currentServiceType.value)
      return true
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
      return false
    }
  }

  /**
   * 切换激活状态
   */
  async function toggleActive(id: number): Promise<boolean> {
    try {
      await aiConfigAPI.toggleActive(id)
      await loadConfigs(currentServiceType.value)
      return true
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
      return false
    }
  }

  return {
    configs,
    loading,
    currentServiceType,
    loadConfigs,
    createConfig,
    updateConfig,
    deleteConfig,
    toggleActive
  }
})