<template>
  <div class="industrial-chat">
    <div class="chat-container">
      <!-- 侧边栏：快捷分类 -->
      <div class="chat-sidebar">
        <div class="sidebar-title">快捷分类</div>
        <div
          v-for="cat in quickCategories"
          :key="cat.value"
          class="sidebar-item"
          :class="{ active: selectedCategory === cat.value }"
          @click="handleCategorySelect(cat.value)"
        >
          <span class="sidebar-icon">{{ cat.icon }}</span>
          <span>{{ cat.label }}</span>
        </div>
        <el-divider />
        <div class="sidebar-title">历史会话</div>
        <div
          v-for="session in sessions"
          :key="session.id"
          class="sidebar-item session-item"
          :class="{ active: currentSessionId === session.id }"
          @click="handleSessionSelect(session)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span class="session-title">{{ session.title }}</span>
        </div>
        <div class="sidebar-footer">
          <el-button type="primary" plain size="small" style="width: 100%" @click="handleNewChat">
            <el-icon><Plus /></el-icon>新建对话
          </el-button>
        </div>
      </div>

      <!-- 主对话区 -->
      <div class="chat-main">
        <!-- 消息列表 -->
        <div ref="messageListRef" class="message-list">
          <div v-if="messages.length === 0" class="welcome">
            <div class="welcome-icon">🏭</div>
            <h2>工业知识助手</h2>
            <p>基于企业知识库的智能问答系统，支持设备故障、工艺参数、SOP 等工业场景</p>
            <div class="welcome-prompts">
              <div
                v-for="prompt in welcomePrompts"
                :key="prompt"
                class="prompt-card"
                @click="handleQuickAsk(prompt)"
              >
                {{ prompt }}
              </div>
            </div>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🏭</span>
            </div>
            <div class="message-content">
              <div class="message-text" v-html="formatMessage(msg.content)" />
              <!-- 引用溯源 -->
              <div v-if="msg.role === 'assistant' && msg.citations?.length" class="citations">
                <div class="citations-title">📎 引用来源</div>
                <div
                  v-for="cite in msg.citations"
                  :key="cite.id"
                  class="citation-item"
                  @click="handleCitationClick(cite)"
                >
                  <span class="citation-index">[{{ cite.id }}]</span>
                  <span class="citation-title">{{ cite.title }}</span>
                  <span class="citation-source">来源：{{ cite.source }}</span>
                  <el-tag size="small" type="info" style="margin-left: 8px">
                    相关度 {{ (cite.relevance * 100).toFixed(0) }}%
                  </el-tag>
                </div>
              </div>
            </div>
          </div>

          <!-- 流式输出中 -->
          <div v-if="streaming" class="message-item assistant">
            <div class="message-avatar">🏭</div>
            <div class="message-content">
              <div class="message-text" v-html="formatMessage(streamingText)" />
              <span class="typing-cursor">▌</span>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="input-toolbar">
            <el-tag
              v-if="selectedCategory"
              closable
              size="small"
              type="primary"
              @close="selectedCategory = ''"
            >
              {{ getCategoryLabel(selectedCategory) }}
            </el-tag>
          </div>
          <div class="input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="输入工业知识问题，按 Enter 发送，Shift+Enter 换行..."
              resize="none"
              @keydown="handleKeydown"
            />
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="streaming"
              :disabled="!inputText.trim()"
              circle
              size="large"
              class="send-btn"
              @click="handleSend"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 引用详情对话框 -->
    <el-dialog
      v-model="citationDialogVisible"
      title="引用原文"
      width="600px"
    >
      <div v-if="currentCitation" class="citation-detail">
        <h4>{{ currentCitation.title }}</h4>
        <el-tag size="small" type="info" style="margin-bottom: 12px">
          来源：{{ currentCitation.source }}
        </el-tag>
        <div class="citation-content">{{ currentCitation.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue';
import { Promotion, Plus, ChatDotRound } from '@element-plus/icons-vue';
import { chatStream, getChatHistory, listChatSessions, type ChatMessage, type Citation } from '@/api/industrial';

// 快捷分类
const quickCategories = [
  { label: '设备故障', value: 'equipment_fault', icon: '🔧' },
  { label: '工艺参数', value: 'process_params', icon: '📊' },
  { label: '操作规范', value: 'operation_spec', icon: '📋' },
  { label: '安全规程', value: 'safety_regulation', icon: '⚠️' },
  { label: '维护保养', value: 'maintenance', icon: '🛠️' }
];

const categoryMap: Record<string, string> = {
  equipment_fault: '设备故障',
  process_params: '工艺参数',
  operation_spec: '操作规范',
  safety_regulation: '安全规程',
  maintenance: '维护保养'
};

const getCategoryLabel = (val: string) => categoryMap[val] || val;

// 欢迎提示
const welcomePrompts = [
  '注塑机温度异常报警如何排查？',
  'CNC 加工中心主轴维护保养规范是什么？',
  '液压系统压力不足的常见原因有哪些？',
  'PLC 控制器通讯故障怎么处理？'
];

// 会话管理
interface Session {
  id: string;
  title: string;
}

const sessions = ref<Session[]>([]);
const currentSessionId = ref('');

const loadSessions = async () => {
  try {
    const res: any = await listChatSessions();
    sessions.value = res.data || [];
  } catch {
    sessions.value = [];
  }
};

const handleNewChat = () => {
  currentSessionId.value = '';
  messages.length = 0;
  selectedCategory.value = '';
};

const handleSessionSelect = async (session: Session) => {
  currentSessionId.value = session.id;
  messages.length = 0;
  try {
    const res: any = await getChatHistory(session.id);
    const history: ChatMessage[] = res.data || [];
    messages.push(...history);
    scrollToBottom();
  } catch {
    // handled
  }
};

// 对话状态
const selectedCategory = ref('');
const inputText = ref('');
const messages = reactive<ChatMessage[]>([]);
const streaming = ref(false);
const streamingText = ref('');
const messageListRef = ref<HTMLElement>();

const handleCategorySelect = (val: string) => {
  selectedCategory.value = selectedCategory.value === val ? '' : val;
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
    }
  });
};

