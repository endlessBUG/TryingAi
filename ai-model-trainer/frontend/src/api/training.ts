/**
 * 训练任务API
 */
import request from '@/utils/request'
import type { CreateTaskRequest, TaskResponse, TaskListResponse, ApiResponse } from '@/types'

/**
 * 创建训练任务
 */
export function createTask(data: CreateTaskRequest): Promise<TaskResponse> {
  return request({
    url: '/training/tasks',
    method: 'post',
    data
  })
}

/**
 * 启动训练任务
 */
export function startTask(taskId: string): Promise<ApiResponse> {
  return request({
    url: `/training/tasks/${taskId}/start`,
    method: 'post'
  })
}

/**
 * 重新训练任务
 */
export function restartTask(taskId: string): Promise<ApiResponse> {
  return request({
    url: `/training/tasks/${taskId}/restart`,
    method: 'post'
  })
}

/**
 * 停止训练任务
 */
export function stopTask(taskId: string): Promise<ApiResponse> {
  return request({
    url: `/training/tasks/${taskId}/stop`,
    method: 'post'
  })
}

/**
 * 获取任务详情
 */
export function getTask(taskId: string): Promise<TaskResponse> {
  return request({
    url: `/training/tasks/${taskId}`,
    method: 'get'
  })
}

/**
 * 获取所有任务
 */
export function getAllTasks(): Promise<TaskListResponse> {
  return request({
    url: '/training/tasks',
    method: 'get'
  })
}

/**
 * 删除任务
 */
export function deleteTask(taskId: string): Promise<ApiResponse> {
  return request({
    url: `/training/tasks/${taskId}`,
    method: 'delete'
  })
}

