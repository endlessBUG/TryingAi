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
export function uploadImageArchive(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)

  return request({
    url: '/files/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 1800000
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
 * 图片预处理
 */
export function preprocessImages(
  datasetId: string,
  resolution: number = 512
): Promise<ApiResponse<{ total: number; resized: number; duplicates: string[] }>> {
  return request({
    url: `/files/datasets/${datasetId}/preprocess`,
    method: 'post',
    params: { resolution },
    timeout: 1800000
  })
}

/**
 * 提示词优化
 */
export function optimizePrompts(
  datasetId: string,
  triggerWord?: string
): Promise<ApiResponse<{ images: ImagePrompt[] }>> {
  return request({
    url: `/files/datasets/${datasetId}/optimize-prompts`,
    method: 'post',
    params: triggerWord ? { triggerWord } : {}
  })
}

/**
 * 图片质量评估
 */
export function evaluateQuality(
  datasetId: string,
  generatorId: string
): Promise<ApiResponse<Dataset>> {
  return request({
    url: `/files/datasets/${datasetId}/evaluate-quality`,
    method: 'post',
    params: { generatorId },
    timeout: 1800000
  })
}

/**
 * 使用指定生成器异步生成提示词
 */
export function regeneratePrompts(
  datasetId: string,
  generatorId: string
): Promise<ApiResponse> {
  return request({
    url: '/files/prompts/regenerate',
    method: 'post',
    params: { datasetId, generatorId }
  })
}
