package me.lollopollqo.sus.injection.util;

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
 * A collection of hacks and utilities for easier / enhanced usage of the reflection and invocation APIs.
 *
 * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
 */
@SuppressWarnings({"unused", "Java9ReflectionClassVisibility"})
public final class ReflectionUtils {
    /**
     * Cached {@link StackWalker} instance for caller checking
     */
    private static final StackWalker STACK_WALKER;
    /**
     * Cached {@link VarHandle} for the cached reflection data inside {@link Class} instances
     *
     * @see #clearReflectionCacheForClass(Class)
     */
    private static final VarHandle reflectionCacheHandle;
    /**
     * Cached {@link VarHandle} for {@link jdk.internal.reflect.Reflection#fieldFilterMap}
     *
     * @see #clearReflectionFiltersForClass(Class)
     */
    private static final VarHandle fieldFilterMapHandle;
    /**
     * Cached {@link VarHandle} for {@link jdk.internal.reflect.Reflection#methodFilterMap}
     *
     * @see #clearReflectionFiltersForClass(Class)
     */
    private static final VarHandle methodFilterMapHandle;
    /**
     * Cached {@link MethodHandle} with {@code invokespecial} behavior for {@link AccessibleObject#setAccessible(boolean)}
     *
     * @see #setAccessible(AccessibleObject, boolean)
     */
    private static final MethodHandle setAccessibleHandle;

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

