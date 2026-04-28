package com.saga.sattolux;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires local DB and environment secrets; covered by focused unit tests instead.")
@SpringBootTest(properties = {
		"security.jwt.secret=test-jwt-secret-value-123456789012345",
		"ai.claude.api-key=",
})
class SattoluxApplicationTests {

	@Test
	void contextLoads() {
	}

}
