# Long-Term Memory Retention Design

## Goal
Add a retention policy for long-term chat memory stored in `vector_store` so only the most recent 5 days of archived `chat_memory` records are kept.

## Scope
- Apply retention only to long-term memory rows written by `MemoryService`
- Identify managed rows by `metadata.sourceType = chat_memory`
- Use the existing metadata timestamp written during archival
- Do not change `manus_message`, `love_app_message`, or `manus_private_vector_store`

## Recommended Approach
Use incremental cleanup inside `MemoryService.archiveToLongTerm(...)`.

Before adding new archived documents, delete expired rows from `vector_store` where:
- `metadata.sourceType = chat_memory`
- `metadata.timestamp` is older than `now() - retentionDays`

This keeps the implementation small, makes retention effective immediately, and avoids adding scheduler/configuration surface area for now.

## Data Rules
- Default retention window: 5 days
- Make the retention window configurable by property so production can tune it later without another code change
- If JDBC is unavailable, skip cleanup and continue archival
- Cleanup must not touch non-`chat_memory` rows
- Cleanup must not touch newer `chat_memory` rows inside the retention window

## Testing
- Deleting expired `chat_memory` rows triggers before archival
- Rows newer than 5 days are not deleted
- Rows with another `sourceType` are not deleted
- Cleanup failure does not block archival
