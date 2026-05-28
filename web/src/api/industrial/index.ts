import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ==================== 类型定义 ====================

/** QA 对 */
export interface QAPair {
  id?: number;
  question: string;
  answer: string;
  category: string;
  equipmentType?: string;
  tags?: string;
  createTime?: string;
  updateTime?: string;
}

/** QA 查询参数 */
export interface QAQuery {
  pageNum?: number;
  pageSize?: number;
  category?: string;
  keyword?: string;
}

/** 设备信息 */
export interface Equipment {
  id?: number;
  equipmentCode: string;
  equipmentName: string;
  equipmentType: string;
  status: string;
  params?: EquipmentParam[];
}

/** 设备参数 */
export interface EquipmentParam {
  id?: number;
  equipmentId?: number;
  paramName: string;
  targetValue: string;
  upperLimit: string;
  lowerLimit: string;
  unit: string;
}

/** 故障代码 */
export interface FaultCode {
  id?: number;
  code: string;
  description: string;
  severity: string;
  equipmentType: string;
  solution?: string;
  createTime?: string;
}

/** 故障代码查询参数 */
export interface FaultCodeQuery {
  pageNum?: number;
  pageSize?: number;
  severity?: string;
  equipmentType?: string;
  keyword?: string;
}

/** 对话消息 */
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  citations?: Citation[];
  timestamp?: string;
}

/** 引用溯源 */
export interface Citation {
  id: number;
  title: string;
  content: string;
  source: string;
  relevance: number;
}

/** 对话请求 */
export interface ChatRequest {
  message: string;
  sessionId?: string;
  category?: string;
}

// ==================== QA 管理接口 ====================

/** 查询 QA 列表 */
export function listQA(query: QAQuery) {
  return request({
    url: '/api/industrial/qa/list',
    method: 'get',
    params: query
  });
}

/** 查询 QA 详情 */
export function getQA(id: number) {
  return request({
    url: `/api/industrial/qa/${id}`,
    method: 'get'
  });
}

/** 新增 QA */
export function addQA(data: QAPair) {
  return request({
    url: '/api/industrial/qa',
    method: 'post',
    data
  });
}

/** 修改 QA */
export function updateQA(data: QAPair) {
  return request({
    url: '/api/industrial/qa',
    method: 'put',
    data
  });
}

/** 删除 QA */
export function deleteQA(id: number) {
  return request({
    url: `/api/industrial/qa/${id}`,
    method: 'delete'
  });
}

/** 批量导入 QA（CSV） */
export function importQA(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/api/industrial/qa/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

/** 导出 QA */
export function exportQA(query: QAQuery) {
  return request({
    url: '/api/industrial/qa/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  });
}

// ==================== 设备参数接口 ====================

/** 查询设备列表 */
export function listEquipment(params?: { keyword?: string; equipmentType?: string }) {
  return request({
    url: '/api/industrial/equipment/list',
    method: 'get',
    params
  });
}

/** 查询设备详情 */
export function getEquipment(id: number) {
  return request({
    url: `/api/industrial/equipment/${id}`,
    method: 'get'
  });
}

/** 查询设备参数列表 */
export function listEquipmentParams(equipmentId: number) {
  return request({
    url: `/api/industrial/equipment/${equipmentId}/params`,
    method: 'get'
  });
}

/** 更新设备参数 */
export function updateEquipmentParam(data: EquipmentParam) {
  return request({
    url: '/api/industrial/equipment/param',
    method: 'put',
    data
  });
}

// ==================== 故障代码接口 ====================

/** 查询故障代码列表 */
export function listFaultCode(query: FaultCodeQuery) {
  return request({
    url: '/api/industrial/fault-code/list',
    method: 'get',
    params: query
  });
}

/** 查询故障代码详情 */
export function getFaultCode(id: number) {
  return request({
    url: `/api/industrial/fault-code/${id}`,
    method: 'get'
  });
}

/** 新增故障代码 */
export function addFaultCode(data: FaultCode) {
  return request({
    url: '/api/industrial/fault-code',
    method: 'post',
    data
  });
}

/** 修改故障代码 */
export function updateFaultCode(data: FaultCode) {
  return request({
    url: '/api/industrial/fault-code',
    method: 'put',
    data
  });
}

/** 删除故障代码 */
export function deleteFaultCode(id: number) {
  return request({
    url: `/api/industrial/fault-code/${id}`,
    method: 'delete'
  });
}

// ==================== 对话接口 ====================

/** 发送对话消息（SSE 流式响应） */
export function chatStream(data: ChatRequest, onMessage: (text: string) => void, onDone: () => void, onError: (err: Error) => void) {
  const baseURL = import.meta.env.VITE_APP_BASE_API || '';
  const token = localStorage.getItem('token') || '';

  fetch(`${baseURL}/api/industrial/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(data)
  })
    .then(response => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const reader = response.body?.getReader();
      if (!reader) throw new Error('No readable stream');
      const decoder = new TextDecoder('utf-8');

      function read() {
        reader!.read().then(({ done, value }) => {
          if (done) {
            onDone();
            return;
          }
          const chunk = decoder.decode(value, { stream: true });
          // 解析 SSE 格式
          const lines = chunk.split('\n');
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const payload = line.slice(5).trim();
              if (payload === '[DONE]') {
                onDone();
                return;
              }
              try {
                const json = JSON.parse(payload);
                onMessage(json.content || json.text || payload);
              } catch {
                onMessage(payload);
              }
            }
          }
          read();
        }).catch(onError);
      }
      read();
    })
    .catch(onError);
}

/** 查询对话历史 */
export function getChatHistory(sessionId: string) {
  return request({
    url: `/api/industrial/chat/history/${sessionId}`,
    method: 'get'
  });
}

/** 查询对话会话列表 */
export function listChatSessions() {
  return request({
    url: '/api/industrial/chat/sessions',
    method: 'get'
  });
}
