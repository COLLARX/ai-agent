<template>
  <AuthPage v-if="!authenticated" :base-url="baseUrl" @authenticated="handleAuthenticated" />

  <div v-else class="app-shell">
    <header class="topbar glass-panel">
      <div class="topbar-header">
        <h1 class="gradient-text">LoLo AI Agent</h1>
        <p>欢迎探索，{{ currentUser?.username || '已登录用户' }}</p>
      </div>
      <div class="topbar-actions">
        <div class="api-input-wrap">
          <label>API URL</label>
          <input v-model.trim="baseUrl" placeholder="http://localhost:8523/api" class="api-input" />
        </div>
        <button type="button" class="logout-btn" @click="handleLogout">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="vertical-align: middle; margin-right: 4px;">
            <path d="M15 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H15M10 17L15 12M15 12L10 7M15 12H3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          退出
        </button>
      </div>
    </header>

    <UploadKnowledgePanel :base-url="baseUrl" :token="token" />

    <nav class="tabbar">
      <button
        v-for="item in tabs"
        :key="item.key"
        class="tab"
        :class="{ active: activeTab === item.key }"
        @click="activeTab = item.key"
      >
        {{ item.label }}
      </button>
    </nav>

    <main class="content">
      <ChatPanel
        v-if="activeTab === 'love'"
        title="恋爱助手 - 为你的感情出谋划策"
        :endpoint="'/ai/love_app/chat/sse'"
        :base-url="baseUrl"
        :token="token"
        :placeholder="'输入你的恋爱问题，比如：如何缓解异地恋焦虑？'"
      />
      <ChatPanel
        v-else
        title="LoLoManus - 你的全能智能中枢"
        :endpoint="'/ai/manus/chat'"
        :base-url="baseUrl"
        :token="token"
        :placeholder="'输入任务，例如：请给我一份学习计划。'"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AuthPage from './components/AuthPage.vue'
import ChatPanel from './components/ChatPanel.vue'
import UploadKnowledgePanel from './components/UploadKnowledgePanel.vue'
import { clearAuthSession, fetchCurrentUser, readAuthToken, readAuthUser, storeAuthSession } from './utils/auth'

const tabs = [
  { key: 'love', label: '恋爱助手' },
  { key: 'manus', label: 'LoLoManus' }
]

const activeTab = ref('love')
const baseUrl = ref('http://localhost:8523/api')
const token = ref(readAuthToken())
const currentUser = ref(readAuthUser())
const authenticated = computed(() => Boolean(token.value))

onMounted(async () => {
  if (!token.value) {
    return
  }
  try {
    currentUser.value = await fetchCurrentUser(baseUrl.value, token.value)
    storeAuthSession(token.value, currentUser.value)
  } catch {
    handleLogout()
  }
})

function handleAuthenticated(payload) {
  token.value = payload?.token || readAuthToken()
  currentUser.value = payload?.userInfo || readAuthUser()
}

function handleLogout() {
  clearAuthSession()
  token.value = ''
  currentUser.value = null
  activeTab.value = 'love'
}
</script>
