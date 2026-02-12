/**
 * 系统配置API
 */
import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export function getSystemConfig(): Promise<ApiResponse<Record<string, string>>> {
  return request({ url: '/system-config', method: 'get' })
}

export function saveSystemConfig(data: Record<string, string>): Promise<ApiResponse> {
  return request({ url: '/system-config', method: 'put', data })
}
