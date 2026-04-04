export function extractDataLines(buffer) {
  const lines = buffer.split(/\r?\n/)
  const rest = lines.pop() || ''
  const events = []

  for (const line of lines) {
    if (!line.startsWith('data:')) continue
    const payload = line.slice(5).trim()
    if (payload) events.push(payload)
  }

  return { events, rest }
}

export async function streamSse(url, { onData, onError, onDone }) {
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream'
    }
  })

  if (!response.ok || !response.body) {
    throw new Error(`Request failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const { events, rest } = extractDataLines(buffer)
      buffer = rest
      for (const payload of events) onData(payload)
    }
    onDone()
  } catch (err) {
    onError(err)
  } finally {
    reader.releaseLock()
  }
}
