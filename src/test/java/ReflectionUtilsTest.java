import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public class ReflectionUtilsTest {
    static boolean dummyClassInitialized;
    @Test
    void testInit() {
        Assertions.assertDoesNotThrow(
                () -> {
                    Class<?> classReflectionUtils = Class.forName("me.lollopollqo.sus.injection.util.ReflectionUtils");
                }
        );
    }

    @Test
    void testEnsureInitialized() throws Throwable{
        final Class<?> clazz = Class.forName("ReflectionUtilsTest$DummyClass", false, ReflectionUtilsTest.class.getClassLoader());
        Assertions.assertFalse(dummyClassInitialized);
        ReflectionUtils.ensureInitialized(clazz);
        Assertions.assertTrue(dummyClassInitialized);
    }

    @Test
    void testReflectionFilterRemoval() {
        final Class<?> forTest = Field.class;
        Field[] fields;

        fields = forTest.getDeclaredFields();
        Assertions.assertEquals(0, fields.length);

        ReflectionUtils.clearReflectionFiltersAndCacheForClass(forTest);

        fields = forTest.getDeclaredFields();
        Assertions.assertNotEquals(0, fields.length);
    }

    @Test
    void testChangeFieldType() throws Throwable {
        class Test {
            private boolean booleanField;

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
    void testMethodHandleCracking() {
        class Test {
            private final Object privateField;

            Test() {
                this.privateField = new Object();
            }
        }

        Assertions.assertDoesNotThrow(
                () -> {
                    final Test testObject = new Test();
                    final MethodHandle privateFieldGetter = ReflectionUtils.findGetter(Test.class, "privateField", Object.class);
                    final Field privateFieldField = MethodHandles.reflectAs(Field.class, privateFieldGetter);

                    privateFieldField.setAccessible(true);

                    Assertions.assertEquals(privateFieldGetter.invoke(testObject), privateFieldField.get(testObject));
                }
        );
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

    private static class DummyClass {
        static {
            dummyClassInitialized = true;
        }
    }

}
