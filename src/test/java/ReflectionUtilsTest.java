import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class ReflectionUtilsTest {
	@Test
	void testInit() {
		Assertions.assertDoesNotThrow(
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

	@Test
	void testAddOpens() {
		Assertions.assertDoesNotThrow(
				() -> {
					Class<?> reflectionClass = Class.forName("jdk.internal.reflect.Reflection");
					Module javaBaseModule = reflectionClass.getModule();
					ReflectionUtils.addOpens(javaBaseModule, "jdk.internal.reflect");
					Method method = reflectionClass.getDeclaredMethod("registerFilter", Map.class, Class.class, Set.class);
					method.setAccessible(true);
				}
		);
	}
}
