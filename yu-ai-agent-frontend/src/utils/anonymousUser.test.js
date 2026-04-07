import test from 'node:test'
import assert from 'node:assert/strict'
import { getAnonymousUserId, ANONYMOUS_USER_ID_KEY } from './anonymousUser.js'

test('getAnonymousUserId should create and persist a new id', () => {
  const store = new Map()
  const originalLocalStorage = globalThis.localStorage

  globalThis.localStorage = {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => {
      store.set(key, String(value))
    }
  }

  try {
    const userId = getAnonymousUserId()
    assert.match(userId, /^anon-/)
    assert.equal(store.get(ANONYMOUS_USER_ID_KEY), userId)
    assert.equal(getAnonymousUserId(), userId)
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})

test('getAnonymousUserId should reuse an existing stored id', () => {
  const originalLocalStorage = globalThis.localStorage

  globalThis.localStorage = {
    getItem: (key) => (key === ANONYMOUS_USER_ID_KEY ? 'anon-existing-user' : null),
    setItem: () => {
      throw new Error('should not store a new id')
    }
  }

  try {
    assert.equal(getAnonymousUserId(), 'anon-existing-user')
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})
