import request from '@/utils/request'

export function getWorkflows() {
  return request.get('/comfyui/workflows')
}

export function getWorkflow(id: string) {
  return request.get(`/comfyui/workflows/${id}`)
}

export function createWorkflow(data: any) {
  return request.post('/comfyui/workflows', data)
}

export function updateWorkflow(id: string, data: any) {
  return request.put(`/comfyui/workflows/${id}`, data)
}

export function deleteWorkflow(id: string) {
  return request.delete(`/comfyui/workflows/${id}`)
}

export function executeWorkflow(id: string, params?: Record<string, any>) {
  return request.post(`/comfyui/workflows/${id}/execute`, params || {})
}

export function getNodeInfo() {
  return request.get('/comfyui/node-info')
}

export function getHistory(promptId: string) {
  return request.get(`/comfyui/history/${promptId}`)
}

export function getImageUrl(filename: string, subfolder?: string, type?: string) {
  let url = `/comfyui/image?filename=${encodeURIComponent(filename)}`
  if (subfolder) url += `&subfolder=${encodeURIComponent(subfolder)}`
  if (type) url += `&type=${encodeURIComponent(type)}`
  return url
}