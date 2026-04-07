import test from 'node:test'
import assert from 'node:assert/strict'
import {
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  buildAuthHeaders,
  clearAuthSession,
  loginWithPassword,
  readAuthToken,
  readAuthUser,
  registerWithPassword,
  storeAuthSession,
  fetchCurrentUser
} from './auth.js'

function mockStorage() {
  const store = new Map()
  return {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => {
      store.set(key, String(value))
    },
    removeItem: (key) => {
      store.delete(key)
    },
    dump: () => store
  }
}

test('storeAuthSession should persist token and user info', () => {
  const storage = mockStorage()
  const originalLocalStorage = globalThis.localStorage
  globalThis.localStorage = storage

  try {
    storeAuthSession('jwt-token', { id: 'user-1', username: 'alice' })
    assert.equal(readAuthToken(), 'jwt-token')
    assert.deepEqual(readAuthUser(), { id: 'user-1', username: 'alice' })
    assert.equal(storage.dump().get(AUTH_TOKEN_KEY), 'jwt-token')
    assert.match(storage.dump().get(AUTH_USER_KEY), /alice/)
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})

test('clearAuthSession should remove token and user info', () => {
  const storage = mockStorage()
  const originalLocalStorage = globalThis.localStorage
  globalThis.localStorage = storage

  try {
    storeAuthSession('jwt-token', { id: 'user-1', username: 'alice' })
    clearAuthSession()
    assert.equal(readAuthToken(), '')
    assert.equal(readAuthUser(), null)
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})

test('buildAuthHeaders should return bearer token header', () => {
  assert.deepEqual(buildAuthHeaders('jwt-token'), { Authorization: 'Bearer jwt-token' })
  assert.deepEqual(buildAuthHeaders(''), {})
})

test('loginWithPassword should post credentials and persist auth session', async () => {
  const storage = mockStorage()
  const originalLocalStorage = globalThis.localStorage
  const originalFetch = globalThis.fetch
  let capturedRequest = null
  globalThis.localStorage = storage
  globalThis.fetch = async (url, options) => {
    capturedRequest = { url, options }
    return {
      ok: true,
      status: 200,
      json: async () => ({
        token: 'jwt-login',
        userInfo: { id: 'user-2', username: 'bob' }
      })
    }
  }

  try {
    const payload = await loginWithPassword('http://localhost:8523/api', 'bob', 'secret123')
    assert.equal(capturedRequest.url, 'http://localhost:8523/api/auth/login')
    assert.equal(capturedRequest.options.method, 'POST')
    assert.match(String(capturedRequest.options.body), /bob/)
    assert.equal(payload.token, 'jwt-login')
    assert.equal(readAuthToken(), 'jwt-login')
    assert.deepEqual(readAuthUser(), { id: 'user-2', username: 'bob' })
  } finally {
    globalThis.localStorage = originalLocalStorage
    globalThis.fetch = originalFetch
  }
})

test('registerWithPassword should post credentials and persist auth session', async () => {
  const storage = mockStorage()
  const originalLocalStorage = globalThis.localStorage
  const originalFetch = globalThis.fetch
  globalThis.localStorage = storage
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({
      token: 'jwt-register',
      userInfo: { id: 'user-3', username: 'cindy' }
    })
  })

  try {
    const payload = await registerWithPassword('http://localhost:8523/api', 'cindy', 'secret123')
    assert.equal(payload.token, 'jwt-register')
    assert.equal(readAuthToken(), 'jwt-register')
  } finally {
    globalThis.localStorage = originalLocalStorage
    globalThis.fetch = originalFetch
  }
})

test('fetchCurrentUser should call /auth/me with bearer token', async () => {
  const originalFetch = globalThis.fetch
  let capturedRequest = null
  globalThis.fetch = async (url, options) => {
    capturedRequest = { url, options }
    return {
      ok: true,
      status: 200,
      json: async () => ({ id: 'user-1', username: 'alice' })
    }
  }

  try {
    const payload = await fetchCurrentUser('http://localhost:8523/api', 'jwt-token')
    assert.equal(capturedRequest.url, 'http://localhost:8523/api/auth/me')
    assert.equal(capturedRequest.options.headers.Authorization, 'Bearer jwt-token')
    assert.deepEqual(payload, { id: 'user-1', username: 'alice' })
  } finally {
    globalThis.fetch = originalFetch
  }
})
