const ANONYMOUS_USER_ID_KEY = 'yu-ai-agent.anonymousUserId'

export { ANONYMOUS_USER_ID_KEY }

export function getAnonymousUserId() {
  const storedUserId = readStoredAnonymousUserId()
  if (storedUserId) {
    return storedUserId
  }

  const userId = createAnonymousUserId()
  writeStoredAnonymousUserId(userId)
  return userId
}

function readStoredAnonymousUserId() {
  try {
    return globalThis.localStorage?.getItem(ANONYMOUS_USER_ID_KEY) || ''
  } catch (error) {
    return ''
  }
}

function writeStoredAnonymousUserId(userId) {
  try {
    globalThis.localStorage?.setItem(ANONYMOUS_USER_ID_KEY, userId)
  } catch (error) {
    // Ignore storage failures so the app still works in restrictive contexts.
  }
}

function createAnonymousUserId() {
  if (globalThis.crypto?.randomUUID) {
    return `anon-${globalThis.crypto.randomUUID()}`
  }

  const timePart = Date.now().toString(36)
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `anon-${timePart}-${randomPart}`
}
