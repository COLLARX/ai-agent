<template>
  <section class="chat-panel glass-panel">
    <div class="panel-header">
      <div class="title-wrap">
        <div class="status-dot" :class="statusClass"></div>
        <h2>{{ title }}</h2>
      </div>
      <span class="status-badge" :class="statusClass">{{ statusText }}</span>
    </div>

    <div ref="listEl" class="messages scroll-area">
      <div v-for="msg in messages" :key="msg.id" class="message-wrapper">
        <div class="message" :class="msg.role">
          <!-- Avatar placeholder -->
          <div class="avatar">{{ msg.role === 'user' ? 'ME' : 'AI' }}</div>
          
          <div class="content-bubble">
            <div class="content">{{ msg.content }}</div>
            <span v-if="loading && msg.role === 'assistant' && msg.content === ''" class="typing-indicator">
              <span></span><span></span><span></span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <form class="composer" @submit.prevent="handleSend">
      <div class="input-wrapper">
        <input 
          v-model.trim="input" 
          :placeholder="placeholder" 
          :disabled="loading" 
          class="chat-input"
        />
        <button type="submit" class="send-btn" :disabled="loading || !input">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22 2L11 13M22 2L15 22L11 13M11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </form>
  </section>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { buildChatRequest } from '../utils/chat'
import { appendStreamChunk } from '../utils/message'
import { streamSse } from '../utils/sse'

const props = defineProps({
  title: { type: String, required: true },
  endpoint: { type: String, required: true },
  baseUrl: { type: String, required: true },
  token: { type: String, default: '' },
  placeholder: { type: String, default: '' }
})

const input = ref('')
const loading = ref(false)
const statusText = ref('在线就绪')
const messages = ref([])
const listEl = ref(null)
const chatSessionId = ref(createSessionId())

const statusClass = computed(() => {
  if (loading.value) return 'running'
  if (statusText.value.includes('失败')) return 'error'
  return 'idle'
})

async function scrollToBottom() {
  await nextTick()
  if (listEl.value) {
    listEl.value.scrollTop = listEl.value.scrollHeight
  }
}

async function handleSend() {
  const text = input.value
  if (!text || loading.value) return

  loading.value = true
  statusText.value = '思考中...'
  messages.value.push({ id: Date.now() + '-u', role: 'user', content: text })
  const aiMsg = { id: Date.now() + '-a', role: 'assistant', content: '' }
  messages.value.push(aiMsg)
  input.value = ''
  await scrollToBottom()

  try {
    const request = buildChatRequest(props.baseUrl, props.endpoint, text, chatSessionId.value, props.token)
    await streamSse(request.url, {
      headers: request.headers,
      onData: (chunk) => {
        aiMsg.content = appendStreamChunk(aiMsg.content, chunk)
        statusText.value = '回复中...'
        scrollToBottom()
      },
      onError: (err) => {
        statusText.value = `失败: ${err.message || '未知错误'}`
      },
      onDone: () => {
        statusText.value = '在线就绪'
      }
    })
  } catch (err) {
    statusText.value = `失败: ${err.message || '未知错误'}`
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function createSessionId() {
  const rand = Math.random().toString(36).slice(2, 10)
  return `session-${Date.now()}-${rand}`
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 580px;
  max-height: 70vh;
  padding: 0;
  overflow: hidden;
}

.panel-header {
  padding: 18px 24px;
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(0,0,0,0.2);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--text-muted);
  box-shadow: 0 0 8px currentColor;
}

.status-dot.idle { color: var(--success); background: var(--success); }
.status-dot.running { color: var(--primary); background: var(--primary); animation: pulse-glow 2s infinite; }
.status-dot.error { color: var(--danger); background: var(--danger); }

.panel-header h2 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
}

.status-badge {
  font-size: 0.8rem;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(255,255,255,0.05);
}

.status-badge.idle { color: var(--success); background: rgba(0, 184, 148, 0.1); }
.status-badge.running { color: var(--primary); background: rgba(78, 204, 163, 0.1); }
.status-badge.error { color: var(--danger); background: rgba(255, 118, 117, 0.1); }

.messages {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-wrapper {
  animation: slideUp 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) both;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message.user {
  flex-direction: row-reverse;
  align-self: flex-end;
  margin-left: auto;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 700;
  flex-shrink: 0;
}

.message.user .avatar {
  background: var(--gradient-accent);
  color: #fff;
}

.message.assistant .avatar {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid var(--glass-border);
  color: var(--primary);
}

.content-bubble {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 0.95rem;
  line-height: 1.6;
  white-space: pre-wrap;
  position: relative;
}

.message.user .content-bubble {
  background: rgba(108, 92, 231, 0.2);
  border: 1px solid rgba(108, 92, 231, 0.5);
  border-top-right-radius: 4px;
}

.message.assistant .content-bubble {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid var(--glass-border);
  border-top-left-radius: 4px;
}

/* Typing Indicator */
.typing-indicator {
  display: inline-flex;
  gap: 4px;
  padding: 4px 6px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

/* Composer Input area */
.composer {
  padding: 20px 24px;
  background: rgba(0,0,0,0.3);
  border-top: 1px solid var(--glass-border);
}

.input-wrapper {
  display: flex;
  background: rgba(0,0,0,0.4);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  padding: 6px 6px 6px 20px;
  transition: all 0.3s ease;
}

.input-wrapper:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 15px rgba(78, 204, 163, 0.15);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--text-main);
  font-size: 1rem;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: var(--gradient-accent);
  color: #fff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(108, 92, 231, 0.4);
}
</style>
