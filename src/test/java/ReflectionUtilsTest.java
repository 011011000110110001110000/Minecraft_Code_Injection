import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ReflectionUtilsTest {
	@Test
	void testInit() {
		assertDoesNotThrow(
			() -> {
				Class<?> classReflectionUtils = null;
				try {
					classReflectionUtils = Class.forName("me.lollopollqo.sus.injection.util.ReflectionUtils");
				} catch (ClassNotFoundException cnfe) {
					// This should never happen
				}
				return classReflectionUtils;
			}
		);
	}
}
