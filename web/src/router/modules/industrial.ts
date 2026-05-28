import { RouteRecordRaw } from 'vue-router';
import Layout from '@/layout/index.vue';

const industrialRoutes: RouteRecordRaw[] = [
  {
    path: '/industrial',
    component: Layout,
    redirect: '/industrial/chat',
    meta: { title: '工业知识库', icon: 'tool' },
    alwaysShow: true,
    children: [
      {
        path: 'chat',
        component: () => import('@/views/industrial/IndustrialChat.vue'),
        name: 'IndustrialChat',
        meta: { title: '知识问答', icon: 'chat' }
      },
      {
        path: 'qa-manage',
        component: () => import('@/views/industrial/QAManage.vue'),
        name: 'QAManage',
        meta: { title: 'QA 知识库管理', icon: 'list' }
      },
      {
        path: 'equipment-params',
        component: () => import('@/views/industrial/EquipmentParams.vue'),
        name: 'EquipmentParams',
        meta: { title: '设备参数管理', icon: 'monitor' }
      },
      {
        path: 'fault-code',
        component: () => import('@/views/industrial/FaultCodeManage.vue'),
        name: 'FaultCodeManage',
        meta: { title: '故障代码管理', icon: 'warning' }
      }
    ]
  }
];

export default industrialRoutes;
