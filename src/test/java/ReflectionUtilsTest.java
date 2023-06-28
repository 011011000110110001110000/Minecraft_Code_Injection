import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class ReflectionUtilsTest {
    @org.junit.jupiter.api.Test
    void testInit() {
        Assertions.assertDoesNotThrow(
                () -> {
                    Class<?> classReflectionUtils = Class.forName("me.lollopollqo.sus.injection.util.ReflectionUtils");
                }
        );
    }

    @org.junit.jupiter.api.Test
    void testRemoveFieldReflectionFilters() {
        Assertions.assertDoesNotThrow(
                () -> {
                    final Field lookupClass;
                    Assertions.assertThrows(
                            NoSuchFieldException.class,
                            () -> MethodHandles.Lookup.class.getDeclaredField("lookupClass")
                    );

                    Map<Class<?>, Set<String>> original = ReflectionUtils.removeFieldReflectionFiltersForClass(MethodHandles.Lookup.class);
                    lookupClass = MethodHandles.Lookup.class.getDeclaredField("lookupClass");
                }
        );
    }
}
