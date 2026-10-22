package mcmod.yfcorelib.api.platform;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 平台服务发现契约测试：api 单独存在（无平台实现）时必须给出明确错误。 */
class ServicesTest {
	@Test
	void missingProviderShouldThrowClearError() {
		assertThrows(IllegalStateException.class, Services::platform);
	}
}
