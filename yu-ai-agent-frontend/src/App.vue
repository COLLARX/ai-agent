<template>
  <AuthPage v-if="!authenticated" :base-url="baseUrl" @authenticated="handleAuthenticated" />

  <div v-else class="app-shell">
    <header class="topbar">
      <div>
        <h1>Yu AI Agent</h1>
        <p>欢迎，{{ currentUser?.username || '已登录用户' }}</p>
      </div>
      <div class="topbar-actions">
        <label class="base-url">
          API Base URL
          <input v-model.trim="baseUrl" placeholder="http://localhost:8523/api" />
        </label>
        <button type="button" class="logout-btn" @click="handleLogout">退出登录</button>
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
        title="恋爱助手"
        :endpoint="'/ai/love_app/chat/sse'"
        :base-url="baseUrl"
        :token="token"
        :placeholder="'输入你的恋爱问题，比如：如何缓解异地恋焦虑？'"
      />
      <ChatPanel
        v-else
        title="LoLoManus"
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
