import me.lollopollqo.sus.injection.util.ReflectionUtils;
import org.junit.jupiter.api.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;

@SuppressWarnings({"unused", "FieldCanBeLocal", "FieldMayBeFinal"})
@TestMethodOrder(
        MethodOrderer.OrderAnnotation.class
)
public class ReflectionUtilsTest {
    static boolean dummyClassInitialized;

    @Test
    @Order(1)
    void testInit() throws Throwable {
        final Class<?> classReflectionUtils = Class.forName("me.lollopollqo.sus.injection.util.ReflectionUtils");
    }

    @Test
    void testEnsureInitialized() throws Throwable {
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

        ReflectionUtils.clearReflectionFiltersForClass(forTest);
        ReflectionUtils.clearReflectionCacheForClass(forTest);

        fields = forTest.getDeclaredFields();
        Assertions.assertNotEquals(0, fields.length);
    }

    @Test
    @SuppressWarnings("deprecation")
    void testSetAccessible() throws Throwable {
        final Field serialVersionUID = ArrayList.class.getDeclaredField("serialVersionUID");

        Assertions.assertFalse(serialVersionUID.isAccessible());

        Assertions.assertFalse(serialVersionUID.trySetAccessible());

        ReflectionUtils.setAccessible(serialVersionUID, true);

        Assertions.assertTrue(serialVersionUID.isAccessible());
    }

    @Test
    void testChangeFieldType() throws Throwable {
        class Test {
            private boolean booleanField;

            Test() {
                this.booleanField = true;
            }
        }

        final String suchBoolean = "Such boolean";
        final Test testObj = new Test();
        final MethodHandle typeSetter = ReflectionUtils.findSetter(Field.class, "type", Class.class);
        final MethodHandle rootGetter = ReflectionUtils.findGetter(Field.class, "root", Field.class);
        final Field booleanFieldCopy = Test.class.getDeclaredField("booleanField");
        final Field booleanField = (Field) rootGetter.bindTo(booleanFieldCopy).invoke();

        typeSetter.bindTo(booleanField).invoke(String.class);
        booleanField.set(testObj, "Such boolean");

        Assertions.assertSame(suchBoolean, booleanField.get(testObj));
    }

    @Test
    void testMethodHandleCracking() throws Throwable {
        class Test {
            private final Object privateField;

            Test() {
                this.privateField = new Object();
            }
        }

        final Test testObject = new Test();
        final MethodHandle privateFieldGetter = ReflectionUtils.findGetter(Test.class, "privateField", Object.class);
        final Field privateFieldField = MethodHandles.reflectAs(Field.class, privateFieldGetter);

        privateFieldField.setAccessible(true);

        Assertions.assertEquals(privateFieldGetter.invoke(testObject), privateFieldField.get(testObject));
    }

    @Test
    void testGetUnsafe() throws Throwable {
        final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) ReflectionUtils.lookupIn(sun.misc.Unsafe.class)
                .findStatic(
                        sun.misc.Unsafe.class,
                        "getUnsafe",
                        MethodType.methodType(sun.misc.Unsafe.class)
                )
                .invoke();

        Assertions.assertNotNull(unsafe);
        Assertions.assertEquals(sun.misc.Unsafe.class, unsafe.getClass());
    }

    private static final class DummyClass {
        static {
            dummyClassInitialized = true;
        }
    }

}
