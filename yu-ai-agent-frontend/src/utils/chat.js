import { buildAuthHeaders } from './auth.js'

export function buildChatRequestUrl(baseUrl, endpoint, message, chatId) {
  const base = String(baseUrl || '').replace(/\/$/, '')
  const url = new URL(base + endpoint)
  url.searchParams.set('message', message)
  if (chatId) {
    url.searchParams.set('chatId', chatId)
  }
  return url.toString()
}

export function buildChatRequest(baseUrl, endpoint, message, chatId, token) {
  return {
    url: buildChatRequestUrl(baseUrl, endpoint, message, chatId),
    headers: buildAuthHeaders(token)
  }
}
