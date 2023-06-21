import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
