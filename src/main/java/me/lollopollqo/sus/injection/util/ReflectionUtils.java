package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class that contains some useful methods for easier / more powerful reflection usage.
 *
 * @author Lollopollqo
 */
@SuppressWarnings("unused")
public class ReflectionUtils {
    /**
     * The {@link sun.misc.Unsafe} instance
     */
    private static final sun.misc.Unsafe UNSAFE;
    /**
     * The offset of the <code>override</code> field in an instance of {@link AccessibleObject}
     *
     * @see #forceSetAccessible(AccessibleObject, boolean)
     */
    private static final long overrideOffset;
    /**
     * Trusted lookup
     */
    private static final MethodHandles.Lookup LOOKUP;

    static {
        UNSAFE = findUnsafe();
        // This needs to be done first because createLookup() uses the overrideOffset field value
        overrideOffset = findOverrideOffset();

        if (overrideOffset == -1) {
            throw new RuntimeException("Could not locate AccessibleObject#override!");
        }

        LOOKUP = createTrustedLookup();
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
    public static Method getMethod(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
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
    public static Field forceGetField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        forceSetAccessible(field, true);
        return field;
    }

    /**
     * Gets a field by name and forces it to be accessible. <br>
     * Use this instead of {@link #forceGetField(Class, String)} if the field name is not known.
     *
     * @param owner     The class that declares the field
     * @param modifiers The access modifiers of the field
     * @param type      The type of the field
     * @return the field with the specified owner, access modifiers and type.
     */
    public static Field forceGetField(Class<?> owner, int modifiers, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getModifiers() == modifiers && field.getType() == type) {
                forceSetAccessible(field, true);
                return field;
            }
        }
        throw new RuntimeException("Failed to get field from " + owner.getName() + " of type" + type.getName());
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
    @SuppressWarnings("deprecation")
    private static long findOverrideOffset() {
        long overrideOffset = -1;
        final AccessibleObject object = allocateInstance(AccessibleObject.class);
        // In Java, the size of an integer is 32 bits
        final int intSizeBytes = 32 / 8;

        for (int currentOffset = 0; currentOffset <= 64; currentOffset += 4) {
            int original = UNSAFE.getInt(object, currentOffset);
            object.setAccessible(true);
            if (original != UNSAFE.getInt(object, currentOffset)) {
                UNSAFE.putInt(object, currentOffset, original);
                if (!object.isAccessible()) {
                    overrideOffset = currentOffset;
                    break;
                }
            }
            object.setAccessible(false);
        }

        return overrideOffset;
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     */
    private static void forceSetAccessible(AccessibleObject object, boolean accessible) {
        UNSAFE.putInt(object, overrideOffset, accessible ? 1 : 0);
    }

    /**
     * Gets a reference to the {@link sun.misc.Unsafe} instance in a JDK agnostic way, which means not relying on the field name.
     *
     * @return the {@link sun.misc.Unsafe} instance
     */
    private static sun.misc.Unsafe findUnsafe() {
        final int unsafeModifiers = Modifier.STATIC | Modifier.FINAL;

        final List<Throwable> exceptions = new ArrayList<>();
        for (Field field : sun.misc.Unsafe.class.getDeclaredFields()) {

            if (field.getType() != sun.misc.Unsafe.class || (field.getModifiers() & unsafeModifiers) != unsafeModifiers) {
                continue;
            }

            try {
                field.setAccessible(true);
                if (field.get(null) instanceof sun.misc.Unsafe unsafe) {
                    return unsafe;
                }
            } catch (Throwable e) {
                exceptions.add(e);
            }
        }

        RuntimeException exception = new RuntimeException("Could not find sun.misc.Unsafe instance!");
        exceptions.forEach(exception::addSuppressed);
        throw exception;
    }

    /**
     * Constructs a {@link MethodHandles.Lookup} instance with <code>TRUSTED</code> access.
     *
     * @return the created {@link MethodHandles.Lookup} instance
     */
    private static MethodHandles.Lookup createTrustedLookup() {
        try {
            var constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
            UNSAFE.putBoolean(constructor, overrideOffset, true);
            return constructor.newInstance(Object.class, null, -1);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not create trusted lookup!", roe);
        }
    }

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    @NotNull
    private static <T> T allocateInstance(@NotNull Class<T> type) {
        try {
            return (T) UNSAFE.allocateInstance(type);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to allocate instance of " + getModuleInclusiveClassName(type), e);
        }
    }

}
