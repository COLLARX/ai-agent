import test from 'node:test'
import assert from 'node:assert/strict'
import { buildChatRequestUrl } from './chat.js'

test('buildChatRequestUrl should append message for manus endpoint', () => {
  const url = buildChatRequestUrl('http://localhost:8523/api', '/ai/manus/chat', 'hello')
  const parsed = new URL(url)
  assert.equal(parsed.pathname, '/api/ai/manus/chat')
  assert.equal(parsed.searchParams.get('message'), 'hello')
  assert.equal(parsed.searchParams.get('chatId'), null)
})

test('buildChatRequestUrl should append chatId for love endpoint', () => {
  const url = buildChatRequestUrl('http://localhost:8523/api/', '/ai/love_app/chat/sse', 'hi')
  const parsed = new URL(url)
  assert.equal(parsed.pathname, '/api/ai/love_app/chat/sse')
  assert.equal(parsed.searchParams.get('message'), 'hi')
  assert.equal(parsed.searchParams.get('chatId'), 'love-demo')
})

