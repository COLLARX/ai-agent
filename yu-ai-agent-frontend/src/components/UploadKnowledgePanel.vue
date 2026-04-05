<template>
  <section class="upload-panel">
    <div class="upload-title">
      <h2>知识库上传（Markdown）</h2>
      <span class="upload-status" :class="statusClass">{{ statusText }}</span>
    </div>
    <p class="upload-note">上传知识仅增强 LoLoManus，不影响恋爱助手。</p>
    <div class="upload-row">
      <input type="file" accept=".md,text/markdown" @change="handleFileChange" />
      <button :disabled="uploading || !selectedFile" @click="handleUpload">
        {{ uploading ? '上传中...' : '上传并向量化' }}
      </button>
    </div>
    <p v-if="errorText" class="upload-error">{{ errorText }}</p>
    <div v-if="result" class="upload-result">
      <div><strong>文档ID:</strong> {{ result.docId }}</div>
      <div><strong>文件名:</strong> {{ result.fileName }}</div>
      <div><strong>分块数:</strong> {{ result.chunks }}</div>
      <div><strong>结果:</strong> {{ result.message }}</div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { uploadMarkdownFile, validateMarkdownFile } from '../utils/upload'
import { getAnonymousUserId } from '../utils/anonymousUser'

const props = defineProps({
  baseUrl: { type: String, required: true }
})

const selectedFile = ref(null)
const uploading = ref(false)
const statusText = ref('就绪')
const errorText = ref('')
const result = ref(null)

const statusClass = computed(() => {
  if (uploading.value) return 'running'
  if (errorText.value) return 'error'
  if (result.value) return 'ok'
  return 'idle'
})

function handleFileChange(event) {
  const file = event?.target?.files?.[0] || null
  selectedFile.value = file
  errorText.value = ''
  result.value = null
  if (!file) {
    statusText.value = '就绪'
    return
  }
  const message = validateMarkdownFile(file)
  if (message) {
    errorText.value = message
    statusText.value = '失败'
    selectedFile.value = null
  } else {
    statusText.value = '待上传'
  }
}

async function handleUpload() {
  if (!selectedFile.value || uploading.value) return
  uploading.value = true
  errorText.value = ''
  statusText.value = '上传中'
  result.value = null
  try {
    const data = await uploadMarkdownFile(props.baseUrl, selectedFile.value, getAnonymousUserId())
    result.value = data
    statusText.value = '完成'
  } catch (e) {
    errorText.value = e.message || '上传失败'
    statusText.value = '失败'
  } finally {
    uploading.value = false
  }
}
</script>
