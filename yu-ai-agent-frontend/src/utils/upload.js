export function buildUploadUrl(baseUrl) {
  const base = String(baseUrl || '').replace(/\/$/, '')
  return `${base}/rag/upload-md`
}

export function validateMarkdownFile(file) {
  if (!file || file.size <= 0) {
    return '文件不能为空'
  }
  const name = (file.name || '').toLowerCase()
  if (!name.endsWith('.md')) {
    return '仅支持 .md 文件'
  }
  return ''
}

export async function uploadMarkdownFile(baseUrl, file) {
  const validationError = validateMarkdownFile(file)
  if (validationError) {
    throw new Error(validationError)
  }
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(buildUploadUrl(baseUrl), {
    method: 'POST',
    body: formData
  })
  let payload = {}
  try {
    payload = await response.json()
  } catch (e) {
    payload = {}
  }
  if (!response.ok) {
    const message = payload?.message || payload?.error || `上传失败(${response.status})`
    throw new Error(message)
  }
  return payload
}

