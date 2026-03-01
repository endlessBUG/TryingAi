/**
 * 提示词生成器API
 */
import request from '@/utils/request'
import type { PromptGenerator, ApiResponse } from '@/types'

export function getPromptGenerators(): Promise<ApiResponse<PromptGenerator[]>> {
  return request({ url: '/prompt-generators', method: 'get' })
}

export function createPromptGenerator(data: PromptGenerator): Promise<ApiResponse<PromptGenerator>> {
  return request({ url: '/prompt-generators', method: 'post', data })
}

export function updatePromptGenerator(id: string, data: PromptGenerator): Promise<ApiResponse<PromptGenerator>> {
  return request({ url: `/prompt-generators/${id}`, method: 'put', data })
}

export function deletePromptGenerator(id: string): Promise<ApiResponse> {
  return request({ url: `/prompt-generators/${id}`, method: 'delete' })
}

export function testPromptGenerator(id: string, file?: File): Promise<ApiResponse<string>> {
  if (file) {
    const formData = new FormData()
    formData.append('file', file)
    return request({
      url: `/prompt-generators/${id}/test`,
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  }
  return request({ url: `/prompt-generators/${id}/test`, method: 'post', timeout: 60000 })
}
