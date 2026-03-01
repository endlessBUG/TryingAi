/**
 * 路由配置
 */
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/upload'
    },
    {
      path: '/',
      component: () => import('@/components/Layout.vue'),
      children: [
        {
          path: 'upload',
          name: 'Upload',
          component: () => import('@/views/UploadView.vue'),
          meta: { title: '数据集管理' }
        },
        {
          path: 'prompt-generators',
          name: 'PromptGenerators',
          component: () => import('@/views/PromptGeneratorView.vue'),
          meta: { title: '提示词生成器' }
        },
        {
          path: 'tasks',
          name: 'Tasks',
          component: () => import('@/views/TasksView.vue'),
          meta: { title: '任务列表' }
        },
        {
          path: 'trainers',
          name: 'Trainers',
          component: () => import('@/views/TrainerView.vue'),
          meta: { title: '训练器管理' }
        },
        {
          path: 'comfyui',
          name: 'ComfyUI',
          component: () => import('@/views/ComfyUIView.vue'),
          meta: { title: 'ComfyUI' }
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('@/views/SettingsView.vue'),
          meta: { title: '配置中心' }
        }
      ]
    }
  ]
})

export default router