const formatMessage = (text: string) => {
  if (!text) return '';
  // 简单 markdown 转换
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br/>');
};

const handleSend = () => {
  const text = inputText.value.trim();
  if (!text || streaming.value) return;

  // 添加用户消息
  messages.push({
    role: 'user',
    content: text,
    timestamp: new Date().toISOString()
  });
  inputText.value = '';
  scrollToBottom();

  // 开始流式请求
  streaming.value = true;
  streamingText.value = '';

  chatStream(
    {
      message: text,
      sessionId: currentSessionId.value || undefined,
      category: selectedCategory.value || undefined
    },
    // onMessage
    (chunk: string) => {
      streamingText.value += chunk;
      scrollToBottom();
    },
    // onDone
    () => {
      messages.push({
        role: 'assistant',
        content: streamingText.value,
        timestamp: new Date().toISOString()
      });
      streamingText.value = '';
      streaming.value = false;
      scrollToBottom();
      loadSessions();
    },
    // onError
    (err: Error) => {
      console.error('Chat stream error:', err);
      messages.push({
        role: 'assistant',
        content: `抱歉，请求出错：${err.message}。请稍后重试。`,
        timestamp: new Date().toISOString()
      });
      streamingText.value = '';
      streaming.value = false;
      scrollToBottom();
    }
  );
};

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
};

const handleQuickAsk = (prompt: string) => {
  inputText.value = prompt;
  handleSend();
};

// 引用详情
const citationDialogVisible = ref(false);
const currentCitation = ref<Citation | null>(null);

const handleCitationClick = (cite: Citation) => {
  currentCitation.value = cite;
  citationDialogVisible.value = true;
};

