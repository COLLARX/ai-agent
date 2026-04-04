import test from 'node:test'
import assert from 'node:assert/strict'
import { extractDataLines } from './sse.js'

test('extractDataLines should parse complete data lines and keep remainder', () => {
  const input = 'data:Step 1\n\n' + 'data:Step 2\n' + 'da'
  const { events, rest } = extractDataLines(input)
  assert.deepEqual(events, ['Step 1', 'Step 2'])
  assert.equal(rest, 'da')
})

test('extractDataLines should ignore non-data lines', () => {
  const input = 'event: ping\n' + 'data:hello\n' + 'id: 1\n'
  const { events } = extractDataLines(input)
  assert.deepEqual(events, ['hello'])
})
