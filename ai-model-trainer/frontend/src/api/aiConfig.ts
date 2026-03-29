/**
 * AI配置API模块
 */
import request from '@/utils/request'
import type {
  AIConfig,
  AIServiceType,
  CreateAIConfigRequest,
  TestConnectionRequest,
  TestGenerateRequest,
  TestGenerateResult,
  UpdateAIConfigRequest
} from '@/types/ai'

export const aiConfigAPI = {
  /**
   * 获取配置列表
   */
  list(serviceType?: AIServiceType): Promise<{ success: boolean; data: AIConfig[] }> {
    return request.get('/ai-configs', {
      params: { service_type: serviceType }
    })
  },

  /**
   * 获取单个配置
   */
  get(id: number): Promise<{ success: boolean; data: AIConfig }> {
    return request.get(`/ai-configs/${id}`)
  },

  /**
   * 创建配置
   */
  create(data: CreateAIConfigRequest): Promise<{ success: boolean; data: AIConfig }> {
    return request.post('/ai-configs', data)
  },

  /**
   * 更新配置
   */
  update(id: number, data: UpdateAIConfigRequest): Promise<{ success: boolean; data: AIConfig }> {
    return request.put(`/ai-configs/${id}`, data)
  },

  /**
   * 删除配置
   */
  delete(id: number): Promise<{ success: boolean }> {
    return request.delete(`/ai-configs/${id}`)
  },

  /**
   * 切换激活状态
   */
  toggleActive(id: number): Promise<{ success: boolean; data: AIConfig }> {
    return request.post(`/ai-configs/${id}/toggle-active`)
  },

  /**
   * 测试连接
   */
  testConnection(data: TestConnectionRequest): Promise<{ success: boolean; message: string }> {
    return request.post('/ai-configs/test', data)
  },

  /**
   * 测试生成
   */
  testGenerate(id: number, data: TestGenerateRequest): Promise<{ success: boolean; data: TestGenerateResult }> {
    return request.post(`/ai-configs/${id}/test-generate`, data)
  },

  /**
 * 获取测试任务状态
   */
  getTaskStatus(id: number, taskId: string): Promise<{ success: boolean; data: TestGenerateResult }> {
    return request.get(`/ai-configs/${id}/test-task/${taskId}`)
  },

  /**
   * 获取 ComfyUI 工作流列表（从目录读取）
   */
  getComfyuiWorkflows(): Promise<{ success: boolean; data: { filename: string; name: string; path: string }[] }> {
    return request.get('/ai-configs/comfyui-workflows')
  },

  /**
   * 获取默认端点配置
   */
  getDefaultEndpoints(): Promise<{ success: boolean; data: Record<string, Record<string, string>> }> {
    return request.get('/ai-configs/default-endpoints')
  }
}