import test from 'node:test'
import assert from 'node:assert/strict'
import { appendStreamChunk } from './message.js'

test('appendStreamChunk should append stream chunks without inserting line breaks', () => {
  let value = ''
  value = appendStreamChunk(value, '你')
  value = appendStreamChunk(value, '好')
  value = appendStreamChunk(value, '，我是林薇。')
  assert.equal(value, '你好，我是林薇。')
})

test('appendStreamChunk should ignore empty chunks', () => {
  const value = appendStreamChunk('hello', '')
  assert.equal(value, 'hello')
})

