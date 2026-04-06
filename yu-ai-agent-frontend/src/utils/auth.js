export const AUTH_TOKEN_KEY = 'auth_token'
export const AUTH_USER_KEY = 'auth_user'

function normalizeBaseUrl(baseUrl) {
  return String(baseUrl || '').replace(/\/$/, '')
}

function readStorage(key) {
  return globalThis.localStorage?.getItem(key) ?? ''
}

function writeStorage(key, value) {
  globalThis.localStorage?.setItem(key, value)
}

export function readAuthToken() {
  return readStorage(AUTH_TOKEN_KEY)
}

export function readAuthUser() {
  const raw = readStorage(AUTH_USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function storeAuthSession(token, userInfo) {
  writeStorage(AUTH_TOKEN_KEY, token || '')
  writeStorage(AUTH_USER_KEY, JSON.stringify(userInfo || null))
}

export function clearAuthSession() {
  globalThis.localStorage?.removeItem(AUTH_TOKEN_KEY)
  globalThis.localStorage?.removeItem(AUTH_USER_KEY)
}

export function buildAuthHeaders(token) {
  if (!token) {
    return {}
  }
  return {
    Authorization: `Bearer ${token}`
  }
}

async function postAuth(baseUrl, path, username, password) {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || `认证失败(${response.status})`)
  }
  storeAuthSession(payload.token, payload.userInfo)
  return payload
}

export function registerWithPassword(baseUrl, username, password) {
  return postAuth(baseUrl, '/auth/register', username, password)
}

export function loginWithPassword(baseUrl, username, password) {
  return postAuth(baseUrl, '/auth/login', username, password)
}

export async function fetchCurrentUser(baseUrl, token = readAuthToken()) {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}/auth/me`, {
    method: 'GET',
    headers: {
      ...buildAuthHeaders(token)
    }
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || `认证失败(${response.status})`)
  }
  return payload
}
