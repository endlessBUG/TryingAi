/**
 * 文件管理API
 */
import request from '@/utils/request'
import type { Dataset, ImagePrompt, UploadResponse, ApiResponse } from '@/types'

/**
 * 获取数据集列表
 */
export function getDatasets(): Promise<ApiResponse<Dataset[]>> {
  return request({ url: '/files/datasets', method: 'get' })
}

/**
 * 删除数据集
 */
export function deleteDataset(id: string): Promise<ApiResponse> {
  return request({ url: `/files/datasets/${id}`, method: 'delete' })
}

/**
 * 上传图片压缩包（新增数据集）
 */
export function uploadImageArchive(
  file: File,
  generatePrompts: boolean = true,
  useAiGeneration: boolean = false
): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('generatePrompts', String(generatePrompts))
  formData.append('useAiGeneration', String(useAiGeneration))

  return request({
    url: '/files/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  })
}

/**
 * 获取数据集详情（图片列表）
 */
export function getDatasetDetail(id: string): Promise<ApiResponse<Dataset>> {
  return request({ url: `/files/datasets/${id}`, method: 'get' })
}

/**
 * 更新提示词
 */
export function updatePrompts(prompts: ImagePrompt[]): Promise<ApiResponse> {
  return request({ url: '/files/prompts', method: 'put', data: prompts })
}

/**
 * 重新生成提示词
 */
export function regeneratePrompts(
  prompts: ImagePrompt[],
  useAiGeneration: boolean = false
): Promise<ApiResponse<{ images: ImagePrompt[] }>> {
  return request({
    url: '/files/prompts/regenerate',
    method: 'post',
    params: { useAiGeneration },
    data: prompts
  })
}
