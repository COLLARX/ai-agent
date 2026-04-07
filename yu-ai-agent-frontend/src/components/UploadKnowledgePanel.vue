<template>
  <section class="upload-panel glass-panel">
    <div class="upload-header">
      <div class="title-wrap">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15M17 8L12 3M12 3L7 8M12 3V15" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <h2>上传知识库 (Markdown)</h2>
      </div>
      <span class="upload-status" :class="statusClass">{{ statusText }}</span>
    </div>
    
    <div class="upload-area">
      <div class="upload-box" :class="{ 'has-file': selectedFile }">
        <input 
          type="file" 
          accept=".md,text/markdown" 
          @change="handleFileChange" 
          class="file-input"
          :disabled="uploading"
        />
        <div class="upload-placeholder">
          <svg v-if="!selectedFile" width="32" height="32" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M13 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V9L13 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M9 15L11 17L15 13" stroke="var(--primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <p>{{ selectedFile ? selectedFile.name : '点击或拖拽 Markdown 文件至此' }}</p>
        </div>
      </div>
      
      <div class="action-wrap">
        <p class="upload-note">注: 知识库仅增强 <strong>LoLoManus</strong></p>
        <button class="upload-btn" :disabled="uploading || !selectedFile" @click="handleUpload">
          <span v-if="uploading" class="loading-spinner"></span>
          {{ uploading ? '向量化处理中...' : '上传并向量化' }}
        </button>
      </div>
    </div>

    <!-- Error state -->
    <div v-if="errorText" class="alert-box error slide-in">
      <p>{{ errorText }}</p>
    </div>

    <!-- Success state -->
    <div v-if="result" class="alert-box success slide-in">
      <div class="result-grid">
        <div class="result-item"><span>文档ID:</span> <strong>{{ result.docId }}</strong></div>
        <div class="result-item"><span>分块数:</span> <strong>{{ result.chunks }}</strong></div>
        <div class="result-item full"><span>结果:</span> <strong>{{ result.message }}</strong></div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { uploadMarkdownFile, validateMarkdownFile } from '../utils/upload'

const props = defineProps({
  baseUrl: { type: String, required: true },
  token: { type: String, default: '' }
})

const selectedFile = ref(null)
const uploading = ref(false)
const statusText = ref('等待文件')
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
    statusText.value = '等待文件'
    return
  }
  const message = validateMarkdownFile(file)
  if (message) {
    errorText.value = message
    statusText.value = '文件异常'
    selectedFile.value = null
  } else {
    statusText.value = '可以上传'
  }
}

async function handleUpload() {
  if (!selectedFile.value || uploading.value) return
  uploading.value = true
  errorText.value = ''
  statusText.value = '处理中...'
  result.value = null
  try {
    const data = await uploadMarkdownFile(props.baseUrl, selectedFile.value, props.token)
    result.value = data
    statusText.value = '成功'
  } catch (e) {
    errorText.value = e.message || '上传失败'
    statusText.value = '失败'
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-panel {
  padding: 20px 24px;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--primary);
}

.title-wrap h2 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--text-main);
}

.upload-status {
  font-size: 0.8rem;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(255,255,255,0.05);
}

.upload-status.ok { color: var(--success); background: rgba(0, 184, 148, 0.1); }
.upload-status.running { color: var(--warning); background: rgba(253, 203, 110, 0.1); }
.upload-status.error { color: var(--danger); background: rgba(255, 118, 117, 0.1); }

.upload-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-box {
  position: relative;
  background: rgba(0,0,0,0.2);
  border: 2px dashed rgba(255,255,255,0.15);
  border-radius: 16px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.upload-box:hover {
  border-color: rgba(78, 204, 163, 0.5);
  background: rgba(78, 204, 163, 0.05);
}

.upload-box.has-file {
  border-color: rgba(108, 92, 231, 0.6);
  background: rgba(108, 92, 231, 0.1);
}

.file-input {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  opacity: 0;
  cursor: pointer;
  z-index: 2;
}

.file-input:disabled {
  cursor: not-allowed;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  pointer-events: none;
}

.upload-placeholder p {
  font-size: 0.95rem;
  font-weight: 500;
  margin: 0;
}

.action-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.upload-note {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.upload-btn {
  padding: 10px 24px;
  border-radius: 12px;
  background: var(--gradient-accent);
  color: white;
  border: none;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 15px rgba(78, 204, 163, 0.3);
}

.upload-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(78, 204, 163, 0.5);
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.alert-box {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  font-size: 0.9rem;
}

.alert-box.error {
  background: rgba(255, 118, 117, 0.1);
  border: 1px solid rgba(255, 118, 117, 0.3);
  color: var(--danger);
}

.alert-box.success {
  background: rgba(0, 184, 148, 0.1);
  border: 1px solid rgba(0, 184, 148, 0.3);
  color: var(--success);
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.result-item.full {
  grid-column: 1 / -1;
}

.result-item span {
  color: var(--text-muted);
  margin-right: 6px;
}

.result-item strong {
  color: var(--text-main);
}

.slide-in {
  animation: slideUp 0.4s ease-out;
}

@media (max-width: 600px) {
  .result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