onMounted(() => {
  loadSessions();
});
</script>

<style scoped>
.industrial-chat {
  height: calc(100vh - 84px);
  padding: 16px;
  background: #0d1117;
}

.chat-container {
  display: flex;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
}

/* 侧边栏 */
.chat-sidebar {
  width: 240px;
  background: #161b22;
  border-right: 1px solid #30363d;
  display: flex;
  flex-direction: column;
  padding: 16px 0;
  overflow-y: auto;
}

.sidebar-title {
  padding: 0 16px 8px;
  font-size: 12px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  color: #c9d1d9;
  font-size: 14px;
  transition: all 0.2s;
}

.sidebar-item:hover {
  background: #1f2937;
}

.sidebar-item.active {
  background: #1f6feb22;
  color: #58a6ff;
  border-right: 2px solid #1f6feb;
}

.sidebar-icon {
  font-size: 16px;
}

.session-item {
  font-size: 13px;
  padding: 8px 16px;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-footer {
  margin-top: auto;
  padding: 16px;
}

/* 主对话区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #0d1117;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* 欢迎页 */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c9d1d9;
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.welcome h2 {
  margin: 0 0 8px;
  color: #e6edf3;
  font-size: 24px;
}

.welcome p {
  color: #8b949e;
  margin: 0 0 32px;
  text-align: center;
  max-width: 480px;
}

.welcome-prompts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 560px;
  width: 100%;
}

.prompt-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #c9d1d9;
  transition: all 0.2s;
  line-height: 1.5;
}

.prompt-card:hover {
  border-color: #1f6feb;
  background: #1f6feb11;
  color: #58a6ff;
}

/* 消息 */
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  max-width: 800px;
}

.message-item.user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background: #1f6feb;
}

.message-item.assistant .message-avatar {
  background: #238636;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-text {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 12px 16px;
  color: #e6edf3;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #1f6feb;
  border-color: #1f6feb;
  color: #fff;
}

.message-text :deep(code) {
  background: #0d1117;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #f0883e;
}

.typing-cursor {
  animation: blink 0.8s infinite;
  color: #58a6ff;
  font-size: 16px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 引用 */
.citations {
  margin-top: 12px;
  padding: 10px 14px;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 8px;
}

.citations-title {
  font-size: 12px;
  color: #8b949e;
  margin-bottom: 8px;
}

.citation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 13px;
}

.citation-item:hover {
  background: #161b22;
}

.citation-index {
  color: #1f6feb;
  font-weight: 700;
  font-size: 12px;
}

.citation-title {
  color: #58a6ff;
  flex: 1;
}

.citation-source {
  color: #8b949e;
  font-size: 12px;
}

/* 引用详情 */
.citation-detail h4 {
  margin: 0 0 8px;
  color: #e6edf3;
}

.citation-content {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
  padding: 16px;
  color: #c9d1d9;
  line-height: 1.8;
  white-space: pre-wrap;
  max-height: 400px;
  overflow-y: auto;
}

/* 输入区 */
.input-area {
  padding: 16px 24px 20px;
  border-top: 1px solid #30363d;
  background: #0d1117;
}

.input-toolbar {
  margin-bottom: 8px;
  min-height: 24px;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  background: #161b22;
  border: 1px solid #30363d;
  color: #e6edf3;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
}

.input-row :deep(.el-textarea__inner):focus {
  border-color: #1f6feb;
  box-shadow: 0 0 0 2px #1f6feb33;
}

.send-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
}

/* 滚动条 */
.message-list::-webkit-scrollbar,
.chat-sidebar::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-thumb,
.chat-sidebar::-webkit-scrollbar-thumb {
  background: #30363d;
  border-radius: 3px;
}

.message-list::-webkit-scrollbar-track,
.chat-sidebar::-webkit-scrollbar-track {
  background: transparent;
}
</style>
