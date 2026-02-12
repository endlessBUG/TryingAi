/**
 * 配置管理API
 */
import request from '@/utils/request'
import type { ConfigTemplate, ApiResponse } from '@/types'

/**
 * 读取YAML配置
 */
export function readYamlConfig(filePath: string): Promise<ApiResponse<{ config: any }>> {
  return request({
    url: '/config/yaml',
    method: 'get',
    params: { filePath }
  })
}

/**
 * 保存YAML配置
 */
export function saveYamlConfig(filePath: string, configData: any): Promise<ApiResponse> {
  return request({
    url: '/config/yaml',
    method: 'post',
    params: { filePath },
    data: configData
  })
}

/**
 * 获取训练配置模板
 */
export function getTrainingTemplate(): Promise<ApiResponse<{ template: ConfigTemplate }>> {
  return request({
    url: '/config/template/training',
    method: 'get'
  })
}
