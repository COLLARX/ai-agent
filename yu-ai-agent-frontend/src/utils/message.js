export function appendStreamChunk(current, chunk) {
  if (!chunk) return current || ''
  if (!current) return chunk
  const needsNewLine = !current.endsWith('\n') && !chunk.startsWith('\n')
  return needsNewLine ? `${current}\n${chunk}` : `${current}${chunk}`
}