            tempReflectionCacheHandle = ReflectionUtils.findVarHandle(Class.class, fieldName, fieldType);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get VarHandle for the cached reflection data", roe);
        }

        reflectionCacheHandle = tempReflectionCacheHandle;

        final String reflectionClassName = "jdk.internal.reflect.Reflection";
        final String fieldFilterMap = "fieldFilterMap";
        final String methodFilterMap = "methodFilterMap";

        try {
            final Class<?> reflectionClass = Class.forName(reflectionClassName);

            fieldFilterMapHandle = ReflectionUtils.findStaticVarHandle(reflectionClass, fieldFilterMap, Map.class);
            methodFilterMapHandle = ReflectionUtils.findStaticVarHandle(reflectionClass, methodFilterMap, Map.class);
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Could not locate " + reflectionClassName, cnfe);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get VarHandles for " + reflectionClassName + "#" + fieldFilterMap + " and " + reflectionClassName + "#" + methodFilterMap, roe);
        }

        final String setAccessible = "setAccessible";

        try {
            setAccessibleHandle = ReflectionUtils.findSpecial(AccessibleObject.class, setAccessible, AccessibleObject.class, void.class, boolean.class);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException("Could not get MethodHandle with invokespecial behavior for " + AccessibleObject.class.getName() + "#" + setAccessible + "(boolean)", roe);
        }

    }

    /**
     * Gets a field by name and makes it accessible. <br>
     * Note that this method still has the same restrictions as {@link Class#getDeclaredField(String)}
     * in regard to what methods it can find (see {@link jdk.internal.reflect.Reflection#filterFields(Class, Field[])}).
     * If you want to be sure the field is visible, you should either use {@link #getAccessibleDeclaredFieldNoFilter(Class, String)}
     * or call {@link #ensureUnfilteredReflection(Class)} on {@code owner} yourself before invoking this method.
     *
     * @param owner The class or interface from which the field is accessed
     * @param name  The name of the field
     * @return the {@link Field} object for the specified field in {@code owner}
     * @throws NoSuchFieldException if a field with the specified name could not be found in {@code owner}
     * @see #getAccessibleDeclaredFieldNoFilter(Class, String)
     * @see #setAccessible(AccessibleObject, boolean)
     */
    public static Field getAccessibleDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        ReflectionUtils.setAccessible(field, true);
        return field;
    }

    /**
     * Gets a method by name and parameter types and makes it accessible. <br>
     * Note that this method still has the same restrictions as {@link Class#getDeclaredMethod(String, Class[])}
     * in regard to what methods it can find (see {@link jdk.internal.reflect.Reflection#filterMethods(Class, Method[])}).
     * If you want to be sure the field is visible, you should either use {@link #getAccessibleDeclaredMethodNoFilter(Class, String, Class[])}
     * or call {@link #ensureUnfilteredReflection(Class)} on {@code owner} yourself before invoking this method.
     *
     * @param owner      The class or interface from which the method is accessed
     * @param name       The name of the method
     * @param paramTypes The types of the parameters of the method, in order
     * @return the {@link Method} with the specified owner, name and parameter types
     * @throws NoSuchMethodException if a method with the specified name and parameter types could not be found in {@code owner}
     * @see #getAccessibleDeclaredMethodNoFilter(Class, String, Class[])
     * @see #setAccessible(AccessibleObject, boolean)
     */
    public static Method getAccessibleDeclaredMethod(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, paramTypes);
        ReflectionUtils.setAccessible(method, true);
        return method;
    }

    /**
     * Gets a field by name and makes it accessible. <br>
     * Unlike {@link #getAccessibleDeclaredMethod(Class, String, Class[])}, this method will bypass reflection filters.
     *
     * @param owner The class or interface from which the field is accessed
     * @param name  The name of the field
     * @return the {@link Field} with the specified owner and name
     * @throws NoSuchFieldException if a field with the specified name could not be found in the specified class
     * @see #getAccessibleDeclaredMethod(Class, String, Class[])
     * @see #setAccessible(AccessibleObject, boolean)
     */
    public static Field getAccessibleDeclaredFieldNoFilter(Class<?> owner, String name) throws NoSuchFieldException {
        ReflectionUtils.ensureUnfilteredReflection(owner);
        return ReflectionUtils.getAccessibleDeclaredField(owner, name);
    }

    /**
     * Gets a method by name and makes it accessible. <br>
     * Unlike {@link #getAccessibleDeclaredMethod(Class, String, Class[])}, this method will bypass reflection filters.
     *
     * @param owner      The class or interface from which the method is accessed
     * @param name       The name of the method
     * @param paramTypes The types of the parameters of the method, in order
     * @return the {@link Method} with the specified owner, name and parameter types
     * @throws NoSuchMethodException if a method with the specified name and parameter types could not be found in the specified class
     * @see #getAccessibleDeclaredMethod(Class, String, Class[])
     * @see #setAccessible(AccessibleObject, boolean)
     */
    public static Method getAccessibleDeclaredMethodNoFilter(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        ReflectionUtils.ensureUnfilteredReflection(owner);
        return ReflectionUtils.getAccessibleDeclaredMethod(owner, name, paramTypes);
    }

    /**
     * Gets <em>all</em> declared fields of a class, bypassing reflection filters.
     * This is done by first disabling all reflection filters for the class, then clearing the cached reflection data.
     * Lastly, a regular invocation of {@link Class#getDeclaredFields()} is performed, and that call will be able to see all the declared fields,
     * without any filters. An invocation of this method on class {@code owner} will cause all subsequent calls to {@link Class#getDeclaredFields()}
     * on {@code owner} to return the full, unfiltered list of declared fields.
     *
     * @param owner The class in which the fields are declared
     * @return The unfiltered list of declared fields
     * @see #getAllDeclaredMethodsOfClass(Class)
     * @see jdk.internal.reflect.Reflection#registerFieldsToFilter(Class, Set)
     * @see jdk.internal.reflect.Reflection#filterFields(Class, Field[])
     */
    public static Field[] getAllDeclaredFieldsOfClass(Class<?> owner) {
        ReflectionUtils.ensureUnfilteredReflection(owner);
        return owner.getDeclaredFields();
    }

    /**
     * Gets <em>all</em> declared methods of a class, bypassing reflection filters.
     * This is done by first disabling all reflection filters for the class, then clearing the cached reflection data.
     * Lastly, a regular invocation of {@link Class#getDeclaredMethods()} is performed, and that call will be able to see all the declared fields,
     * without any filters. An invocation of this method on class {@code owner} will cause all subsequent calls to {@link Class#getDeclaredFields()}
     * on {@code owner} to return the full, unfiltered list of declared fields.
     *
     * @param owner The class in which the methods are declared
     * @return The unfiltered list of declared methods
     * @see #getAllDeclaredFieldsOfClass(Class)
     * @see jdk.internal.reflect.Reflection#registerMethodsToFilter(Class, Set)
     * @see jdk.internal.reflect.Reflection#filterMethods(Class, Method[])
     */
    public static Method[] getAllDeclaredMethodsOfClass(Class<?> owner) {
        ReflectionUtils.ensureUnfilteredReflection(owner);
        return owner.getDeclaredMethods();
    }

    /**
     * Ensures any reflective operations on the given class will not be affected by reflection filters.
     *
     * @param clazz The class on which reflective operations will be performed
     */
    public static void ensureUnfilteredReflection(Class<?> clazz) {
        ReflectionUtils.clearReflectionFiltersForClass(clazz);
        ReflectionUtils.clearReflectionCacheForClass(clazz);
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
     * @see jdk.internal.reflect.Reflection#filterFields(Class, Field[])
     * @see jdk.internal.reflect.Reflection#filterMethods(Class, Method[])
     */
    @SuppressWarnings("unchecked")
    public static void clearReflectionFiltersForClass(Class<?> clazz) {
        final Map<Class<?>, Set<String>> originalFieldFilterMap = (Map<Class<?>, Set<String>>) fieldFilterMapHandle.getVolatile();
        final Map<Class<?>, Set<String>> originalMethodFilterMap = (Map<Class<?>, Set<String>>) methodFilterMapHandle.getVolatile();

        final Map<Class<?>, Set<String>> newFieldFilterMap;
        final Map<Class<?>, Set<String>> newMethodFilterMap;

        if (originalFieldFilterMap.containsKey(clazz)) {
            newFieldFilterMap = new HashMap<>(originalFieldFilterMap);
            newFieldFilterMap.remove(clazz);
            fieldFilterMapHandle.setVolatile(newFieldFilterMap);
        }

        if (originalMethodFilterMap.containsKey(clazz)) {
            newMethodFilterMap = new HashMap<>(originalMethodFilterMap);
            newMethodFilterMap.remove(clazz);
            methodFilterMapHandle.setVolatile(newMethodFilterMap);
        }
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility by (ab)using {@code invokespecial} behavior,
     * bypassing access checks.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     */
    public static void setAccessible(AccessibleObject object, boolean accessible) {
        try {
            setAccessibleHandle.invoke(object, accessible);
        } catch (Throwable t) {
            throw new RuntimeException("Could not force the accessibility of " + object + " to be set to " + accessible, t);
        }
    }

    /**
     * Exports the package with the given name from the {@code source} module to the {@code target} module.
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
     * Exports the package with the given name from the {@code source} module to all <em>unnamed</em> modules.
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
     * Exports the package with the given name from the {@code source} module to all modules.
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
     * Opens the package with the given name from the {@code source} module to the {@code target} module.
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
     * Opens the package with the given name from the {@code source} module to all <em>unnamed</em> modules.
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
     * Opens the package with the given name from the {@code source} module to <em>all</em> modules.
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
     * Gets the value of the field with the specified {@code name} and {@code type} for the given object.
     *
     * @param owner The object that owns the field value
     * @param name  The name of the field
     * @param type  The type of the field
     * @return the value of the field for {@code owner}
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object owner, String name, Class<T> type) {
        try {
            return (T) ReflectionUtils.findGetter(owner.getClass(), name, type).invoke(owner);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get field " + name + " from " + getModuleInclusiveClassName(owner.getClass()), e);
        }
    }

    /**
     * Invokes the non-static method with the given name, parameter types and return type on the given object.
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
        return (T) ReflectionUtils.invokeNonStatic(owner, name, MethodType.methodType(returnType, argumentTypes), arguments);
    }

    /**
     * Invokes the non-static method with the given name, parameter types and return type on the given object.
     *
     * @param owner     The object to invoke the method on
     * @param name      The name of the method
     * @param type      The type of the method
     * @param arguments The arguments to use for the method invocation
     * @return the value returned by the method, as an {@link Object}
     */
    public static Object invokeNonStatic(Object owner, String name, MethodType type, Object... arguments) {
        try {
            return LookupHelper.LOOKUP.bind(owner, name, type).invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke " + getModuleInclusiveClassName(owner.getClass()) + "." + name + type, e);
        }
    }

    /**
     * Produces a method handle for a virtual method. <br>
     * See the documentation for {@link MethodHandles.Lookup#findVirtual(Class, String, MethodType)} for details.
     *
     * @param owner          The class or interface from which the method is accessed
     * @param name           The name of the method
     * @param returnType     The return type of the method
     * @param parameterTypes The parameter types of the method, in order
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     * @see #findVirtual(Class, String, MethodType)
     */
    public static MethodHandle findVirtual(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return ReflectionUtils.findVirtual(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    /**
     * Produces a method handle for a virtual method. <br>
     * See the documentation for {@link MethodHandles.Lookup#findVirtual(Class, String, MethodType)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The name of the method
     * @param type  The type of the method, with the receiver argument omitted
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findVirtual(owner, name, type);
    }

    /**
     * Produces a method handle for a virtual method, bound to the given {@code instance}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findVirtual(Class, String, MethodType)} for details.
     *
     * @param owner          The class or interface from which the method is accessed
     * @param name           The name of the method
     * @param returnType     The return type of the method
     * @param parameterTypes The parameter types of the method, in order
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     * @see #findVirtualAndBind(Class, Object, String, MethodType)
     */
    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return ReflectionUtils.findVirtualAndBind(owner, instance, name, MethodType.methodType(returnType, parameterTypes));
    }

    /**
     * Produces a method handle for a virtual method, bound to the given {@code instance}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findVirtual(Class, String, MethodType)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The name of the method
     * @param type  The type of the method, with the receiver argument omitted
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, MethodType type) throws ReflectiveOperationException {
        return ReflectionUtils.findVirtual(owner, name, type).bindTo(instance);
    }

    /**
     * Produces a method handle for a static method. <br>
     * See the documentation for {@link MethodHandles.Lookup#findStatic(Class, String, MethodType)} for details.
     *
     * @param owner          The class from which the method is accessed
     * @param name           The name of the method
     * @param parameterTypes The parameter types of the method, in order
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is not {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle findStatic(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return ReflectionUtils.findStatic(owner, name, MethodType.methodType(returnType, parameterTypes));
    }

    /**
     * Produces a method handle for a static method. <br>
     * See the documentation for {@link MethodHandles.Lookup#findStatic(Class, String, MethodType)} for details.
     *
     * @param owner The class from which the method is accessed
     * @param name  The name of the method
     * @param type  The type of the method
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is not {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle findStatic(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStatic(owner, name, type);
    }

    /**
     * Produces a method handle which creates an object and initializes it, using the constructor of the specified type. <br>
     * See the documentation for {@link MethodHandles.Lookup#findConstructor(Class, MethodType)} for details.
     *
     * @param owner          The class or interface from which the method is accessed
     * @param parameterTypes The parameter types of the constructor, in order
     * @return the desired method handle
     * @throws ReflectiveOperationException if the constructor does not exist, if access checking fails or
     *                                      if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     * @see #findConstructor(Class, MethodType)
     */
    public static MethodHandle findConstructor(Class<?> owner, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return ReflectionUtils.findConstructor(owner, MethodType.methodType(void.class, parameterTypes));
    }

    /**
     * Produces a method handle which creates an object and initializes it, using the constructor of the specified type. <br>
     * See the documentation for {@link MethodHandles.Lookup#findConstructor(Class, MethodType)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param type  The type of the method, with the receiver argument omitted, and a void return type
     * @return the desired method handle
     * @throws ReflectiveOperationException if the constructor does not exist, if access checking fails or
     *                                      if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle findConstructor(Class<?> owner, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findConstructor(owner, type);
    }

    /**
     * Produces an early-bound method handle for a virtual method. <br>
     * It will bypass checks for overriding methods on the receiver, as if called from an {@code invokespecial} instruction
     * from within the explicitly specified {@code specialCaller}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findSpecial(Class, String, MethodType, Class)} for details.
     *
     * @param owner          The class or interface from which the method is accessed
     * @param name           The name of the method (which must not be "&lt;init&gt;")
     * @param specialCaller  The proposed calling class to perform the {@code invokespecial}
     * @param returnType     The return type of the method
     * @param parameterTypes The parameter types of the method, in order
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     * @see #findSpecial(Class, String, Class, MethodType)
     */
    public static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, Class<?> returnType, Class<?>... parameterTypes) throws ReflectiveOperationException {
        return ReflectionUtils.findSpecial(owner, name, specialCaller, MethodType.methodType(returnType, parameterTypes));
    }

    /**
     * Produces an early-bound method handle for a virtual method. <br>
     * It will bypass checks for overriding methods on the receiver, as if called from an {@code invokespecial} instruction
     * from within the explicitly specified {@code specialCaller}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findSpecial(Class, String, MethodType, Class)} for details.
     *
     * @param owner         The class or interface from which the method is accessed
     * @param name          The name of the method (which must not be "&lt;init&gt;")
     * @param specialCaller The proposed calling class to perform the {@code invokespecial}
     * @param type          The type of the method, with the receiver argument omitted
     * @return the desired method handle
     * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.findSpecial(owner, name, specialCaller, type);
    }

    /**
     * Produces a method handle giving read access to a non-static field. <br>
     * See the documentation for {@link MethodHandles.Lookup#findGetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static}
     */
    public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findGetter(owner, name, type);
    }

    /**
     * Produces a method handle giving read access to a non-static field, bound to the given {@code instance}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findGetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field for {@code instance}
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static}
     */
    public static <T, O extends T> MethodHandle findGetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return ReflectionUtils.findGetter(owner, name, type).bindTo(instance);
    }

    /**
     * Produces a method handle giving read access to a static field. <br>
     * See the documentation for {@link MethodHandles.Lookup#findStaticGetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is not {@code static}
     */
    public static MethodHandle findStaticGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStaticGetter(owner, name, type);
    }

    /**
     * Produces a method handle giving write access to a non-static field. <br>
     * See the documentation for {@link MethodHandles.Lookup#findSetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can store values into the field
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static} or {@code final}
     */
    public static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findSetter(owner, name, type);
    }

    /**
     * Produces a method handle giving write access to a non-static field, bound to the given {@code instance}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findSetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can store values into the field for {@code instance}
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static} or {@code final}
     */
    public static <T, O extends T> MethodHandle findSetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return ReflectionUtils.findSetter(owner, name, type).bindTo(instance);
    }

    /**
     * Produces a method handle giving write access to a static field. <br>
     * See the documentation for {@link MethodHandles.Lookup#findStaticSetter(Class, String, Class)} for details.
     *
     * @param owner The class or interface from which the method is accessed
     * @param name  The field's name
     * @param type  The field's type
     * @return a method handle which can load values from the field
     * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is not {@code static} or is {@code final}
     */
    public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.findStaticSetter(owner, name, type);
    }

    /**
     * Produces a VarHandle giving access to a non-static field {@code name} of
     * type {@code type} declared in a class of type {@code owner}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findVarHandle(Class, String, Class)} for details.
     *
     * @param owner The class that declares the field
     * @param name  The field's name
     * @param type  The field's type
     * @return a VarHandle giving access to non-static fields
     * @throws ReflectiveOperationException if the field cannot be found, can be found but is static, or if access checking fails
     */
    public static VarHandle findVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return VarHandleHelper.findVarHandle(owner, name, type);
    }

    /**
     * Produces a VarHandle giving access to a static field {@code name} of
     * type {@code type} declared in a class of type {@code owner}. <br>
     * See the documentation for {@link MethodHandles.Lookup#findStaticVarHandle(Class, String, Class)} for details.
     *
     * @param owner The class that declares the static field
     * @param name  The field's name
     * @param type  The field's type
     * @return a VarHandle giving access to a static field
     * @throws ReflectiveOperationException if the field cannot be found, can be found but is not static, or if access checking fails
     */
    public static VarHandle findStaticVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return VarHandleHelper.findStaticVarHandle(owner, name, type);
    }

    /**
     * Makes a direct method handle to m. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflect(Method)} for details.
     *
     * @param m The reflected method
     * @return a method handle which can invoke the reflected method
     * @throws ReflectiveOperationException if access checking fails or if the method's variable arity modifier bit is set
     *                                      and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle unreflect(Method m) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflect(m);
    }

    /**
     * Produces a method handle for a reflected constructor. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflectConstructor(Constructor)} for details.
     *
     * @param c The reflected constructor
     * @return a method handle which can invoke the reflected constructor
     * @throws ReflectiveOperationException if access checking fails or if the method's variable arity modifier bit is set
     *                                      and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle unreflectConstructor(Constructor<?> c) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectConstructor(c);
    }

    /**
     * Produces a method handle for a reflected method. <br>
     * It will bypass checks for overriding methods on the receiver, as if called
     * from an {@code invokespecial} instruction from within the explicitly specified specialCaller. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflectSpecial(Method, Class)} for details.
     *
     * @param m             The reflected method
     * @param specialCaller The class nominally calling the method
     * @return a method handle which can invoke the reflected method
     * @throws ReflectiveOperationException if access checking fails, or if the method is {@code static},
     *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
     */
    public static MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectSpecial(m, specialCaller);
    }

    /**
     * Produces a method handle giving read access to a reflected field. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflectGetter(Field)} for details.
     *
     * @param f The reflected field
     * @return a method handle which can load values from the reflected field
     * @throws ReflectiveOperationException if access checking fails
     */
    public static MethodHandle unreflectGetter(Field f) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectGetter(f);
    }

    /**
     * Produces a method handle giving write access to a reflected field. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflectSetter(Field)} for details.
     *
     * @param f The reflected field
     * @return a method handle which can store values into the reflected field
     * @throws ReflectiveOperationException if access checking fails, or if the field is {@code final}
     *                                      and write access is not enabled on the {@code Field} object
     */
    public static MethodHandle unreflectSetter(Field f) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectSetter(f);
    }

    /**
     * Produces a VarHandle giving access to a reflected field {@code f}. <br>
     * See the documentation for {@link MethodHandles.Lookup#unreflectVarHandle(Field)} for details.
     *
     * @param f The reflected field
     * @return a {@link VarHandle} giving access to non-static fields or a static field
     * @throws ReflectiveOperationException if access checking fails
     */
    public static VarHandle unreflectVarHandle(Field f) throws ReflectiveOperationException {
        return VarHandleHelper.unreflectVarHandle(f);
    }

    /**
     * Attempts to load the class with the given name using this class' class loader.
     *
     * @param name The binary name of the class
     * @return the loaded class
     * @throws ClassNotFoundException if a class with the given {@code name} could not be found
     * @see #loadClass(String, ClassLoader)
     */
    public static <T> Class<T> loadClass(@NotNull String name) throws ClassNotFoundException {
        return ReflectionUtils.loadClass(name, ReflectionUtils.class.getClassLoader());
    }

    /**
     * Attempts to load the class with the given name using the system class loader.
     *
     * @param name The binary name of the class
     * @return the loaded class
     * @throws ClassNotFoundException if a class with the given {@code name} could not be found
     * @see #loadClass(String, ClassLoader)
     */
    public static <T> Class<T> loadClassWithSystemLoader(@NotNull String name) throws ClassNotFoundException {
        return ReflectionUtils.loadClass(name, ClassLoader.getSystemClassLoader());
    }

    /**
     * Attempts to load the class with the given name using the given {@link ClassLoader}.
     *
     * @param name   The binary name of the class
     * @param loader The class loader to use for loading the class
     * @return the loaded class
     * @throws ClassNotFoundException if a class with the given {@code name} could not be found
     * @see ClassLoader#loadClass(String)
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String name, ClassLoader loader) throws ClassNotFoundException {
        return (Class<T>) loader.loadClass(name);
    }

    /**
     * Ensures the specified classes are initialized.
     *
     * @param classes The classes to be initialized
     * @return an array containing the initialized classes
     * @see #ensureInitialized(Class)
     */
    @NotNull
    public static Class<?>[] ensureInitialized(@NotNull Class<?> @NotNull ... classes) {
        for (Class<?> clazz : classes) {
            ReflectionUtils.ensureInitialized(clazz);
        }
        return classes;
    }

    /**
     * Ensures that {@code clazz} has been initialized.
     *
     * @param clazz The class to be initialized
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
     * Gets the <em>full</em> name of the specified class, including the name of the module it's defined in.
     *
     * @param clazz The class whose name is to be determined
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
            callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
        } catch (IllegalCallerException ignored) {

        }
        throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.class) + callerBlame);
    }

    /**
     * Helper class that makes working with {@link MethodHandle}s easier.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class MethodHandleHelper {

        /**
         * Internal helper for {@link ReflectionUtils#findGetter(Class, String, Class)}
         * <p>
         * Produces a method handle giving read access to a non-static field. <br>
         * See the documentation for {@link MethodHandles.Lookup#findGetter(Class, String, Class)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can load values from the field
         * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static}
         */
        public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findGetter(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findStaticGetter(Class, String, Class)}
         * <p>
         * Produces a method handle giving read access to a static field. <br>
         * See the documentation for {@link MethodHandles.Lookup#findStaticGetter(Class, String, Class)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can load values from the field
         * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is not {@code static}
         */
        public static MethodHandle findStaticGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticGetter(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findSetter(Class, String, Class)}
         * <p>
         * Produces a method handle giving write access to a non-static field. <br>
         * See the documentation for {@link MethodHandles.Lookup#findSetter(Class, String, Class)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can store values into the field
         * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is {@code static} or {@code final}
         */
        private static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findSetter(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findStaticSetter(Class, String, Class)}
         * <p>
         * Produces a method handle giving write access to a static field. <br>
         * See the documentation for {@link MethodHandles.Lookup#findStaticSetter(Class, String, Class)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The field's name
         * @param type  The field's type
         * @return a method handle which can load values from the field
         * @throws ReflectiveOperationException if the field does not exist, if access checking fails, or if the field is not {@code static} or is {@code final}
         */
        private static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticSetter(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findVirtual(Class, String, MethodType)}
         * <p>
         * Produces a method handle for a virtual method. <br>
         * See the documentation for {@link MethodHandles.Lookup#findVirtual(Class, String, MethodType)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param name  The name of the method
         * @param type  The type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
         *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findVirtual(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findStatic(Class, String, MethodType)}
         * <p>
         * Produces a method handle for a static method. <br>
         * See the documentation for {@link MethodHandles.Lookup#findStatic(Class, String, MethodType)} for details.
         *
         * @param owner The class from which the method is accessed
         * @param name  The name of the method
         * @param type  The type of the method
         * @return the desired method handle
         * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is not {@code static},
         *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle findStatic(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStatic(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findSpecial(Class, String, Class, MethodType)}
         * <p>
         * Produces an early-bound method handle for a virtual method. <br>
         * It will bypass checks for overriding methods on the receiver, as if called from an {@code invokespecial} instruction
         * from within the explicitly specified {@code specialCaller}. <br>
         * See the documentation for {@link MethodHandles.Lookup#findSpecial(Class, String, MethodType, Class)} for details.
         *
         * @param owner         The class or interface from which the method is accessed
         * @param name          The name of the method (which must not be "&lt;init&gt;")
         * @param specialCaller The proposed calling class to perform the {@code invokespecial}
         * @param type          The type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @throws ReflectiveOperationException if the method does not exist, if access checking fails, if the method is {@code static},
         *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findSpecial(owner, name, type, specialCaller);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findConstructor(Class, MethodType)}
         * <p>
         * Produces a method handle which creates an object and initializes it, using the constructor of the specified type. <br>
         * See the documentation for {@link MethodHandles.Lookup#findConstructor(Class, MethodType)} for details.
         *
         * @param owner The class or interface from which the method is accessed
         * @param type  The type of the method, with the receiver argument omitted, and a void return type
         * @return the desired method handle
         * @throws ReflectiveOperationException if the constructor does not exist, if access checking fails or
         *                                      if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle findConstructor(Class<?> owner, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findConstructor(owner, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflect(Method)}
         * <p>
         * Makes a direct method handle to m. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflect(Method)} for details.
         *
         * @param m The reflected method
         * @return a method handle which can invoke the reflected method
         * @throws ReflectiveOperationException if access checking fails or if the method's variable arity modifier bit is set
         *                                      and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle unreflect(Method m) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflect(m);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflectSpecial(Method, Class)}
         * <p>
         * Produces a method handle for a reflected method. <br>
         * It will bypass checks for overriding methods on the receiver, as if called
         * from an {@code invokespecial} instruction from within the explicitly specified specialCaller. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflectSpecial(Method, Class)} for details.
         *
         * @param m             The reflected method
         * @param specialCaller The class nominally calling the method
         * @return a method handle which can invoke the reflected method
         * @throws ReflectiveOperationException if access checking fails, or if the method is {@code static},
         *                                      or if the method's variable arity modifier bit is set and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectSpecial(m, specialCaller);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflectConstructor(Constructor)}
         * <p>
         * Produces a method handle for a reflected constructor. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflectConstructor(Constructor)} for details.
         *
         * @param c The reflected constructor
         * @return a method handle which can invoke the reflected constructor
         * @throws ReflectiveOperationException if access checking fails or if the method's variable arity modifier bit is set
         *                                      and {@link MethodHandle#asVarargsCollector(Class)} fails
         */
        private static MethodHandle unreflectConstructor(Constructor<?> c) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectConstructor(c);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflectGetter(Field)}
         * <p>
         * Produces a method handle giving read access to a reflected field. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflectGetter(Field)} for details.
         *
         * @param f The reflected field
         * @return a method handle which can load values from the reflected field
         * @throws ReflectiveOperationException if access checking fails
         */
        private static MethodHandle unreflectGetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectGetter(f);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflectSetter(Field)}
         * <p>
         * Produces a method handle giving write access to a reflected field. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflectSetter(Field)} for details.
         *
         * @param f The reflected field
         * @return a method handle which can store values into the reflected field
         * @throws ReflectiveOperationException if access checking fails, or if the field is {@code final}
         *                                      and write access is not enabled on the {@code Field} object
         */
        private static MethodHandle unreflectSetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectSetter(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private MethodHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.MethodHandleHelper.class) + callerBlame);
        }
    }

    /**
     * Helper class that makes working with {@link VarHandle}s easier.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class VarHandleHelper {

        /**
         * Internal helper for {@link ReflectionUtils#findVarHandle(Class, String, Class)}
         * <p>
         * Produces a VarHandle giving access to a non-static field {@code name} of
         * type {@code type} declared in a class of type {@code owner}. <br>
         * See the documentation for {@link MethodHandles.Lookup#findVarHandle(Class, String, Class)} for details.
         *
         * @param owner The class that declares the field
         * @param name  The field's name
         * @param type  The field's type
         * @return a VarHandle giving access to non-static fields
         * @throws ReflectiveOperationException if the field cannot be found, can be found but is static, or if access checking fails
         */
        private static VarHandle findVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findVarHandle(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#findStaticVarHandle(Class, String, Class)}
         * <p>
         * Produces a VarHandle giving access to a static field {@code name} of
         * type {@code type} declared in a class of type {@code owner}. <br>
         * See the documentation for {@link MethodHandles.Lookup#findStaticVarHandle(Class, String, Class)} for details.
         *
         * @param owner The class that declares the static field
         * @param name  The field's name
         * @param type  The field's type
         * @return a VarHandle giving access to a static field
         * @throws ReflectiveOperationException if the field cannot be found, can be found but is not static, or if access checking fails
         */
        private static VarHandle findStaticVarHandle(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findStaticVarHandle(owner, name, type);
        }

        /**
         * Internal helper for {@link ReflectionUtils#unreflectVarHandle(Field)}
         * <p>
         * Produces a VarHandle giving access to a reflected field {@code f}. <br>
         * See the documentation for {@link MethodHandles.Lookup#unreflectVarHandle(Field)} for details.
         *
         * @param f The reflected field
         * @return a {@link VarHandle} giving access to non-static fields or a static field
         * @throws ReflectiveOperationException if access checking fails
         */
        private static VarHandle unreflectVarHandle(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.unreflectVarHandle(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private VarHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.VarHandleHelper.class) + callerBlame);
        }
    }

    /**
     * Helper class that holds a trusted {@link MethodHandles.Lookup} instance. <br>
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class LookupHelper {
        /**
         * Cached {@link MethodHandles.Lookup} instance which is trusted
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
                throw new RuntimeException("Could not find " + ReflectionUtils.getModuleInclusiveClassName(MethodHandles.Lookup.class) + "constructor", nsme);
            }

            LOOKUP = LookupHelper.trustedLookupIn(Object.class);

        }

        /**
         * Internal helper for {@link ReflectionUtils#ensureInitialized(Class)}
         * <p>
         * Ensures that {@code clazz} has been initialized.
         *
         * @param clazz The class to be initialized
         * @return the initialized class
         */
        private static <T> Class<T> ensureInitialized(Class<T> clazz) throws IllegalAccessException {
            // We need to teleport the lookup to the specified class because of how the access checking is done,
            // even though the lookup itself has trusted access
            LookupHelper.LOOKUP.in(clazz).ensureInitialized(clazz);
            return clazz;
        }

        /**
         * Produces a trusted {@link MethodHandles.Lookup} instance with the given {@code lookupClass} and a {@code null} {@code previousLookupClass}.
         *
         * @param lookupClass The desired lookup class
         * @return the created {@code Lookup} object
         */
        private static MethodHandles.Lookup trustedLookupIn(Class<?> lookupClass) {
            try {
                return LOOKUP_CONSTRUCTOR.newInstance(lookupClass, null, TRUSTED_MODE);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Could not create an instance of " + ReflectionUtils.getModuleInclusiveClassName(MethodHandles.Lookup.class), e);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private LookupHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(LookupHelper.class) + callerBlame);
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

        static {

            // Open the java.lang package to this class' module, so that we can freely invoke Constructor#setAccessible(boolean) on the ModuleLayer.Controller constructor
            ModuleHelper.addOpens(ModuleLayer.Controller.class.getModule(), ModuleLayer.Controller.class.getPackageName(), ModuleHelper.class.getModule());

            try {
                LAYER_CONTROLLER_CONSTRUCTOR = ModuleLayer.Controller.class.getDeclaredConstructor(ModuleLayer.class);
                LAYER_CONTROLLER_CONSTRUCTOR.setAccessible(true);
            } catch (NoSuchMethodException nsme) {
                throw new RuntimeException("Could not find constructor for " + getModuleInclusiveClassName(ModuleLayer.Controller.class), nsme);
            }

            try {
                ALL_UNNAMED_MODULE = (Module) LookupHelper.LOOKUP.findStaticGetter(Module.class, "ALL_UNNAMED_MODULE", Module.class).invoke();
                EVERYONE_MODULE = (Module) LookupHelper.LOOKUP.findStaticGetter(Module.class, "EVERYONE_MODULE", Module.class).invoke();
            } catch (Throwable t) {
                throw new RuntimeException("Could not find special module instances", t);
            }
        }

        /**
         * Internal helper for {@link ReflectionUtils#addExports(Module, String, Module)}
         * <p>
         * Exports the package with the given name from the {@code source} module to the {@code target} module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be exported to
         */
        private static void addExports(Module source, String packageName, Module target) {
            JavaLangAccessBridge.addExports(source, packageName, target);
        }

        /**
         * Internal helper for {@link ReflectionUtils#addExportsToAllUnnamed(Module, String)}
         * <p>
         * Exports the package with the given name from the {@code source} module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExportsToAllUnnamed(Module source, String packageName) {
            JavaLangAccessBridge.addExportsToAllUnnamed(source, packageName);
        }

        /**
         * Internal helper for {@link ReflectionUtils#addExports(Module, String)}
         * <p>
         * Exports the package with the given name from the {@code source} module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExports(Module source, String packageName) {
            JavaLangAccessBridge.addExports(source, packageName);
        }

        /**
         * Internal helper for {@link ReflectionUtils#addOpens(Module, String, Module)}
         * <p>
         * Opens the package with the given name from the {@code source} module to the {@code target} module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         */
        private static void addOpens(Module source, String packageName, Module target) {
            JavaLangAccessBridge.addOpens(source, packageName, target);
        }

        /**
         * Internal helper for {@link ReflectionUtils#addOpensToAllUnnamed(Module, String)}
         * <p>
         * Opens the package with the given name from the {@code source} module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addOpensToAllUnnamed(Module source, String packageName) {
            JavaLangAccessBridge.addOpensToAllUnnamed(source, packageName);
        }

        /**
         * Internal helper for {@link ReflectionUtils#addOpens(Module, String)}
         * <p>
         * Opens the package with the given name from the {@code source} module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @implNote Since {@link jdk.internal.access.JavaLangAccess} does not expose any methods to unconditionally open a package to all modules,
         * we use the special {@link #EVERYONE_MODULE} instance as if it was any other normal module
         */
        private static void addOpens(Module source, String packageName) {
            ModuleHelper.addOpens(source, packageName, EVERYONE_MODULE);
        }

        /**
         * Exports the package with the given name from the {@code source} module to the {@code target} module using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be exported to
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsWithController(Module source, String packageName, Module target) {
            return ModuleHelper.getControllerForModule(source).addExports(source, packageName, target);
        }

        /**
         * Exports the package with the given name from the {@code source} module to all unnamed modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsToAllUnnamedWithController(Module source, String packageName) {
            return ModuleHelper.addExportsWithController(source, packageName, ALL_UNNAMED_MODULE);
        }

        /**
         * Exports the package with the given name from the {@code source} module to all modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to export the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addExportsWithController(Module source, String packageName) {
            return ModuleHelper.addExportsWithController(source, packageName, EVERYONE_MODULE);
        }

        /**
         * Opens the package with the given name from the {@code source} module to the {@code target} module using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensWithController(Module source, String packageName, Module target) {
            return ModuleHelper.getControllerForModule(source).addOpens(source, packageName, target);
        }

        /**
         * Opens the package with the given name from the {@code source} module to all unnamed modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensToAllUnnamedWithController(Module source, String packageName) {
            return ModuleHelper.addOpensWithController(source, packageName, ALL_UNNAMED_MODULE);
        }

        /**
         * Opens the package with the given name from the {@code source} module to all modules using an instance of {@link ModuleLayer.Controller}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @return the newly created {@link ModuleLayer.Controller} instance that was used to open the package
         * @throws UnsupportedOperationException if {@code source} is not in a {@link ModuleLayer}
         */
        private static ModuleLayer.Controller addOpensWithController(Module source, String packageName) {
            return ModuleHelper.addOpensWithController(source, packageName, EVERYONE_MODULE);
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

            return ModuleHelper.getControllerForLayer(layer);
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
                throw new RuntimeException("Could not create a new instance of " + ReflectionUtils.getModuleInclusiveClassName(ModuleLayer.Controller.class), roe);
            }
        }

        /**
         * This method is only used to trigger initialization for this class.
         * In of itself, this method is a no-op.
         */
        private static void bootstrap() {
            // NO-OP
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private ModuleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.ModuleHelper.class) + callerBlame);
        }

    }

    /**
     * A custom (and very bare-bones) implementation of {@link ClassLoader} to be used in conjunction with the class generated by {@link InjectorGenerator#generateIn(String)}.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class InjectorClassLoader extends ClassLoader {
        /**
         * Private constructor. <br>
         * Only meant to be used by {@link JavaLangAccessBridge}.
         */
        private InjectorClassLoader() {
            super(InjectorClassLoader.class.getClassLoader());
        }

        /**
         * Converts an array of bytes into an instance of {@link Class}.
         *
         * @param classBytes The class bytes
         * @return the {@code Class} object created from the data
         */
        private Class<?> define(byte[] classBytes) {
            return this.defineClass(null, classBytes, 0, classBytes.length, null);
        }

        /**
         * Converts an array of bytes into an instance of {@link Class}, and then initializes said class.
         *
         * @param classBytes The class bytes
         * @return the {@code Class} object created from the data
         */
        @SuppressWarnings("UnusedReturnValue")
        private Class<?> defineAndLoad(byte[] classBytes) {
            try {
                return Class.forName(this.define(classBytes).getName(), true, this);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e); // This should never happen
            }
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
    private static final class InjectorGenerator {
        /**
         * The name of the generated "injector" class
         */
        private static final String INJECTOR_CLASS_NAME = "Injector";

        /**
         * Generates an "injector" class inside the package with the given name.
         *
         * @param packageName The name of the package the class should be generated in
         * @return the bytes of the generated injector class
         */
        private static byte[] generateIn(final String packageName) {
            final String fullInjectorClassName = packageName + "/" + InjectorGenerator.INJECTOR_CLASS_NAME;
            final String injectorClassDescriptor = "L" + fullInjectorClassName + ";";
            final String ownPackageName = InjectorGenerator.class.getPackageName();

            final ClassWriter classWriter = new ClassWriter(0);
            MethodVisitor methodVisitor;

            classWriter.visit(
                    Opcodes.V17,
                    Opcodes.ACC_SUPER,
                    fullInjectorClassName,
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

                Label l0 = new Label();
                methodVisitor.visitLabel(l0);

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

                Label l1 = new Label();
                methodVisitor.visitLabel(l1);

                methodVisitor.visitLocalVariable(
                        "this",
                        injectorClassDescriptor,
                        null,
                        l0,
                        l1,
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

                Label l0 = new Label();
                methodVisitor.visitLabel(l0);

                methodVisitor.visitLdcInsn(
                        Type.getType(injectorClassDescriptor)
                );
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        0
                );

                Label l1 = new Label();
                methodVisitor.visitLabel(l1);

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

                Label l2 = new Label();
                methodVisitor.visitLabel(l2);

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

                Label l3 = new Label();
                methodVisitor.visitLabel(l3);

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

                Label l4 = new Label();
                methodVisitor.visitLabel(l4);

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

                Label l5 = new Label();
                methodVisitor.visitLabel(l5);

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

                Label l6 = new Label();
                methodVisitor.visitLabel(l6);

                methodVisitor.visitInsn(
                        Opcodes.RETURN
                );

                methodVisitor.visitLocalVariable(
                        "injectorClass",
                        "Ljava/lang/Class;",
                        "Ljava/lang/Class<*>;",
                        l1,
                        l6,
                        0
                );
                methodVisitor.visitLocalVariable(
                        "loaderClass",
                        "Ljava/lang/Class;",
                        "Ljava/lang/Class<*>;",
                        l2,
                        l6,
                        1
                );
                methodVisitor.visitLocalVariable(
                        "javaBaseModule",
                        "Ljava/lang/Module;",
                        null,
                        l3,
                        l6,
                        2
                );
                methodVisitor.visitLocalVariable(
                        "loaderModule",
                        "Ljava/lang/Module;",
                        null,
                        l4,
                        l6,
                        3
                );
                methodVisitor.visitLocalVariable(
                        "javaLangAccess",
                        "Ljdk/internal/access/JavaLangAccess;",
                        null,
                        l5,
                        l6,
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
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.InjectorGenerator.class) + callerBlame);
        }
    }

    /**
     * Helper class that serves as a bridge between {@link ReflectionUtils} (and its internals) and {@link jdk.internal.access.JavaLangAccess}. <br>
     * The purpose of this class is to get rid of the need to export the "jdk.internal.access" package to this class' module via the {@code --add-exports}
     * argument at compile / run time.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static final class JavaLangAccessBridge {
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addExports(Module, String, Module)}
         *
         * @see JavaLangAccessBridge#addExports(Module, String, Module)
         */
        private static final MethodHandle addExportsToModule;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addExportsToAllUnnamed(Module, String)}
         *
         * @see JavaLangAccessBridge#addExportsToAllUnnamed(Module, String)
         */
        private static final MethodHandle addExportsToAllUnnamedModules;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addExports(Module, String)}
         *
         * @see JavaLangAccessBridge#addExports(Module, String)
         */
        private static final MethodHandle addExportsToAllModules;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addOpens(Module, String, Module)}
         *
         * @see JavaLangAccessBridge#addOpens(Module, String, Module)
         */
        private static final MethodHandle addOpensToModule;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addOpensToAllUnnamed(Module, String)}
         *
         * @see JavaLangAccessBridge#addOpensToAllUnnamed(Module, String)
         */
        private static final MethodHandle addOpensToAllUnnamedModules;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addEnableNativeAccess(Module)}
         *
         * @see JavaLangAccessBridge#addEnableNativeAccess(Module)
         */
        private static final MethodHandle addEnableNativeAccessToModule;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#addEnableNativeAccessAllUnnamed()}
         *
         * @see JavaLangAccessBridge#addEnableNativeAccessAllUnnamed()
         */
        private static final MethodHandle addEnableNativeAccessToAllUnnamedModules;
        /**
         * Cached {@link MethodHandle} for {@link jdk.internal.access.JavaLangAccess#isEnableNativeAccess(Module)}
         *
         * @see JavaLangAccessBridge#isEnableNativeAccess(Module)
         */
        private static final MethodHandle isEnableNativeAccess;

        static {

            JavaLangAccessBridge.gainInternalAccess();

            final String sharedSecretsClassName = "jdk.internal.access.SharedSecrets";
            final String javaLangAccessClassName = "jdk.internal.access.JavaLangAccess";
            final Class<?> sharedSecretsClass;
            final Class<?> javaLangAccessClass;

            try {
                sharedSecretsClass = Class.forName(sharedSecretsClassName);
            } catch (ClassNotFoundException cnfe) {
                throw new RuntimeException("Could not find " + sharedSecretsClassName, cnfe);
            }

            try {
                javaLangAccessClass = Class.forName(javaLangAccessClassName);
            } catch (ClassNotFoundException cnfe) {
                throw new RuntimeException("Could not find " + javaLangAccessClassName, cnfe);
            }

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final MethodHandle getJavaLangAccessHandle;

            try {
                getJavaLangAccessHandle = lookup.findStatic(sharedSecretsClass, "getJavaLangAccess", MethodType.methodType(javaLangAccessClass));
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not get handle for ", roe);
            }

            final Object javaLangAccessInstance;

            try {
                javaLangAccessInstance = getJavaLangAccessHandle.invoke();
            } catch (Throwable t) {
                throw new RuntimeException("Could not obtain the " + javaLangAccessClassName + " instance", t);
            }

            try {
                addExportsToModule = lookup.findVirtual(javaLangAccessClass, "addExports", MethodType.methodType(void.class, Module.class, String.class, Module.class)).bindTo(javaLangAccessInstance);
                addExportsToAllUnnamedModules = lookup.findVirtual(javaLangAccessClass, "addExportsToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class)).bindTo(javaLangAccessInstance);
                addExportsToAllModules = lookup.findVirtual(javaLangAccessClass, "addExports", MethodType.methodType(void.class, Module.class, String.class)).bindTo(javaLangAccessInstance);
                addOpensToModule = lookup.findVirtual(javaLangAccessClass, "addOpens", MethodType.methodType(void.class, Module.class, String.class, Module.class)).bindTo(javaLangAccessInstance);
                addOpensToAllUnnamedModules = lookup.findVirtual(javaLangAccessClass, "addOpensToAllUnnamed", MethodType.methodType(void.class, Module.class, String.class)).bindTo(javaLangAccessInstance);
                addEnableNativeAccessToModule = lookup.findVirtual(javaLangAccessClass, "addEnableNativeAccess", MethodType.methodType(Module.class, Module.class)).bindTo(javaLangAccessInstance);
                addEnableNativeAccessToAllUnnamedModules = lookup.findVirtual(javaLangAccessClass, "addEnableNativeAccessAllUnnamed", MethodType.methodType(void.class)).bindTo(javaLangAccessInstance);
                isEnableNativeAccess = lookup.findVirtual(javaLangAccessClass, "isEnableNativeAccess", MethodType.methodType(boolean.class, Module.class)).bindTo(javaLangAccessInstance);
            } catch (Throwable t) {
                throw new RuntimeException("Could not obtain handles for the methods in " + javaLangAccessClassName, t);
            }

        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addExports(Module, String, Module)}
         * <p>
         * Updates module {@code m1} to export a package to module {@code m2}.
         *
         * @param m1  The module that contains the package
         * @param pkg The name of the package to export
         * @param m2  The module the package should be exported to
         */
        private static void addExports(Module m1, String pkg, Module m2) {
            try {
                addExportsToModule.invoke(m1, pkg, m2);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addExportsToAllUnnamed(Module, String)}
         * <p>
         * Updates module {@code m} to export a package to all unnamed modules.
         *
         * @param m   The module that contains the package
         * @param pkg The name of the package to export
         */
        private static void addExportsToAllUnnamed(Module m, String pkg) {
            try {
                addExportsToAllUnnamedModules.invoke(m, pkg);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addExports(Module, String)}
         * <p>
         * Updates module {@code m} to export a package unconditionally.
         *
         * @param m   The module that contains the package
         * @param pkg The name of the package to export
         */
        private static void addExports(Module m, String pkg) {
            try {
                addExportsToAllModules.invoke(m, pkg);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addOpens(Module, String, Module)}
         * <p>
         * Updates module {@code m1} to open a package to module {@code m2}.
         *
         * @param m1  The module that contains the package
         * @param pkg The name of the package to open
         * @param m2  The module to open the package to
         */
        private static void addOpens(Module m1, String pkg, Module m2) {
            try {
                addOpensToModule.invoke(m1, pkg, m2);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addOpensToAllUnnamed(Module, String)}.
         * <p>
         * Updates module {@code m} to open a package to all unnamed modules.
         *
         * @param m   The module that contains the package
         * @param pkg The name of the package to open
         */
        private static void addOpensToAllUnnamed(Module m, String pkg) {
            try {
                addExportsToAllUnnamedModules.invoke(m, pkg);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addEnableNativeAccess(Module)}
         * <p>
         * Updates module {@code m} to allow access to restricted methods.
         *
         * @param m The module to update
         */
        private static void addEnableNativeAccess(Module m) {
            try {
                addEnableNativeAccessToModule.invoke(m);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#addEnableNativeAccessAllUnnamed()}
         * <p>
         * Updates all unnamed modules to allow access to restricted methods.
         */
        private static void addEnableNativeAccessAllUnnamed() {
            try {
                addEnableNativeAccessToAllUnnamedModules.invoke();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Bridge method for {@link jdk.internal.access.JavaLangAccess#isEnableNativeAccess(Module)}
         * <p>
         * Checks whether module {@code m} can access restricted methods.
         *
         * @param m The module to check against
         * @return {@code true} if {@code m} can access restricted methods, {@code false} otherwise
         */
        private static boolean isEnableNativeAccess(Module m) {
            try {
                return (boolean) isEnableNativeAccess.invoke(m);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        /**
         * Exploits the Proxy API to gain access to the {@link jdk.internal.access} package,
         * which is normally not visible to modules that are not part of the JDK implementation.
         */
        @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
        private static void gainInternalAccess() {
            final String javaLangAccessName = "jdk.internal.access.JavaLangAccess";
            final InjectorClassLoader injectorLoader = new InjectorClassLoader();
            final Class<?> javaLangAccessInterface;
            final Class<?>[] interfaces;
            final Class<?> injectorClass;

            try {
                javaLangAccessInterface = Class.forName(javaLangAccessName);
                interfaces = new Class[]{
                        javaLangAccessInterface
                };

                final Object proxyInstance = Proxy.newProxyInstance(
                        injectorLoader,
                        interfaces,
                        (proxy, method, arguments) -> null
                );

                final String packageName = proxyInstance.getClass().getPackageName().replace(".", "/");

                injectorLoader.defineAndLoad(InjectorGenerator.generateIn(packageName));

            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not gain access to the jdk.internal.access package", roe);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private JavaLangAccessBridge() {
            String callerBlame = "";
            try {
                callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(STACK_WALKER.getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(JavaLangAccessBridge.class) + callerBlame);
        }
    }

}
