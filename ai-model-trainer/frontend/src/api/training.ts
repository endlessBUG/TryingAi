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
 * 一键训练流水线
 */
export function autoPipeline(
  datasetId: string,
  trainerId: string,
  yamlConfig?: string
): Promise<ApiResponse> {
  return request({
    url: '/training/auto-pipeline',
    method: 'post',
    params: { datasetId, trainerId },
    data: yamlConfig ? { yamlConfig } : undefined
  })
}

/**
 * 智能超参数推荐
 */
export function recommendParams(datasetId: string): Promise<ApiResponse<Record<string, any>>> {
  return request({
    url: `/training/recommend/${datasetId}`,
    method: 'get'
  })
}

/**
 * 对比任务
 */
export function compareTasks(taskIds: string[]): Promise<TaskListResponse> {
  return request({
    url: '/training/tasks/compare',
    method: 'get',
    params: { taskIds: taskIds.join(',') }
  })
}

/**
 * 按数据集获取任务
 */
export function getTasksByDataset(datasetId: string): Promise<TaskListResponse> {
  return request({
    url: `/training/tasks/by-dataset/${datasetId}`,
    method: 'get'
  })
}

/**
 * 获取任务日志
 */
export function getTaskLog(taskId: string): Promise<ApiResponse<string>> {
  return request({ url: `/training/tasks/${taskId}/log`, method: 'get' })
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

