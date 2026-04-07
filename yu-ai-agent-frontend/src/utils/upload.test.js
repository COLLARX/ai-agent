import test from 'node:test'
import assert from 'node:assert/strict'
import { buildUploadUrl, uploadMarkdownFile, validateMarkdownFile } from './upload.js'

test('buildUploadUrl should normalize trailing slash', () => {
  const url = buildUploadUrl('http://localhost:8523/api/')
  assert.equal(url, 'http://localhost:8523/api/rag/upload-md')
})

test('validateMarkdownFile should reject non-md file', () => {
  const error = validateMarkdownFile({ name: 'note.txt', size: 10 })
  assert.equal(error, '仅支持 .md 文件')
})

test('validateMarkdownFile should reject empty file', () => {
  const error = validateMarkdownFile({ name: 'a.md', size: 0 })
  assert.equal(error, '文件不能为空')
})

test('validateMarkdownFile should pass valid markdown file', () => {
  const error = validateMarkdownFile({ name: 'knowledge.md', size: 5 })
  assert.equal(error, '')
})

test('uploadMarkdownFile should send bearer auth with the markdown upload', async () => {
  const originalFetch = globalThis.fetch
  let capturedBody = null
  let capturedHeaders = null

  globalThis.fetch = async (_url, options) => {
    capturedBody = options.body
    capturedHeaders = options.headers
    return {
      ok: true,
      status: 200,
      json: async () => ({ docId: 'doc-1' })
    }
  }

  try {
    const file = { name: 'knowledge.md', size: 12 }
    const result = await uploadMarkdownFile('http://localhost:8523/api/', file, 'jwt-token')
    assert.equal(result.docId, 'doc-1')
    assert.equal(capturedBody.get('userId'), null)
    assert.equal(capturedHeaders.Authorization, 'Bearer jwt-token')
  } finally {
    globalThis.fetch = originalFetch
  }
})
