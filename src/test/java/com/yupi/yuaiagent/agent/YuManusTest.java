package com.yupi.yuaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class YuManusTest {

    @Resource
    private LoLoManus loLoManus;

    @Test
    public void run() {
        String userPrompt = """
                我的另一半住在上海静安区，请帮我找 5 公里内合适的约会地点，
                并结合一些网络图片，制定一份详细的约会计划，
                再以 PDF 格式输出。
                你应该用中文回答我。
                """;
        String answer = loLoManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
