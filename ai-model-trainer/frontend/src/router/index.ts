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
          path: 'conda',
          name: 'Conda',
          component: () => import('@/views/CondaConfigView.vue'),
          meta: { title: 'Conda 配置' }
        },
        {
          path: 'config',
          name: 'Config',
          component: () => import('@/views/ConfigView.vue'),
          meta: { title: '配置管理' }
        }
      ]
    }
  ]
})

export default router
