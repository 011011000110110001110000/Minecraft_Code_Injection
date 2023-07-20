package me.lollopollqo.sus.injection.util;

import jdk.internal.access.JavaLangAccess;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.*;

/**
 * <b>WIP</b> <br>
 * TODO: documentation, more helpers for internal methods, disabling reflection filters <br>
 * A collection of hacks for easier / enhanced usage of the reflection and invocation APIs. <br>
 *
 * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
 */
@SuppressWarnings("unused")
public final class ReflectionUtils {
    /**
     * Cached {@link StackWalker} instance for caller checking
     */
    private static final StackWalker STACK_WALKER;
    private static final VarHandle reflectionCacheHandle;
    private static final VarHandle fieldFilterMapHandle;
    private static final VarHandle methodFilterMapHandle;
    private static final VarHandle overrideHandle;

    static {

        STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

        // ModuleHelper needs to be initialized before the other helper classes
        ModuleHelper.bootstrap();

        final String className = Class.class.getName();
        VarHandle tempReflectionCacheHandle;
        Class<?> fieldType;
        String fieldName;
        try {
            try {
                Class.forName(className + "$ReflectionData");
                fieldType = SoftReference.class;
                fieldName = "reflectionData";
            } catch (ClassNotFoundException cnfe) {
                // Semeru is a special case
                fieldType = Class.forName(className + "$ReflectCache");
                fieldName = "reflectCache";
            }

            tempReflectionCacheHandle = findVarHandle(Class.class, fieldName, fieldType);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get VarHandle for the cached reflection data", roe);
        }

        reflectionCacheHandle = tempReflectionCacheHandle;

        final String reflectionClassName = "jdk.internal.reflect.Reflection";
        final String fieldFilterMap = "fieldFilterMap";
        final String methodFilterMap = "methodFilterMap";

        try {
            // noinspection Java9ReflectionClassVisibility
            final Class<?> reflectionClass = Class.forName(reflectionClassName);

            fieldFilterMapHandle = findStaticVarHandle(reflectionClass, "fieldFilterMap", Map.class);
            methodFilterMapHandle = findStaticVarHandle(reflectionClass, "methodFilterMap", Map.class);
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Could not locate " + reflectionClassName, cnfe);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get VarHandles for " + reflectionClassName + "#" + fieldFilterMap + " and " + reflectionClassName + "#" + methodFilterMap, roe);
        }

        final String override = "override";

