package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper class that contains some useful methods for easier / more powerful reflection usage.
 *
 * @author Lollopollqo
 */
@SuppressWarnings("unused")
public class ReflectionUtils {
    /**
     * The {@link jdk.internal.misc.Unsafe} instance
     */
    private static final jdk.internal.misc.Unsafe UNSAFE;
    /**
     * Trusted lookup
     */
    private static final MethodHandles.Lookup LOOKUP;
    /**
     * Handle for {@link AccessibleObject#setAccessible0(boolean)}
     */
    private static final MethodHandle setAccessible0;

    static {
        UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();
        LOOKUP = createTrustedLookup();
        setAccessible0 = setAccessible0Handle();
    }

    /**
     * Gets a method by name and forces it to be accessible.
     *
     * @param owner      The class that declares the method
     * @param name       The name of the method
     * @param paramTypes The types of the parameters of the method, in order
     * @return the method with the specified owner, name and parameter types
     * @throws NoSuchMethodException if a field with the specified name and parameter types could not be found in the specified class
     */
    public static Method forceGetDeclaredMethod(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, paramTypes);
        forceSetAccessible(method, true);
        return method;
    }

    /**
     * Gets a field by name and forces it to be accessible.
     *
     * @param owner The class that declares the field
     * @param name  The name of the field
     * @return the field with the specified owner and name
     * @throws NoSuchFieldException if a field with the specified name could not be found in the specified class
     */
    public static Field forceGetDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        forceSetAccessible(field, true);
        return field;
    }

    /**
     * Gets a field by name and forces it to be accessible. <br>
     * Use this instead of {@link #forceGetDeclaredField(Class, String)} if the field name is not known.
     *
     * @param owner     The class that declares the field
     * @param modifiers The access modifiers of the field
     * @param type      The type of the field
     * @return the field with the specified owner, access modifiers and type
     */
    public static Field forceGetDeclaredField(Class<?> owner, int modifiers, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getModifiers() == modifiers && field.getType() == type) {
                forceSetAccessible(field, true);
                return field;
            }
        }
        throw new RuntimeException("Failed to get field from " + owner.getName() + " of type" + type.getName());
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     * @see #setAccessible0
     */
    @SuppressWarnings("SameParameterValue")
    private static void forceSetAccessible(AccessibleObject object, boolean accessible) {
        setAccessible(object, accessible);
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility. <br>
     * Unlike {@link AccessibleObject#setAccessible(boolean)}, this method does not perform any permission checks.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible the accessibility to be forcefully set
     * @return the value of the <code>accessible</code> argument
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setAccessible(AccessibleObject object, boolean accessible) {
        try {
            return (boolean) setAccessible0.bindTo(object).invokeExact(accessible);
        } catch (Throwable e) {
            // This should never happen
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object owner, String name, Class<T> type) {
        try {
            return (T) findGetter(owner.getClass(), name, type).invoke(owner);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get field " + name + " from " + getModuleInclusiveClassName(owner.getClass()), e);
        }
    }

    public static void invokeNonStaticMethod(Object owner, String name, MethodType type, Object... arguments) {
        try {
            LOOKUP.bind(owner, name, type).invokeWithArguments(arguments);
        } catch (Throwable e) {
            new RuntimeException("Failed to invoke " + getModuleInclusiveClassName(owner.getClass()) + "." + name + type, e).printStackTrace();
        }
    }

    public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findGetter(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findGetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findGetter(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStaticGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findStaticGetter(owner, name, type);
    }

    public static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findSetter(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findSetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findSetter(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findStaticSetter(owner, name, type);
    }

    public static <T> Class<T> loadClass(String name) throws ReflectiveOperationException {
        return loadClass(ReflectionUtils.class.getClassLoader(), name);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(ClassLoader loader, String name) throws ReflectiveOperationException {
        return (Class<T>) loader.loadClass(name);
    }

    public static MethodHandle findVirtual(Class<?> owner, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return findVirtual(owner, name, MethodType.methodType(returnType, params));
    }

    public static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findVirtual(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return findVirtualAndBind(owner, instance, name, MethodType.methodType(returnType, params));
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, MethodType type) throws ReflectiveOperationException {
        return findVirtual(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStatic(Class<?> owner, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return LOOKUP.in(owner).findStatic(owner, name, MethodType.methodType(returnType, params));
    }

    /**
     * Ensures the specified class is initialized.
     *
     * @param clazz the class whose initialization is to be ensured
     * @return the initialized class
     */
    @NotNull
    public static <T> Class<T> ensureInitialized(@NotNull Class<T> clazz) {
        try {
            LOOKUP.in(clazz).ensureInitialized(clazz);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to ensure " + getModuleInclusiveClassName(clazz) + " was initialized", e);
        }
        return clazz;
    }

    /**
     * Ensures the specified classes are initialized.
     *
     * @param classes the classes whose initialization is to be ensured
     * @return an array containing the initialized classes
     * @see #ensureInitialized(Class)
     */
    @NotNull
    public static Class<?>[] ensureInitialized(@NotNull Class<?> @NotNull ... classes) {
        for (Class<?> clazz : classes) {
            ensureInitialized(clazz);
        }
        return classes;
    }

    /**
     * Gets the full name of the specified class, including the module it's defined in.
     *
     * @param clazz the class whose name is to be determined
     * @return a String obtained by concatenating the name of the module the specified class is defined in and the name of the class itself, separated by the <code>'/'</code> character
     */
    @NotNull
    public static String getModuleInclusiveClassName(@NotNull Class<?> clazz) {
        return clazz.getModule().getName() + '/' + clazz.getName();
    }

    /**
     * Finds the offset of the <code>override</code> field in an {@link AccessibleObject} instance.
     *
     * @return the offset of the <code>override</code> field of an {@link AccessibleObject} instance, <br>
     * or <code>-1</code> if the offset could not be determined
     */
    private static long findOverrideOffset() {
        long overrideOffset = -1;
        final AccessibleObject object = allocateInstance(AccessibleObject.class);
        // 4 byte mark word (32 bit machine) and 4 byte klass word
        final int minHeaderSizeBytes = 8;

        /*
         * As of JDK 17, there's technically no need to check up to 64 bytes because the object header is at most 12 bytes
         * (12 bytes on a 64 bit machine, 8 bytes on a 32 bit machine), and the AccessibleObject class only declares a singular boolean field
         * (the override field which we are interested in).
         *
         * This means that the offset should always be the size of the object header, but in case a few more fields are introduced in later JDK versions
         * this code tries to account for it.
         */
        for (int currentOffset = minHeaderSizeBytes; currentOffset <= 64; currentOffset++) {
            // Set the override field to false
            object.setAccessible(false);
            // If the boolean value at the current offset is true, then it means it is not the override field, so keep trying
            if (UNSAFE.getBoolean(object, currentOffset)) {
                continue;
            }
            // Change the value of the override field to true
            object.setAccessible(true);
            // If the boolean value at the current offset is now true, then it means we found the override field
            if (UNSAFE.getBoolean(object, currentOffset)) {
                overrideOffset = currentOffset;
                break;
            }
        }

        return overrideOffset;
    }

    /**
     * Gets a {@link MethodHandle} for {@link AccessibleObject#setAccessible0(boolean)}.
     *
     * @return the acquired {@link MethodHandle}
     */
    private static MethodHandle setAccessible0Handle() {
        try {
            return findVirtual(AccessibleObject.class, "setAccessible0", boolean.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get AccessibleObject#setAccessible0 handle!", e);
        }
    }

    /**
     * Constructs a {@link MethodHandles.Lookup} instance with <code>TRUSTED</code> access.
     *
     * @return the created {@link MethodHandles.Lookup} instance
     */
    private static MethodHandles.Lookup createTrustedLookup() {
        try {
            // See MethodHandles.Lookup#TRUSTED
            final int trusted = -1;
            long overrideOffset = findOverrideOffset();

            if (overrideOffset == -1) {
                throw new RuntimeException("Could not locate AccessibleObject#override!");
            }

            Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
            // We cannot use forceSetAccessible for obvious reasons, so we resort to Unsafe
            UNSAFE.putBoolean(lookupConstructor, overrideOffset, true);
            return lookupConstructor.newInstance(Object.class, null, trusted);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not create trusted lookup!", roe);
        }
    }

    /**
     * Allocates a new instance of the given class, throwing a {@link RuntimeException} if an {@link InstantiationException} occurs.
     *
     * @param clazz The class a new instance of which is to be created
     * @return the allocated instance
     * @see jdk.internal.misc.Unsafe#allocateInstance(Class)
     */
    @SuppressWarnings({"unchecked", "SameParameterValue"})
    @NotNull
    private static <T> T allocateInstance(@NotNull Class<T> clazz) {
        try {
            return (T) UNSAFE.allocateInstance(clazz);
        } catch (InstantiationException ie) {
            throw new RuntimeException("Failed to allocate instance of " + getModuleInclusiveClassName(clazz), ie);
        }
    }

}
