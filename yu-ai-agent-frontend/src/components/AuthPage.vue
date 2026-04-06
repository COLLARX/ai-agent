<template>
  <section class="auth-shell">
    <div class="auth-card">
      <div class="auth-header">
        <h1>Yu AI Agent</h1>
        <p>登录后再进入恋爱助手和 LoLoManus</p>
      </div>

      <div class="auth-tabs">
        <button type="button" class="auth-tab" :class="{ active: mode === 'login' }" @click="mode = 'login'">
          登录
        </button>
        <button
          type="button"
          class="auth-tab"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label>
          用户名
          <input v-model.trim="username" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label>
          密码
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <p v-if="errorText" class="auth-error">{{ errorText }}</p>

        <button type="submit" :disabled="loading || !username || !password">
          {{ loading ? '提交中...' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { loginWithPassword, registerWithPassword } from '../utils/auth'

const props = defineProps({
  baseUrl: { type: String, required: true }
})

const emit = defineEmits(['authenticated'])

const mode = ref('login')
const username = ref('')
const password = ref('')
const loading = ref(false)
const errorText = ref('')

async function handleSubmit() {
  if (!username.value || !password.value || loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const action = mode.value === 'login' ? loginWithPassword : registerWithPassword
    const payload = await action(props.baseUrl, username.value, password.value)
    emit('authenticated', payload)
  } catch (error) {
    errorText.value = error.message || '认证失败'
  } finally {
    loading.value = false
  }
}
</script>