        try {
            overrideHandle = findVarHandle(AccessibleObject.class, "override", boolean.class);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get VarHandle for "  + AccessibleObject.class.getName() + "#" + override, roe);
        }

    }

    /**
     * Gets a method by name and forces it to be accessible.
     *
     * @param owner      The class or interface from which the method is accessed
     * @param name       The name of the method
     * @param paramTypes The types of the parameters of the method, in order
     * @return the method with the specified owner, name and parameter types
     * @throws NoSuchMethodException if a field with the specified name and parameter types could not be found in the specified class
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredMethod(String, Class[])} in regard to what methods it can find (see {@link jdk.internal.reflect.Reflection#registerMethodsToFilter}).
     * @see #unsafeSetAccessible(AccessibleObject, boolean)
     */
    public static Method forceGetDeclaredMethodWithUnsafe(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, paramTypes);
        unsafeSetAccessible(method, true);
        return method;
    }

    /**
     * Gets a field by name and forces it to be accessible.
     *
     * @param owner The class or interface from which the field is accessed
     * @param name  The name of the field
     * @return the field with the specified owner and name
     * @throws NoSuchFieldException if a field with the specified name could not be found in the specified class
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredField(String)} in regard to what fields it can find (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     * @see #forceGetDeclaredFieldWithUnsafe(Class, int, Class)
     * @see #unsafeSetAccessible(AccessibleObject, boolean)
     */
    public static Field forceGetDeclaredFieldWithUnsafe(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        unsafeSetAccessible(field, true);
        return field;
    }

    /**
     * Gets a field with the specified type and access modifiers and forces it to be accessible. <br>
     * Use this instead of {@link #forceGetDeclaredFieldWithUnsafe(Class, String)} if the field name is not known. <br>
     * If there is more than one field with the specified modifiers and type in the target class, <br>
     * then this method will return the first field with the specified modifiers and type it can find in the target class. <br>
     * The order in which fields are checked is determined by {@link Class#getDeclaredFields()}.
     *
     * @param owner     The class or interface from which the field is accessed
     * @param modifiers The access modifiers of the field
     * @param type      The type of the field
     * @return the field with the specified owner, access modifiers and type
     * @throws NoSuchFieldException if a field with the specified type and modifiers could not be found in the specified class
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredFields()} in regard to what fields it can find (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     * @see #forceGetDeclaredFieldWithUnsafe(Class, String)
     * @see #unsafeSetAccessible(AccessibleObject, boolean)
     */
    public static Field forceGetDeclaredFieldWithUnsafe(Class<?> owner, int modifiers, Class<?> type) throws NoSuchFieldException {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getModifiers() == modifiers && field.getType() == type) {
                unsafeSetAccessible(field, true);
                return field;
            }
        }
        throw new NoSuchFieldException("Failed to get field from " + owner.getName() + " of type" + type.getName());
    }

    /**
     * Gets <em>all</em> declared fields of a class, bypassing reflection filters.
     * This is done by first disabling all reflection filters for the class, then clearing the cached reflection data.
     * Then, a regular invocation of {@link Class#getDeclaredFields()} is performed, and that call will be able to see all the declared fields,
     * without any filters. In the same fashion as {@link #clearReflectionFiltersAndCacheForClass(Class)}, an invocation of this method on class {@code owner}
     * will cause all subsequent calls to {@link Class#getDeclaredFields()} on {@code owner} to return the full, unfiltered list of declared fields.
     *
     * @param owner The class that owns the fields
     * @return The unfiltered list of declared fields
     * @see #getAllDeclaredMethodsOfClass(Class)
     */
    public static Field[] getAllDeclaredFieldsOfClass(Class<?> owner) {
        clearReflectionFiltersAndCacheForClass(owner);
        return owner.getDeclaredFields();
    }

    /**
     * Gets <em>all</em> declared methods of a class, bypassing reflection filters.
     * This is done by first disabling all reflection filters for the class, then clearing the cached reflection data.
     * Then, a regular invocation of {@link Class#getDeclaredMethods()} is performed, and that call will be able to see all the declared fields,
     * without any filters. In the same fashion as {@link #clearReflectionFiltersAndCacheForClass(Class)}, an invocation of this method on class {@code owner}
     * will cause all subsequent calls to {@link Class#getDeclaredMethods()} on {@code owner} to return the full, unfiltered list of declared methods.
     *
     * @param owner The class that owns the methods
     * @return The unfiltered list of declared methods
     * @see #getAllDeclaredFieldsOfClass(Class)
     */
    public static Method[] getAllDeclaredMethodsOfClass(Class<?> owner) {
        clearReflectionFiltersAndCacheForClass(owner);
        return owner.getDeclaredMethods();
    }

    /**
     * Convenience method for invoking {@link #clearReflectionFiltersForClass(Class)} and {@link #clearReflectionCacheForClass(Class)} on a class.
     * This will cause any subsequent call of {@link Class#getDeclaredFields()} and {@link Class#getDeclaredMethods()} to return the full, unfiltered list.
     *
     * @param clazz The class whose reflection cache and filters are to be cleared
     * @see #clearReflectionCacheForClass(Class)
     * @see #clearReflectionFiltersForClass(Class)
     */
    public static void clearReflectionFiltersAndCacheForClass(Class<?> clazz) {
        clearReflectionFiltersForClass(clazz);
        clearReflectionCacheForClass(clazz);
    }

    /**
     * Atomically clears the cached reflection data for the given class.
     *
     * @param clazz The class whose reflection cache is to be cleared
     */
    public static void clearReflectionCacheForClass(Class<?> clazz) {
        reflectionCacheHandle.setVolatile(clazz, null);
    }

    /**
     * Removes any reflection filters that were set for the given class.
     *
     * @param clazz The class for which reflection filters are to be removed
     * @see jdk.internal.reflect.Reflection#registerFieldsToFilter(Class, Set)
     * @see jdk.internal.reflect.Reflection#registerMethodsToFilter(Class, Set)
     */
    @SuppressWarnings("unchecked")
    public static void clearReflectionFiltersForClass(Class<?> clazz) {
        final Map<Class<?>, Set<String>> originalFieldFilterMap = (Map<Class<?>, Set<String>>) fieldFilterMapHandle.getVolatile();
        final Map<Class<?>, Set<String>> originalMethodFilterMap = (Map<Class<?>, Set<String>>) methodFilterMapHandle.getVolatile();

        final Map<Class<?>, Set<String>> newFieldFilterMap = new HashMap<>(originalFieldFilterMap);
        final Map<Class<?>, Set<String>> newMethodFilterMap = new HashMap<>(originalMethodFilterMap);

        newFieldFilterMap.remove(clazz);
        newMethodFilterMap.remove(clazz);

        fieldFilterMapHandle.setVolatile(newFieldFilterMap);
        methodFilterMapHandle.setVolatile(newMethodFilterMap);
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility by using {@link UnsafeHelper#unsafeSetAccessible(AccessibleObject, boolean)}. <br>
     * Unlike {@link AccessibleObject#setAccessible(boolean)}, this method does not perform any permission checks.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     * @return <code>accessible</code>
     * @see UnsafeHelper#unsafeSetAccessible(AccessibleObject, boolean)
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean unsafeSetAccessible(AccessibleObject object, boolean accessible) {
        try {
            UnsafeHelper.unsafeSetAccessible(object, accessible);
            return accessible;
        } catch (Throwable e) {
            throw new RuntimeException("Could not force the accessibility of " + object + " to be set to " + accessible, e);
        }
    }

    /**
     * Exports the specified package from the specified module to the specified module.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @param target      The module the package is to be exported to
     * @see ModuleHelper#addExports(Module, String, Module)
     */
    public static void addExports(Module source, String packageName, Module target) {
        try {
            ModuleHelper.addExports(source, packageName, target);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to module " + target.getName(), t);
        }
    }

    /**
     * Exports the specified package from the specified module to all <em>unnamed</em> modules.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @see ModuleHelper#addExportsToAllUnnamed(Module, String)
     */
    public static void addExportsToAllUnnamed(Module source, String packageName) {
        try {
            ModuleHelper.addExportsToAllUnnamed(source, packageName);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all unnamed modules", t);
        }
    }

    /**
     * Exports the specified package from the specified module to all modules.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @see ModuleHelper#addExports(Module, String)
     */
    public static void addExports(Module source, String packageName) {
        try {
            ModuleHelper.addExports(source, packageName);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules", t);
        }
    }

    /**
     * Opens the specified package from the specified module to the specified module.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @param target      The module the package is to be opened to
     * @see ModuleHelper#addOpens(Module, String, Module)
     */
    public static void addOpens(Module source, String packageName, Module target) {
        try {
            ModuleHelper.addOpens(source, packageName, target);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to module " + target.getName(), t);
        }
    }

    /**
     * Opens the specified package from the specified module to all <em>unnamed</em> modules.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @see ModuleHelper#addOpensToAllUnnamed(Module, String)
     */
    public static void addOpensToAllUnnamed(Module source, String packageName) {
        try {
            ModuleHelper.addOpensToAllUnnamed(source, packageName);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all unnamed modules", t);
        }
    }

    /**
     * Opens the specified package from the specified module to <em>all</em> modules.
     *
     * @param source      The module the package belongs to
     * @param packageName The name of the package
     * @see ModuleHelper#addOpens(Module, String)
     */
    public static void addOpens(Module source, String packageName) {
        try {
            ModuleHelper.addOpens(source, packageName);
        } catch (Throwable t) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules", t);
        }
    }

    /**
     * Gets the value of the field with the specified name and type in the specified class. <br>
     * This method bypasses any limitations that {@link Class#getDeclaredField(String)} has (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     *
     * @param owner The class or interface from which the field is accessed
     * @param name  The name of the field
     * @param type  The type of the field
     * @return the value of the field
     */
    @SuppressWarnings("unchecked")
    public static <T> T getField(Object owner, String name, Class<T> type) {
        try {
            return (T) findGetter(owner.getClass(), name, type).invoke(owner);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get field " + name + " from " + getModuleInclusiveClassName(owner.getClass()), e);
        }
    }

    /**
     * Invokes the non-static method with the given name, arguments and return type on the given object.
     *
     * @param owner         The object to invoke the method on
     * @param name          The name of the method
     * @param returnType    The return type of the method
     * @param argumentTypes The argument types of the method
     * @param arguments     The arguments to use when invoking the method
     * @return the value returned by the method, cast to the appropriate type
     * @see #invokeNonStatic(Object, String, MethodType, Object...)
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeNonStatic(Object owner, String name, Class<T> returnType, Class<?>[] argumentTypes, Object... arguments) {
        return (T) invokeNonStatic(owner, name, MethodType.methodType(returnType, argumentTypes), arguments);
    }

    /**
     * Invokes the non-static method with the given name, arguments and return type on the given object.
     *
     * @param owner     The object to invoke the method on
     * @param name      The name of the method
     * @param type      The {@link MethodType} that describes the method
     * @param arguments The arguments to use when invoking the method
     * @return the value returned by the method as an {@link Object}
     */
    public static Object invokeNonStatic(Object owner, String name, MethodType type, Object... arguments) {
        try {
            return LookupHelper.LOOKUP.bind(owner, name, type).invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke " + getModuleInclusiveClassName(owner.getClass()) + "." + name + type, e);
        }
    }

    /**
     * Produces a method handle giving read access to a non-static field.
     * The type of the method handle will have a return type of the field's
     * value type.
     * The method handle's single argument will be the instance containing
     * the field.
     * Access checking is performed immediately on behalf of the lookup class.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field
     * @throws NoSuchFieldException   if the field does not exist
     * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
     * @see MethodHandleHelper#findGetter(Class, String, Class)
     */
    public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findGetter(owner, name, type);
    }

    /**
     * Produces a method handle giving read access to a non-static field, and binds it <br>
     * to the given <code>instance</code>. <br>
     * The type of the method handle will have a return type of the field's
     * value type. <br>
     * The method handle's single argument will be the instance containing
     * the field. <br>
     * Access checking is performed immediately on behalf of the lookup class. <br>
     *
     * @param owner    The class or interface from which the method is accessed
     * @param instance The instance that the method handle will be bound to
     * @param name     The field's name
     * @param type     The field's type
     * @return a method handle which can load values from the field
     * @throws NoSuchFieldException     if the field does not exist
     * @throws IllegalAccessException   if access checking fails, or if the field is {@code static}
     * @throws IllegalArgumentException if the target does not have a leading parameter type that is a reference type
     * @throws ClassCastException       if {@code instance} cannot be converted to the leading parameter type of the target
     * @see #findGetter(Class, String, Class)
     * @see java.lang.invoke.MethodHandle#bindTo(Object)
     */
    public static <O, T extends O> MethodHandle findGetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findGetter(owner, name, type).bindTo(instance);
    }

    /**
     * Produces a method handle giving read access to a static field. <br>
     * The type of the method handle will have a return type of the field's
     * value type. <br>
     * The method handle will take no arguments. <br>
     * Access checking is performed immediately on behalf of the lookup class. <br>
     * <br>
     * If the returned method handle is invoked, the field's class will
     * be initialized, if it has not already been initialized. <br>
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field
     * @throws NoSuchFieldException   if the field does not exist
     * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
     */
    public static MethodHandle findStaticGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStaticGetter(owner, name, type);
    }

    public static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findSetter(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findSetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findSetter(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStaticSetter(owner, name, type);
    }

    /**
     * Loads the class with the given name using this class' {@link ClassLoader}.
     *
     * @param name The name of the class
     * @return the loaded class
     * @throws ClassNotFoundException if the class can not be found
     * @see #loadClass(ClassLoader, String)
     */
    public static <T> Class<T> loadClass(String name) throws ClassNotFoundException {
        return loadClass(ReflectionUtils.class.getClassLoader(), name);
    }

    /**
     * Loads the class with the given name using the given {@link ClassLoader}.
     *
     * @param loader The class loader to use for loading the class
     * @param name   The name of the class
     * @return the loaded class
     * @throws ClassNotFoundException if the class can not be found
     * @see ClassLoader#loadClass(String)
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(ClassLoader loader, String name) throws ClassNotFoundException {
        return (Class<T>) loader.loadClass(name);
    }

    /**
     * Produces a method handle for a virtual method.
     * The type of the method handle will be that of the method,
     * with the receiver type (usually {@code owner}) prepended.
     * The method and all its argument types must be accessible to the lookup object.
     * <p>
     * When called, the handle will treat the first argument as a receiver
     * and, for non-private methods, dispatch on the receiver's type to determine which method
     * implementation to enter.
     * For private methods the named method in {@code owner} will be invoked on the receiver.
     * (The dispatching action is identical with that performed by an
     * {@code invokevirtual} or {@code invokeinterface} instruction.)
     * <p>
     * The first argument will be of type {@code owner} if the lookup
     * class has full privileges to access the member.  Otherwise
     * the member must be {@code protected} and the first argument
     * will be restricted in type to the lookup class.
     * <p>
     * The returned method handle will have
     * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
     * the method's variable arity modifier bit ({@code 0x0080}) is set.
     * <p>
     * Because of the general <a href="MethodHandles.Lookup.html#equiv">equivalence</a> between {@code invokevirtual}
     * instructions and method handles produced by {@code findVirtual},
     * if the class is {@code MethodHandle} and the name string is
     * {@code invokeExact} or {@code invoke}, the resulting
     * method handle is equivalent to one produced by
     * {@link java.lang.invoke.MethodHandles#exactInvoker MethodHandles.exactInvoker} or
     * {@link java.lang.invoke.MethodHandles#invoker MethodHandles.invoker}
     * with the same {@code type} argument.
     * <p>
     * If the class is {@code VarHandle} and the name string corresponds to
     * the name of a signature-polymorphic access mode method, the resulting
     * method handle is equivalent to one produced by
     * {@link java.lang.invoke.MethodHandles#varHandleInvoker} with
     * the access mode corresponding to the name string and with the same
     * {@code type} arguments.
     *
     * @param owner          The class or interface from which the method is accessed
     * @param name           The name of the method
     * @param returnType     The return type of the method
     * @param parameterTypes The types of the parameters the method accepts, in order
     * @return the desired method handle
     * @throws NoSuchMethodException  if the method does not exist
     * @throws IllegalAccessException if access checking fails,
     *                                or if the method is {@code static},
     *                                or if the method's variable arity modifier bit
     *                                is set and {@code asVarargsCollector} fails
     * @throws SecurityException      if a security manager is present and it
     *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     * @throws NullPointerException   if any argument is null
     * @see #findVirtual(Class, String, MethodType)
     */
    public static MethodHandle findVirtual(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return findVirtual(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    public static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findVirtual(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return findVirtualAndBind(owner, instance, name, MethodType.methodType(returnType, parameterTypes));
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, MethodType type) throws ReflectiveOperationException {
        return findVirtual(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStatic(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return findStatic(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    public static MethodHandle findStatic(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStatic(owner, name, type);
    }

    public static MethodHandle findConstructor(Class<?> owner, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return findConstructor(owner, MethodType.methodType(void.class, parameterTypes));
    }

    public static MethodHandle findConstructor(Class<?> owner, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findConstructor(owner, type);
    }

    public static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return findSpecial(owner, name, specialCaller, MethodType.methodType(returnType, parameterTypes));
    }

    public static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findSpecial(owner, name, specialCaller, type);
    }

    /**
     * Produces a VarHandle giving access to a non-static field {@code name}
     * of type {@code type} declared in a class of type {@code owner}.
     * The VarHandle's variable type is {@code type} and it has one
     * coordinate type, {@code owner}.
     * <p>
     * Access checking is performed immediately on behalf of the lookup
     * class.
     * <p>
     * Certain access modes of the returned VarHandle are unsupported under
     * the following conditions:
     * <ul>
     * <li>if the field is declared {@code final}, then the write, atomic
     *     update, numeric atomic update, and bitwise atomic update access
     *     modes are unsupported.
     * <li>if the field type is anything other than {@code byte},
     *     {@code short}, {@code char}, {@code int}, {@code long},
     *     {@code float}, or {@code double} then numeric atomic update
     *     access modes are unsupported.
     * <li>if the field type is anything other than {@code boolean},
     *     {@code byte}, {@code short}, {@code char}, {@code int} or
     *     {@code long} then bitwise atomic update access modes are
     *     unsupported.
     * </ul>
     * <p>
     * If the field is declared {@code volatile} then the returned VarHandle
     * will override access to the field (effectively ignore the
     * {@code volatile} declaration) in accordance to its specified
     * access modes.
     * <p>
     * If the field type is {@code float} or {@code double} then numeric
     * and atomic update access modes compare values using their bitwise
     * representation (see {@link Float#floatToRawIntBits} and
     * {@link Double#doubleToRawLongBits}, respectively).
     *
     * @param owner The receiver class, of type {@code R}, that declares the non-static field
     * @param name  The field's name
     * @param type  The field's type, of type {@code T}
     * @return a VarHandle giving access to non-static fields.
     * @throws NoSuchFieldException   if the field does not exist
     * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
     * @throws SecurityException      if a security manager is present and it
     *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     * @throws NullPointerException   if any argument is null
     * @apiNote Bitwise comparison of {@code float} values or {@code double} values,
     * as performed by the numeric and atomic update access modes, differ
     * from the primitive {@code ==} operator and the {@link Float#equals}
     * and {@link Double#equals} methods, specifically with respect to
     * comparing NaN values or comparing {@code -0.0} with {@code +0.0}.
     * Care should be taken when performing a compare and set or a compare
     * and exchange operation with such values since the operation may
     * unexpectedly fail.
     * There are many possible NaN values that are considered to be
     * {@code NaN} in Java, although no IEEE 754 floating-point operation
     * provided by Java can distinguish between them.  Operation failure can
     * occur if the expected or witness value is a NaN value and it is
     * transformed (perhaps in a platform specific manner) into another NaN
     * value, and thus has a different bitwise representation (see
     * {@link Float#intBitsToFloat} or {@link Double#longBitsToDouble} for more
     * details).
     * The values {@code -0.0} and {@code +0.0} have different bitwise
     * representations but are considered equal when using the primitive
     * {@code ==} operator.  Operation failure can occur if, for example, a
     * numeric algorithm computes an expected value to be say {@code -0.0}
     * and previously computed the witness value to be say {@code +0.0}.
     */
    public static VarHandle findVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return VarHandleHelper.findVarHandle(owner, name, type);
    }

    /**
     * Produces a VarHandle giving access to a static field {@code name} of
     * type {@code type} declared in a class of type {@code owner}.
     * The VarHandle's variable type is {@code type} and it has no
     * coordinate types.
     * <p>
     * Access checking is performed immediately on behalf of the lookup
     * class.
     * <p>
     * If the returned VarHandle is operated on, the declaring class will be
     * initialized, if it has not already been initialized.
     * <p>
     * Certain access modes of the returned VarHandle are unsupported under
     * the following conditions:
     * <ul>
     * <li>if the field is declared {@code final}, then the write, atomic
     *     update, numeric atomic update, and bitwise atomic update access
     *     modes are unsupported.
     * <li>if the field type is anything other than {@code byte},
     *     {@code short}, {@code char}, {@code int}, {@code long},
     *     {@code float}, or {@code double}, then numeric atomic update
     *     access modes are unsupported.
     * <li>if the field type is anything other than {@code boolean},
     *     {@code byte}, {@code short}, {@code char}, {@code int} or
     *     {@code long} then bitwise atomic update access modes are
     *     unsupported.
     * </ul>
     * <p>
     * If the field is declared {@code volatile} then the returned VarHandle
     * will override access to the field (effectively ignore the
     * {@code volatile} declaration) in accordance to its specified
     * access modes.
     * <p>
     * If the field type is {@code float} or {@code double} then numeric
     * and atomic update access modes compare values using their bitwise
     * representation (see {@link Float#floatToRawIntBits} and
     * {@link Double#doubleToRawLongBits}, respectively).
     *
     * @param owner The class that declares the static field
     * @param name  The field's name
     * @param type  The field's type, of type {@code T}
     * @return a VarHandle giving access to a static field
     * @throws NoSuchFieldException   if the field does not exist
     * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
     * @throws SecurityException      if a security manager is present and it
     *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     * @throws NullPointerException   if any argument is null
     * @apiNote Bitwise comparison of {@code float} values or {@code double} values,
     * as performed by the numeric and atomic update access modes, differ
     * from the primitive {@code ==} operator and the {@link Float#equals}
     * and {@link Double#equals} methods, specifically with respect to
     * comparing NaN values or comparing {@code -0.0} with {@code +0.0}.
     * Care should be taken when performing a compare and set or a compare
     * and exchange operation with such values since the operation may
     * unexpectedly fail.
     * There are many possible NaN values that are considered to be
     * {@code NaN} in Java, although no IEEE 754 floating-point operation
     * provided by Java can distinguish between them.  Operation failure can
     * occur if the expected or witness value is a NaN value and it is
     * transformed (perhaps in a platform specific manner) into another NaN
     * value, and thus has a different bitwise representation (see
     * {@link Float#intBitsToFloat} or {@link Double#longBitsToDouble} for more
     * details).
     * The values {@code -0.0} and {@code +0.0} have different bitwise
     * representations but are considered equal when using the primitive
     * {@code ==} operator.  Operation failure can occur if, for example, a
     * numeric algorithm computes an expected value to be say {@code -0.0}
     * and previously computed the witness value to be say {@code +0.0}.
     */
    public static VarHandle findStaticVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return VarHandleHelper.findStaticVarHandle(owner, name, type);
    }

    public static MethodHandle unreflect(Method m) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflect(m);
    }

    public static MethodHandle unreflectConstructor(Constructor<?> c) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectConstructor(c);
    }

    public static MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectSpecial(m, specialCaller);
    }

    public static MethodHandle unreflectGetter(Field f) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectGetter(f);
    }

    public static MethodHandle unreflectSetter(Field f) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectSetter(f);
    }

    public static VarHandle unreflectVarHandle(Field f) throws ReflectiveOperationException {
        return VarHandleHelper.unreflectVarHandle(f);
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
            return LookupHelper.ensureInitialized(clazz);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to ensure initialization of class " + getModuleInclusiveClassName(clazz), e);
        }
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
     * Gets the full name of the specified class, including the name of the module it's defined in.
     *
     * @param clazz the class whose name is to be determined
     * @return a String obtained by concatenating the name of the module the specified class is defined in and the name of the class itself, separated by the <code>'/'</code> character
     */
    @NotNull
    public static String getModuleInclusiveClassName(@NotNull Class<?> clazz) {
        return clazz.getModule().getName() + '/' + clazz.getName();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ReflectionUtils() {
        String callerBlame = "";
        try {
            callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
        } catch (IllegalCallerException ignored) {

        }
        throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.class) + callerBlame);
    }

    /**
     * Helper class that holds a trusted {@link MethodHandles.Lookup} instance. <br>
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class LookupHelper {
        /**
         * Cached trusted lookup instance
         */
        private static final MethodHandles.Lookup LOOKUP;
        /**
         * Cached constructor for the {@link MethodHandles.Lookup} class
         *
         * @see #trustedLookupIn(Class)
         */
        private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR;
        /**
         * Used to denote that the lookup object has trusted access
         */
        private static final int TRUSTED_MODE = -1;

        static {

            // Enable AccessibleObject#setAccessible(boolean) usage on the MethodHandles.Lookup members
            ModuleHelper.addOpens(Object.class.getModule(), "java.lang.invoke", LookupHelper.class.getModule());

            try {
                LOOKUP_CONSTRUCTOR = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
                LOOKUP_CONSTRUCTOR.setAccessible(true);
            } catch (NoSuchMethodException nsme) {
                throw new RuntimeException("Could not find " + getModuleInclusiveClassName(MethodHandles.Lookup.class) + "constructor", nsme);
            }

            LOOKUP = trustedLookupIn(Object.class);

        }

        /**
         * Ensures the specified class is initialized.
         *
         * @param clazz the class whose initialization is to be ensured
         * @return the initialized class
         */
        private static <T> Class<T> ensureInitialized(Class<T> clazz) throws IllegalAccessException {
            // We need to teleport the lookup to the specified class because of how the access checking is done,
            // even though the lookup itself has trusted access
            LookupHelper.LOOKUP.in(clazz).ensureInitialized(clazz);
            return clazz;
        }

        /**
         * Produces a trusted {@link MethodHandles.Lookup} instance with the given {@code lookupClass}.
         *
         * @param lookupClass The desired lookup class
         * @return the {@link MethodHandles.Lookup} instance
         */
        private static MethodHandles.Lookup trustedLookupIn(Class<?> lookupClass) {
            try {
                return LOOKUP_CONSTRUCTOR.newInstance(lookupClass, null, TRUSTED_MODE);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Could not create an instance of " + getModuleInclusiveClassName(MethodHandles.Lookup.class), e);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private LookupHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(LookupHelper.class) + callerBlame);
        }
    }

    /**
     * Helper class that makes working with {@link MethodHandle}s easier.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class MethodHandleHelper {
        // TODO: documentation

        /**
         * Produces a method handle giving read access to a non-static field.
         * The type of the method handle will have a return type of the field's
         * value type.
         * The method handle's single argument will be the instance containing
         * the field.
         * Access checking is performed immediately on behalf of the lookup class.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can load values from the field
         * @throws NoSuchFieldException   if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
         * @see java.lang.invoke.MethodHandles.Lookup#findGetter(Class, String, Class)
         */
        public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findGetter(owner, name, type);
        }

        /**
         * Produces a method handle giving read access to a static field. <br>
         * The type of the method handle will have a return type of the field's
         * value type. <br>
         * The method handle will take no arguments. <br>
         * Access checking is performed immediately on behalf of the lookup class. <br>
         * <br>
         * If the returned method handle is invoked, the field's class will
         * be initialized, if it has not already been initialized. <br>
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can load values from the field
         * @throws NoSuchFieldException   if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
         * @see MethodHandles.Lookup#findStaticGetter(Class, String, Class)
         */
        public static MethodHandle findStaticGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticGetter(owner, name, type);
        }

        private static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findSetter(owner, name, type);
        }

        public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticSetter(owner, name, type);
        }

        /**
         * Produces a method handle for a virtual method.
         * The type of the method handle will be that of the method,
         * with the receiver type (usually {@code owner}) prepended.
         * The method and all its argument types must be accessible to the lookup object.
         * <p>
         * When called, the handle will treat the first argument as a receiver
         * and, for non-private methods, dispatch on the receiver's type to determine which method
         * implementation to enter.
         * For private methods the named method in {@code owner} will be invoked on the receiver.
         * (The dispatching action is identical with that performed by an
         * {@code invokevirtual} or {@code invokeinterface} instruction.)
         * <p>
         * The first argument will be of type {@code owner} if the lookup
         * class has full privileges to access the member.  Otherwise
         * the member must be {@code protected} and the first argument
         * will be restricted in type to the lookup class.
         * <p>
         * The returned method handle will have
         * {@linkplain MethodHandle#asVarargsCollector variable arity} if and only if
         * the method's variable arity modifier bit ({@code 0x0080}) is set.
         * <p>
         * Because of the general <a href="MethodHandles.Lookup.html#equiv">equivalence</a> between {@code invokevirtual}
         * instructions and method handles produced by {@code findVirtual},
         * if the class is {@code MethodHandle} and the name string is
         * {@code invokeExact} or {@code invoke}, the resulting
         * method handle is equivalent to one produced by
         * {@link java.lang.invoke.MethodHandles#exactInvoker MethodHandles.exactInvoker} or
         * {@link java.lang.invoke.MethodHandles#invoker MethodHandles.invoker}
         * with the same {@code type} argument.
         * <p>
         * If the class is {@code VarHandle} and the name string corresponds to
         * the name of a signature-polymorphic access mode method, the resulting
         * method handle is equivalent to one produced by
         * {@link java.lang.invoke.MethodHandles#varHandleInvoker} with
         * the access mode corresponding to the name string and with the same
         * {@code type} arguments.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The name of the method
         * @param type  The type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @throws NoSuchMethodException  if the method does not exist
         * @throws IllegalAccessException if access checking fails,
         *                                or if the method is {@code static},
         *                                or if the method's variable arity modifier bit
         *                                is set and {@code asVarargsCollector} fails
         * @throws SecurityException      if a security manager is present and it
         *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException   if any argument is null
         * @see java.lang.invoke.MethodHandles.Lookup#findVirtual(Class, String, MethodType)
         */
        private static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findVirtual(owner, name, type);
        }

        private static MethodHandle findStatic(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStatic(owner, name, type);
        }

        private static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findSpecial(owner, name, type, specialCaller);
        }

        private static MethodHandle findConstructor(Class<?> owner, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findConstructor(owner, type);
        }

        private static MethodHandle unreflect(Method m) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflect(m);
        }

        private static MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectSpecial(m, specialCaller);
        }

        private static MethodHandle unreflectConstructor(Constructor<?> c) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectConstructor(c);
        }

        private static MethodHandle unreflectGetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectGetter(f);
        }

        private static MethodHandle unreflectSetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectSetter(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private MethodHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.MethodHandleHelper.class) + callerBlame);
        }
    }

    /**
     * Helper class that makes working with {@link VarHandle}s easier.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class VarHandleHelper {

        /**
         * Produces a VarHandle giving access to a non-static field {@code name}
         * of type {@code type} declared in a class of type {@code owner}.
         * The VarHandle's variable type is {@code type} and it has one
         * coordinate type, {@code owner}.
         * <p>
         * Access checking is performed immediately on behalf of the lookup
         * class.
         * <p>
         * Certain access modes of the returned VarHandle are unsupported under
         * the following conditions:
         * <ul>
         * <li>if the field is declared {@code final}, then the write, atomic
         *     update, numeric atomic update, and bitwise atomic update access
         *     modes are unsupported.
         * <li>if the field type is anything other than {@code byte},
         *     {@code short}, {@code char}, {@code int}, {@code long},
         *     {@code float}, or {@code double} then numeric atomic update
         *     access modes are unsupported.
         * <li>if the field type is anything other than {@code boolean},
         *     {@code byte}, {@code short}, {@code char}, {@code int} or
         *     {@code long} then bitwise atomic update access modes are
         *     unsupported.
         * </ul>
         * <p>
         * If the field is declared {@code volatile} then the returned VarHandle
         * will override access to the field (effectively ignore the
         * {@code volatile} declaration) in accordance to its specified
         * access modes.
         * <p>
         * If the field type is {@code float} or {@code double} then numeric
         * and atomic update access modes compare values using their bitwise
         * representation (see {@link Float#floatToRawIntBits} and
         * {@link Double#doubleToRawLongBits}, respectively).
         *
         * @param owner The receiver class, of type {@code R}, that declares the non-static field
         * @param name  The field's name
         * @param type  The field's type, of type {@code T}
         * @return a VarHandle giving access to non-static fields.
         * @throws NoSuchFieldException   if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is {@code static}
         * @throws SecurityException      if a security manager is present and it
         *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException   if any argument is null
         * @apiNote Bitwise comparison of {@code float} values or {@code double} values,
         * as performed by the numeric and atomic update access modes, differ
         * from the primitive {@code ==} operator and the {@link Float#equals}
         * and {@link Double#equals} methods, specifically with respect to
         * comparing NaN values or comparing {@code -0.0} with {@code +0.0}.
         * Care should be taken when performing a compare and set or a compare
         * and exchange operation with such values since the operation may
         * unexpectedly fail.
         * There are many possible NaN values that are considered to be
         * {@code NaN} in Java, although no IEEE 754 floating-point operation
         * provided by Java can distinguish between them.  Operation failure can
         * occur if the expected or witness value is a NaN value and it is
         * transformed (perhaps in a platform specific manner) into another NaN
         * value, and thus has a different bitwise representation (see
         * {@link Float#intBitsToFloat} or {@link Double#longBitsToDouble} for more
         * details).
         * The values {@code -0.0} and {@code +0.0} have different bitwise
         * representations but are considered equal when using the primitive
         * {@code ==} operator.  Operation failure can occur if, for example, a
         * numeric algorithm computes an expected value to be say {@code -0.0}
         * and previously computed the witness value to be say {@code +0.0}.
         */
        private static VarHandle findVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findVarHandle(owner, name, type);
        }

        /**
         * Produces a VarHandle giving access to a static field {@code name} of
         * type {@code type} declared in a class of type {@code owner}.
         * The VarHandle's variable type is {@code type} and it has no
         * coordinate types.
         * <p>
         * Access checking is performed immediately on behalf of the lookup
         * class.
         * <p>
         * If the returned VarHandle is operated on, the declaring class will be
         * initialized, if it has not already been initialized.
         * <p>
         * Certain access modes of the returned VarHandle are unsupported under
         * the following conditions:
         * <ul>
         * <li>if the field is declared {@code final}, then the write, atomic
         *     update, numeric atomic update, and bitwise atomic update access
         *     modes are unsupported.
         * <li>if the field type is anything other than {@code byte},
         *     {@code short}, {@code char}, {@code int}, {@code long},
         *     {@code float}, or {@code double}, then numeric atomic update
         *     access modes are unsupported.
         * <li>if the field type is anything other than {@code boolean},
         *     {@code byte}, {@code short}, {@code char}, {@code int} or
         *     {@code long} then bitwise atomic update access modes are
         *     unsupported.
         * </ul>
         * <p>
         * If the field is declared {@code volatile} then the returned VarHandle
         * will override access to the field (effectively ignore the
         * {@code volatile} declaration) in accordance to its specified
         * access modes.
         * <p>
         * If the field type is {@code float} or {@code double} then numeric
         * and atomic update access modes compare values using their bitwise
         * representation (see {@link Float#floatToRawIntBits} and
         * {@link Double#doubleToRawLongBits}, respectively).
         *
         * @param owner The class that declares the static field
         * @param name  The field's name
         * @param type  The field's type, of type {@code T}
         * @return a VarHandle giving access to a static field
         * @throws NoSuchFieldException   if the field does not exist
         * @throws IllegalAccessException if access checking fails, or if the field is not {@code static}
         * @throws SecurityException      if a security manager is present and it
         *                                <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
         * @throws NullPointerException   if any argument is null
         * @apiNote Bitwise comparison of {@code float} values or {@code double} values,
         * as performed by the numeric and atomic update access modes, differ
         * from the primitive {@code ==} operator and the {@link Float#equals}
         * and {@link Double#equals} methods, specifically with respect to
         * comparing NaN values or comparing {@code -0.0} with {@code +0.0}.
         * Care should be taken when performing a compare and set or a compare
         * and exchange operation with such values since the operation may
         * unexpectedly fail.
         * There are many possible NaN values that are considered to be
         * {@code NaN} in Java, although no IEEE 754 floating-point operation
         * provided by Java can distinguish between them.  Operation failure can
         * occur if the expected or witness value is a NaN value and it is
         * transformed (perhaps in a platform specific manner) into another NaN
         * value, and thus has a different bitwise representation (see
         * {@link Float#intBitsToFloat} or {@link Double#longBitsToDouble} for more
         * details).
         * The values {@code -0.0} and {@code +0.0} have different bitwise
         * representations but are considered equal when using the primitive
         * {@code ==} operator.  Operation failure can occur if, for example, a
         * numeric algorithm computes an expected value to be say {@code -0.0}
         * and previously computed the witness value to be say {@code +0.0}.
         */
        private static VarHandle findStaticVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticVarHandle(owner, name, type);
        }

        private static VarHandle unreflectVarHandle(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectVarHandle(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private VarHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.VarHandleHelper.class) + callerBlame);
        }
    }

    /**
     * Helper class that simplifies the process of bypassing module access checks <br>
     * (meaning exporting / opening packages to modules they aren't normally exported / opened to).
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class ModuleHelper {
        /**
         * Special module that represents all unnamed modules (see {@link Module#ALL_UNNAMED_MODULE})
         */
        private static final Module ALL_UNNAMED_MODULE;
        /**
         * Special module that represents all modules (see {@link Module#EVERYONE_MODULE})
         */
        private static final Module EVERYONE_MODULE;
        /**
         * Cached constructor for the {@link ModuleLayer.Controller} class
         *
         * @see #getControllerForLayer(ModuleLayer)
         */
        private static final Constructor<ModuleLayer.Controller> LAYER_CONTROLLER_CONSTRUCTOR;
        /**
         * The {@link jdk.internal.access.JavaLangAccess} instance
         */
        private static final JavaLangAccess JAVA_LANG_ACCESS;

        static {

            gainInternalAccess();

            JAVA_LANG_ACCESS = jdk.internal.access.SharedSecrets.getJavaLangAccess();

            // Open the java.lang package to this class' module, so that we can use AccessibleObject#setAccessible(boolean) on the ModuleLayer.Controller constructor
            addOpens(ModuleLayer.Controller.class.getModule(), ModuleLayer.Controller.class.getPackageName(), ModuleHelper.class.getModule());

            try {
                LAYER_CONTROLLER_CONSTRUCTOR = ModuleLayer.Controller.class.getDeclaredConstructor(ModuleLayer.class);
                LAYER_CONTROLLER_CONSTRUCTOR.setAccessible(true);
            } catch (NoSuchMethodException nsme) {
                throw new RuntimeException("Could not find constructor for " + getModuleInclusiveClassName(ModuleLayer.Controller.class), nsme);
            }

            try {
                ALL_UNNAMED_MODULE = (Module) LookupHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "ALL_UNNAMED_MODULE", Module.class).invoke();
                EVERYONE_MODULE = (Module) LookupHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "EVERYONE_MODULE", Module.class).invoke();
            } catch (Throwable t) {
                throw new RuntimeException("Could not find special module instances", t);
            }
        }

        /**
         * Exports the specified package from the specified module to the specified module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be exported to
         */
        private static void addExports(Module source, String packageName, Module target) {
            JAVA_LANG_ACCESS.addExports(source, packageName, target);
        }

        /**
         * Exports the specified package from the specified module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExportsToAllUnnamed(Module source, String packageName) {
            JAVA_LANG_ACCESS.addExportsToAllUnnamed(source, packageName);
        }

        /**
         * Exports the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExports(Module source, String packageName) {
            JAVA_LANG_ACCESS.addExports(source, packageName);
        }

        /**
         * Opens the specified package from the specified module to the specified module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         */
        private static void addOpens(Module source, String packageName, Module target) {
            JAVA_LANG_ACCESS.addOpens(source, packageName, target);
        }

        /**
         * Opens the specified package from the specified module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addOpensToAllUnnamed(Module source, String packageName) {
            JAVA_LANG_ACCESS.addOpensToAllUnnamed(source, packageName);
        }

        /**
         * Opens the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @implNote Since {@link jdk.internal.access.JavaLangAccess} does not expose any methods to unconditionally open a package to all modules,
         * we use the special {@link #EVERYONE_MODULE} instance as if it was any other normal module
         */
        private static void addOpens(Module source, String packageName) {
            addOpens(source, packageName, EVERYONE_MODULE);
        }

        /**
         * Exports the specified package from the specified module to the specified module using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be exported to
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsWithController(Module source, String packageName, Module target) {
            return getControllerForModule(source).addExports(source, packageName, target);
        }

        /**
         * Exports the specified package from the specified module to all unnamed modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsToAllUnnamedWithController(Module source, String packageName) {
            return addExportsWithController(source, packageName, ALL_UNNAMED_MODULE);
        }

        /**
         * Exports the specified package from the specified module to all modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsWithController(Module source, String packageName) {
            return addExportsWithController(source, packageName, EVERYONE_MODULE);
        }

        /**
         * Opens the specified package from the specified module to the specified module using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensWithController(Module source, String packageName, Module target) {
            return getControllerForModule(source).addOpens(source, packageName, target);
        }

        /**
         * Opens the specified package from the specified module to all unnamed modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensToAllUnnamedWithController(Module source, String packageName) {
            return addOpensWithController(source, packageName, ALL_UNNAMED_MODULE);
        }

        /**
         * Opens the specified package from the specified module to all modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensWithController(Module source, String packageName) {
            return addOpensWithController(source, packageName, EVERYONE_MODULE);
        }

        /**
         * This method is only used to trigger classloading for this class, it does nothing on its own.
         */
        private static void bootstrap() {
            // NOOP
        }

        /**
         * Produces a {@link ModuleLayer.Controller} instance that controls the layer the given module belongs to.
         *
         * @param module The module whose layer is to be controlled
         * @return the {@link ModuleLayer.Controller} instance
         * @throws UnsupportedOperationException if {@code module} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller getControllerForModule(Module module) {
            final ModuleLayer layer = module.getLayer();

            if (layer == null) {
                throw new UnsupportedOperationException("Cannot obtain a controller instance for module " + module.getName() + " because it is not in any layer");
            }

            return getControllerForLayer(layer);
        }

        /**
         * Produces a {@link ModuleLayer.Controller} instance that controls the given {@link ModuleLayer}.
         *
         * @param layer The layer to be controlled
         * @return the {@link ModuleLayer.Controller} instance
         */
        private static ModuleLayer.Controller getControllerForLayer(ModuleLayer layer) {
            try {
                return LAYER_CONTROLLER_CONSTRUCTOR.newInstance(layer);
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not create a new instance of " + getModuleInclusiveClassName(ModuleLayer.Controller.class), roe);
            }
        }

        /**
         * Exploits the Proxy API to gain access to the {@link jdk.internal.access} package,
         * which is normally restricted for classes outside the {@code java.base} module.
         */
        @SuppressWarnings({"Java9ReflectionClassVisibility", "SuspiciousInvocationHandlerImplementation"})
        private static void gainInternalAccess() {
            final String javaLangAccessName = "jdk.internal.access.JavaLangAccess";
            final Class<?> javaLangAccessInterface;
            final Class<?> injectorClass;
            final InjectorClassLoader injectorLoader = new InjectorClassLoader();

            try {
                javaLangAccessInterface = Class.forName(javaLangAccessName);

                final Object proxyInstance = Proxy.newProxyInstance(
                        injectorLoader,
                        new Class[]{
                                javaLangAccessInterface
                        },
                        (proxy, method, arguments) -> null
                );

                final String packageName = proxyInstance.getClass().getPackageName().replace(".", "/");

                injectorLoader.defineAndLoad(InjectorGenerator.generateIn(packageName));

            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not gain access to ", roe);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private ModuleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.ModuleHelper.class) + callerBlame);
        }

    }

    /**
     * Helper class that holds references to the {@link sun.misc.Unsafe} and {@link jdk.internal.misc.Unsafe} instances. <br>
     * This class also contains a few methods that make working with the two <code>Unsafe</code> classes easier.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class UnsafeHelper {
        /**
         * The {@link sun.misc.Unsafe} instance
         */
        private static final sun.misc.Unsafe UNSAFE;
        /**
         * The {@link jdk.internal.misc.Unsafe} instance
         */
        private static final jdk.internal.misc.Unsafe INTERNAL_UNSAFE;
        /**
         * The cached offset (in bytes) of the {@link AccessibleObject#override} field in an {@link AccessibleObject} instance
         */
        private static final long OVERRIDE_OFFSET;

        static {

            ModuleHelper.addExports(Object.class.getModule(), "jdk.internal.misc", UnsafeHelper.class.getModule());

            UNSAFE = findUnsafe();
            INTERNAL_UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();

            // Bypass reflection filters by using the jdk.internal.misc.Unsafe instance
            OVERRIDE_OFFSET = INTERNAL_UNSAFE.objectFieldOffset(AccessibleObject.class, "override");
        }

        /**
         * Forces the given {@link AccessibleObject} instance to have the desired accessibility. <br>
         * Unlike {@link AccessibleObject#setAccessible(boolean)}, this method does not perform any permission checks.
         *
         * @param object     The object whose accessibility is to be forcefully set
         * @param accessible the accessibility to be forcefully set
         * @implNote As the name of the method suggests, this makes use of {@link jdk.internal.misc.Unsafe} to bypass any access checks.
         */
        @SuppressWarnings("SameParameterValue")
        private static void unsafeSetAccessible(AccessibleObject object, boolean accessible) {
            INTERNAL_UNSAFE.putBoolean(object, OVERRIDE_OFFSET, accessible);
        }

        /**
         * Gets a reference to the {@link sun.misc.Unsafe} instance without relying on the field name.
         *
         * @return the {@link sun.misc.Unsafe} instance
         */
        private static sun.misc.Unsafe findUnsafe() {
            final int unsafeFieldModifiers = Modifier.STATIC | Modifier.FINAL;
            final List<Throwable> exceptions = new ArrayList<>();

            // We cannot rely on the field name, as it is an implementation detail and as such it can be different from vendor to vendor
            for (Field field : sun.misc.Unsafe.class.getDeclaredFields()) {

                // Verify that the field is of the correct type and has the correct access modifiers
                if (field.getType() != sun.misc.Unsafe.class || (field.getModifiers() & unsafeFieldModifiers) != unsafeFieldModifiers) {
                    continue;
                }

                try {
                    // We don't need to do anything fancy to set the field to be accessible
                    // since the jdk.unsupported module opens the sun.misc package to all modules
                    field.setAccessible(true);
                    if (field.get(null) instanceof sun.misc.Unsafe unsafe) {
                        return unsafe;
                    }
                } catch (Throwable e) {
                    exceptions.add(e);
                }
            }

            // If we couldn't find the field, throw an exception
            final RuntimeException exception = new RuntimeException("Could not find sun.misc.Unsafe instance!");
            exceptions.forEach(exception::addSuppressed);
            throw exception;
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private UnsafeHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.UnsafeHelper.class) + callerBlame);
        }
    }

    /**
     * A custom (and very bare-bones) implementation of {@link ClassLoader} to be used in conjunction with the class generated by {@link InjectorGenerator#generateIn(String)}.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static class InjectorClassLoader extends ClassLoader {
        private InjectorClassLoader() {
            super(InjectorClassLoader.class.getClassLoader());
        }

        private Class<?> define(byte[] data) {
            return defineClass(null, data, 0, data.length, null);
        }

        @SuppressWarnings("UnusedReturnValue")
        private Class<?> defineAndLoad(byte[] data) throws ClassNotFoundException {
            return Class.forName(define(data).getName(), true, this);
        }
    }

    /**
     * Helper class that generates an {@code Injector} class inside the given package.
     * <p>
     * The source of the generated class is as follows:
     * <blockquote><pre>{@code
     * public class Injector {
     *     static {
     *         final Class<?> injectorClass = Injector.class;
     *         final Class<?> loaderClass = injectorClass.getClassLoader().getClass();
     *         final Module javaBaseModule = Object.class.getModule();
     *         final Module loaderModule = loaderClass.getModule();
     *         // Obtain the jdk.internal.access.JavaLangAccess instance via jdk.internal.access.SharedSecrets
     *         final jdk.internal.access.JavaLangAccess javaLangAccess = jdk.internal.access.SharedSecrets.getJavaLangAccess();
     *
     *         // Export the jdk.internal.access package to the loader module to enable jdk.internal.access.JavaLangAccess access
     *         javaLangAccess.addExports(javaBaseModule, "jdk.internal.access", loaderModule);
     *     }
     * }
     * }</pre></blockquote>
     * <p>
     * In conjunction with {@link InjectorClassLoader}, this is used to define and load a class inside a proxy module which has access to the {@link jdk.internal.access} package.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static class InjectorGenerator {
        /**
         * The name of the generated injector class
         */
        private static final String INJECTOR_CLASS_NAME = "Injector";

        /**
         * Generates an injector class inside the package with the given name.
         *
         * @param packageName The name of the package the class should be generated in
         * @return the generated injector class' bytes
         */
        public static byte[] generateIn(final String packageName) {
            final String fullClassName = packageName + "/" + INJECTOR_CLASS_NAME;
            final String descriptor = "L" + fullClassName + ";";
            final String ownPackageName = InjectorGenerator.class.getPackageName();

            final ClassWriter classWriter = new ClassWriter(0);
            MethodVisitor methodVisitor;

            classWriter.visit(
                    Opcodes.V17,
                    Opcodes.ACC_SUPER,
                    fullClassName,
                    null,
                    "java/lang/Object",
                    null
            );

            // Default constructor bytecode (<init>()V)
            {
                methodVisitor = classWriter.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>",
                        "()V",
                        null,
                        null
                );

                methodVisitor.visitCode();

                Label label0 = new Label();
                methodVisitor.visitLabel(label0);

                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        0
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/Object",
                        "<init>",
                        "()V",
                        false
                );
                methodVisitor.visitInsn(
                        Opcodes.RETURN
                );

                Label label1 = new Label();
                methodVisitor.visitLabel(label1);

                methodVisitor.visitLocalVariable(
                        "this",
                        descriptor,
                        null,
                        label0,
                        label1,
                        0
                );

                methodVisitor.visitMaxs(1, 1);

                methodVisitor.visitEnd();
            }

            // Static class initializer bytecode (<clinit>()V)
            {
                methodVisitor = classWriter.visitMethod(
                        Opcodes.ACC_STATIC,
                        "<clinit>",
                        "()V",
                        null,
                        null
                );

                methodVisitor.visitCode();

                Label label0 = new Label();
                methodVisitor.visitLabel(label0);

                methodVisitor.visitLdcInsn(
                        Type.getType(descriptor)
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        0
                );

                Label label1 = new Label();
                methodVisitor.visitLabel(label1);

                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        0
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/Class",
                        "getClassLoader",
                        "()Ljava/lang/ClassLoader;",
                        false
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/Object",
                        "getClass",
                        "()Ljava/lang/Class;",
                        false
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE, 1);

                Label label2 = new Label();
                methodVisitor.visitLabel(label2);

                methodVisitor.visitLdcInsn(
                        Type.getType("Ljava/lang/Object;")
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/Class",
                        "getModule",
                        "()Ljava/lang/Module;",
                        false
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        2
                );

                Label label3 = new Label();
                methodVisitor.visitLabel(label3);

                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        1
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/Class",
                        "getModule",
                        "()Ljava/lang/Module;",
                        false
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        3
                );

                Label label4 = new Label();
                methodVisitor.visitLabel(label4);

                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "jdk/internal/access/SharedSecrets",
                        "getJavaLangAccess",
                        "()Ljdk/internal/access/JavaLangAccess;",
                        false
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        4
                );

                Label label5 = new Label();
                methodVisitor.visitLabel(label5);

                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        4
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        2
                );
                methodVisitor.visitLdcInsn(
                        "jdk.internal.access"
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        3
                );
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEINTERFACE,
                        "jdk/internal/access/JavaLangAccess",
                        "addExports",
                        "(Ljava/lang/Module;Ljava/lang/String;Ljava/lang/Module;)V",
                        true
                );

                Label label6 = new Label();
                methodVisitor.visitLabel(label6);

                methodVisitor.visitInsn(
                        Opcodes.RETURN
                );

                methodVisitor.visitLocalVariable(
                        "injectorClass",
                        "Ljava/lang/Class;",
                        "Ljava/lang/Class<*>;",
                        label1,
                        label6,
                        0
                );
                methodVisitor.visitLocalVariable(
                        "loaderClass",
                        "Ljava/lang/Class;",
                        "Ljava/lang/Class<*>;",
                        label2,
                        label6,
                        1
                );
                methodVisitor.visitLocalVariable(
                        "javaBaseModule",
                        "Ljava/lang/Module;",
                        null,
                        label3,
                        label6,
                        2
                );
                methodVisitor.visitLocalVariable(
                        "loaderModule",
                        "Ljava/lang/Module;",
                        null,
                        label4,
                        label6,
                        3
                );
                methodVisitor.visitLocalVariable(
                        "javaLangAccess",
                        "Ljdk/internal/access/JavaLangAccess;",
                        null,
                        label5,
                        label6,
                        4
                );

                methodVisitor.visitMaxs(4, 5);
                methodVisitor.visitEnd();
            }
            classWriter.visitEnd();

            return classWriter.toByteArray();
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private InjectorGenerator() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.InjectorGenerator.class) + callerBlame);
        }
    }

}
