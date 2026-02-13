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

export function testPromptGenerator(id: string): Promise<ApiResponse<string>> {
  return request({ url: `/prompt-generators/${id}/test`, method: 'post', timeout: 60000 })
}
