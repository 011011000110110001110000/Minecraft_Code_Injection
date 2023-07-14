import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

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
}
