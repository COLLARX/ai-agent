export function buildChatRequestUrl(baseUrl, endpoint, message) {
  const base = String(baseUrl || '').replace(/\/$/, '')
  const url = new URL(base + endpoint)
  url.searchParams.set('message', message)
  if (endpoint.includes('/love_app/')) {
    url.searchParams.set('chatId', 'love-demo')
  }
  return url.toString()
}

