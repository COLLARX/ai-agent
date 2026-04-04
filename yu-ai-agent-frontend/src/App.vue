<template>
  <div class="app-shell">
    <header class="topbar">
      <div>
        <h1>Yu AI Agent</h1>
        <p>简洁实用版前端</p>
      </div>
      <label class="base-url">
        API Base URL
        <input v-model.trim="baseUrl" placeholder="http://localhost:8523/api" />
      </label>
    </header>

    <UploadKnowledgePanel :base-url="baseUrl" />

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
        :placeholder="'输入你的恋爱问题，比如：如何缓解异地恋焦虑？'"
      />
      <ChatPanel
        v-else
        title="LoLoManus"
        :endpoint="'/ai/manus/chat'"
        :base-url="baseUrl"
        :placeholder="'输入任务，例如：请给我一份学习计划。'"
      />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ChatPanel from './components/ChatPanel.vue'
import UploadKnowledgePanel from './components/UploadKnowledgePanel.vue'

const tabs = [
  { key: 'love', label: '恋爱助手' },
  { key: 'manus', label: 'LoLoManus' }
]

const activeTab = ref('love')
const baseUrl = ref('http://localhost:8523/api')
</script>
