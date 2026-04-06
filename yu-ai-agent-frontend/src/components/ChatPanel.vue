<template>
  <section class="panel">
    <div class="panel-header">
      <h2>{{ title }}</h2>
      <span class="status" :class="statusClass">{{ statusText }}</span>
    </div>

    <div ref="listEl" class="messages">
      <div v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
        <div class="role">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
        <div class="content">{{ msg.content }}</div>
      </div>
    </div>

    <form class="composer" @submit.prevent="handleSend">
      <input v-model.trim="input" :placeholder="placeholder" :disabled="loading" />
      <button type="submit" :disabled="loading || !input">{{ loading ? '发送中...' : '发送' }}</button>
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
const statusText = ref('就绪')
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
  statusText.value = '连接中...'
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
        statusText.value = '响应中...'
        scrollToBottom()
      },
      onError: (err) => {
        statusText.value = `失败: ${err.message || '未知错误'}`
      },
      onDone: () => {
        statusText.value = '完成'
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
