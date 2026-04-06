export function buildChatRequestUrl(baseUrl, endpoint, message, chatId, userId) {
  const base = String(baseUrl || '').replace(/\/$/, '')
  const url = new URL(base + endpoint)
  url.searchParams.set('message', message)
  if (chatId) {
    url.searchParams.set('chatId', chatId)
  }
  if (userId && !endpoint.includes('/love_app/')) {
    url.searchParams.set('userId', userId)
  }
  return url.toString()
}
