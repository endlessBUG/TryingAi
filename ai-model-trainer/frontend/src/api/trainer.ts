/**
 * 训练器管理API
 */
import request from '@/utils/request'
import type { Trainer, ApiResponse } from '@/types'

export function getTrainers(): Promise<ApiResponse<Trainer[]>> {
  return request({ url: '/trainers', method: 'get' })
}

export function createTrainer(data: Trainer): Promise<ApiResponse<Trainer>> {
  return request({ url: '/trainers', method: 'post', data })
}

export function updateTrainer(id: string, data: Trainer): Promise<ApiResponse<Trainer>> {
  return request({ url: `/trainers/${id}`, method: 'put', data })
}

export function deleteTrainer(id: string): Promise<ApiResponse> {
  return request({ url: `/trainers/${id}`, method: 'delete' })
}
