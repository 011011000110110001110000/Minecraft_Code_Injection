package me.lollopollqo.sus.injection.util;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper class that contains some useful methods for easier reflection usage.
 *
 * @author Lollopollqo
 */
public class ReflectionUtils {
    /**
     * Trusted lookup
     */
    public static final MethodHandles.Lookup LOOKUP;

    static {
        try {
            LOOKUP = ReflectionUtils.createLookup();
        } catch (InstantiationException ie) {
            throw new RuntimeException("Failed to create trusted lookup", ie);
        }
    }

    /**
     * Get a method by name and force it to be accessible.
     */
    public static Method getMethod(Class<?> base, String name, Class<?>... params) throws Exception {
        Method m = LOOKUP.in(base).lookupClass().getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    /**
     * Get a field by name and force it to be accessible.
     */
    public static Field getField(Class<?> base, String name) throws Exception {
        Field f = LOOKUP.in(base).lookupClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static MethodHandles.Lookup createLookup() throws InstantiationException {
        long override = -1;
        {
            AccessibleObject object = UnsafeUtils.allocateInstance(AccessibleObject.class);
            for (long offset = 4; offset < 64; offset++) {
                object.setAccessible(false);
                if (UnsafeUtils.getBoolean(object, offset)) {
                    continue;
                }
                object.setAccessible(true);
                if (UnsafeUtils.getBoolean(object, offset)) {
                    override = offset;
                    break;
                }
            }
            if (override == -1) {
                throw new RuntimeException("Failed to find AccessibleObject.override offset");
            }
        }

        try {
            Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
            UnsafeUtils.putBoolean(constructor, override, true);
            return constructor.newInstance(Object.class, null, -1);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate MethodHandles.Lookup", e);
        }
    }
}
