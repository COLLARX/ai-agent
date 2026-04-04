export function appendStreamChunk(current, chunk) {
  if (!chunk) return current || ''
  return `${current || ''}${chunk}`
}

