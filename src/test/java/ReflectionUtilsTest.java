import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public class ReflectionUtilsTest {
    @Test
    void testInit() {
        Assertions.assertDoesNotThrow(
                () -> {
                    Class<?> classReflectionUtils = Class.forName("me.lollopollqo.sus.injection.util.ReflectionUtils");
                }
        );
    }


    @Test
    @SuppressWarnings("UnusedAssignment")
    void testRemoveFieldReflectionFilters() {
        Assertions.assertThrows(
                NoSuchFieldException.class,
                () -> MethodHandles.Lookup.class.getDeclaredField("lookupClass")
        );

        Assertions.assertDoesNotThrow(
                () -> {
                    final Field lookupClass;
                    Map<Class<?>, Set<String>> original = ReflectionUtils.removeFieldReflectionFiltersForClass(MethodHandles.Lookup.class);
                    lookupClass = MethodHandles.Lookup.class.getDeclaredField("lookupClass");
                }
        );
    }

    @Test
    void testChangeFieldType() throws Throwable {
        class Test {
            private final boolean booleanField;

            Test() {
                this.booleanField = true;
            }
        }

        {
            final Test testObj = new Test();
            final MethodHandle typeSetter = ReflectionUtils.findSetter(Field.class, "type", Class.class);
            final MethodHandle rootGetter = ReflectionUtils.findGetter(Field.class, "root", Field.class);
            final Field booleanFieldCopy = Test.class.getDeclaredField("booleanField");
            final Field booleanField = (Field) rootGetter.bindTo(booleanFieldCopy).invoke();
            typeSetter.bindTo(booleanField).invoke(String.class);
            booleanField.set(testObj, "Such boolean");
            System.out.println(booleanField.get(testObj));
            System.out.println(testObj.booleanField);
        }
    }

    @Test
    void testGetter() {
        class Test {
            private final Object privateField;

            Test() {
                this.privateField = new Object();
            }
        }
    }

    @Test
    void testSetter() {

    }

    @Test
    void testTypedVarHandle() {

    }
}
