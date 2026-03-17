package com.yupi.yuaiagent.memory;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 长期记忆实体
 */
@Data
public class LongTermMemory {
    private String id;
    private String sessionId;
    private String content;
    private LocalDateTime timestamp;
    private String messageType; // user/assistant
}
