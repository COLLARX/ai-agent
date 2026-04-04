import test from 'node:test'
import assert from 'node:assert/strict'
import { buildUploadUrl, validateMarkdownFile } from './upload.js'

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

