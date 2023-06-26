package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>WIP</b> <br>
 * TODO: documentation, more helpers for internal methods <br>
 * Utility class that contains some useful methods for easier / more powerful reflection usage. <br>
 *
 * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
 */
@SuppressWarnings("unused")
public final class ReflectionUtils {

    static {
        // Ensure UnsafeHelper's initializer is invoked before the other Helper classes' ones by accessing a static member.
        // We do it this way instead of using  Class.forName(String) so we can check for illegal initializations from outside this class in the UnsafeHelper initializer
        // (and also to avoid having a hardcoded string with the fully qualified class name in case this ever gets relocated).
        // This is essential to avoid an access violation error happening due to incorrect classloading order.
        sun.misc.Unsafe unsafe = UnsafeHelper.UNSAFE;

        // Handle this here to ensure it only happens after all the necessary initialization steps have happened
        ModuleHelper.getSpecialModules();
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
     */
    public static Method forceGetDeclaredMethod(Class<?> owner, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, paramTypes);
        forceSetAccessibleWithUnsafe(method, true);
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
     */
    public static Field forceGetDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        forceSetAccessibleWithUnsafe(field, true);
        return field;
    }

    /**
     * Gets a field with the specified type and access modifiers and forces it to be accessible. <br>
     * Use this instead of {@link #forceGetDeclaredField(Class, String)} if the field name is not known. <br>
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
     */
    public static Field forceGetDeclaredField(Class<?> owner, int modifiers, Class<?> type) throws NoSuchFieldException {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getModifiers() == modifiers && field.getType() == type) {
                forceSetAccessibleWithUnsafe(field, true);
                return field;
            }
        }
        throw new NoSuchFieldException("Failed to get field from " + owner.getName() + " of type" + type.getName());
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     */
    @SuppressWarnings("SameParameterValue")
    private static void forceSetAccessibleWithUnsafe(AccessibleObject object, boolean accessible) {
        unsafeSetAccessible(object, accessible);
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility. <br>
     * Unlike {@link AccessibleObject#setAccessible(boolean)}, this method does not perform any permission checks.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible The accessibility to be forcefully set
     * @return <code>accessible</code>
     * @see UnsafeHelper#unsafeSetAccesible(AccessibleObject, boolean)
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean unsafeSetAccessible(AccessibleObject object, boolean accessible) {
        try {
            UnsafeHelper.unsafeSetAccesible(object, accessible);
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

    public static MethodHandle unreflectSpecial(Method m) throws ReflectiveOperationException {
        return MethodHandleHelper.unreflectSpecial(m);
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
            callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
        } catch (IllegalCallerException ignored) {

        }
        throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.class) + callerBlame);
    }

    /**
     * Helper class that holds a trusted {@link MethodHandles.Lookup} instance. <br>
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     * @implNote Initializing this class from outside of {@link UnsafeHelper} <b>will</b> result in an access violation due to incorrect classloading order
     */
    private static final class LookupHelper {
        /**
         * Trusted lookup
         */
        private static final MethodHandles.Lookup LOOKUP;

        static {
            // Don't allow initialization externally
            {
                final IllegalCallerException illegalCaller = new IllegalCallerException("ReflectionUtils$ModuleHelper#<clinit> invoked from outside ReflectionUtils$ModuleHelper");
                Class<?> caller;

                try {
                    caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
                } catch (IllegalCallerException ice) {
                    illegalCaller.addSuppressed(ice);
                    throw illegalCaller;
                }

                if (caller != ReflectionUtils.ModuleHelper.class) {
                    throw illegalCaller;
                }
            }

            LOOKUP = getTrustedLookup();
        }

        /**
         * Ensures the specified class is initialized.
         *
         * @param clazz the class whose initialization is to be ensured
         * @return the initialized class
         */
        private static <T> Class<T> ensureInitialized(Class<T> clazz) throws IllegalAccessException {
            LookupHelper.LOOKUP.in(clazz).ensureInitialized(clazz);
            return clazz;
        }

        /**
         * Gets or creates a trusted {@link MethodHandles.Lookup} instance.
         *
         * @return the {@link MethodHandles.Lookup} instance
         */
        private static MethodHandles.Lookup getTrustedLookup() {
            MethodHandles.Lookup implLookup;
            try {
                try {
                    // Get the trusted lookup via reflection
                    final Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                    UnsafeHelper.unsafeSetAccesible(implLookupField, true);
                    implLookup = (MethodHandles.Lookup) implLookupField.get(null);

                } catch (ReflectiveOperationException roe) {
                    // If for some reason we couldn't get the lookup via reflection, create a new instance ourselves

                    // The access modes to use for the trusted lookup instance
                    // See MethodHandles.Lookup#TRUSTED
                    final int trusted = -1;
                    final Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);

                    UnsafeHelper.unsafeSetAccesible(lookupConstructor, true);
                    implLookup = lookupConstructor.newInstance(Object.class, null, trusted);
                }
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not get trusted lookup!", roe);
            }

            return implLookup;
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private LookupHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
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
            return LookupHelper.LOOKUP.in(owner).findGetter(owner, name, type);
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
            return LookupHelper.LOOKUP.in(owner).findStaticGetter(owner, name, type);
        }

        private static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(owner).findSetter(owner, name, type);
        }

        public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(owner).findStaticSetter(owner, name, type);
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
            return LookupHelper.LOOKUP.in(owner).findVirtual(owner, name, type);
        }

        private static MethodHandle findStatic(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(owner).findStatic(owner, name, type);
        }

        private static MethodHandle findSpecial(Class<?> owner, String name, Class<?> specialCaller, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.findSpecial(owner, name, type, specialCaller);
        }

        private static MethodHandle findConstructor(Class<?> owner, MethodType type) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(owner).findConstructor(owner, type);
        }

        private static MethodHandle unreflect(Method m) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(m.getDeclaringClass()).unreflect(m);
        }

        private static MethodHandle unreflectSpecial(Method m) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(m.getDeclaringClass()).unreflectSpecial(m, m.getDeclaringClass());
        }

        private static MethodHandle unreflectConstructor(Constructor<?> c) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(c.getDeclaringClass()).unreflectConstructor(c);
        }

        private static MethodHandle unreflectGetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(f.getDeclaringClass()).unreflectGetter(f);
        }

        private static MethodHandle unreflectSetter(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(f.getDeclaringClass()).unreflectSetter(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private MethodHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
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
            return LookupHelper.LOOKUP.in(owner).findVarHandle(owner, name, type);
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
            return LookupHelper.LOOKUP.in(owner).findStaticVarHandle(owner, name, type);
        }

        private static VarHandle unreflectVarHandle(Field f) throws ReflectiveOperationException {
            return LookupHelper.LOOKUP.in(f.getDeclaringClass()).unreflectVarHandle(f);
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private VarHandleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
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
         * An instance of {@link ModuleLayer.Controller}, used to export / open packages without restrictions
         *
         * @see #unsafeAddExports(Module, String, Module)
         * @see #unsafeAddExportsToAllUnnamed(Module, String)
         * @see #unsafeAddExports(Module, String)
         * @see #unsafeAddOpens(Module, String, Module)
         * @see #unsafeAddOpensToAllUnnamed(Module, String)
         * @see #unsafeAddOpens(Module, String)
         */
        private static final ModuleLayer.Controller layerController;
        /**
         * The cached offset (in bytes) for the {@link ModuleLayer.Controller#layer} field in a {@link ModuleLayer.Controller} instance
         *
         * @see #layerController
         * @see #unsafeAddExports(Module, String, Module)
         * @see #unsafeAddExportsToAllUnnamed(Module, String)
         * @see #unsafeAddExports(Module, String)
         * @see #unsafeAddOpens(Module, String, Module)
         * @see #unsafeAddOpensToAllUnnamed(Module, String)
         * @see #unsafeAddOpens(Module, String)
         */
        private static final long layerFieldOffset;
        /**
         * Special module that represents all unnamed modules (see {@link Module#ALL_UNNAMED_MODULE}) <br>
         *
         * @implNote Cannot be final due to classloading ordering issues
         */
        private static Module allUnnamedModule;
        /**
         * Special module that represents all modules (see {@link Module#EVERYONE_MODULE}) <br>
         *
         * @implNote Cannot be final due to classloading ordering issues
         */
        private static Module everyoneModule;

        static {
            // Don't allow initialization externally
            {
                final IllegalCallerException illegalCaller = new IllegalCallerException("ReflectionUtils$ModuleHelper#<clinit> invoked from outside ReflectionUtils$UnsafeHelper");
                final Class<?> caller;

                try {
                    caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
                } catch (IllegalCallerException ice) {
                    illegalCaller.addSuppressed(ice);
                    throw illegalCaller;
                }

                if (caller != UnsafeHelper.class) {
                    throw illegalCaller;
                }
            }

            try {
                layerController = (ModuleLayer.Controller) UnsafeHelper.UNSAFE.allocateInstance(ModuleLayer.Controller.class);
                layerFieldOffset = UnsafeHelper.UNSAFE.objectFieldOffset(ModuleLayer.Controller.class.getDeclaredField("layer"));
            } catch (Throwable t) {
                throw new RuntimeException(t);
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
            jdk.internal.access.SharedSecrets.getJavaLangAccess().addExports(source, packageName, target);
        }

        /**
         * Exports the specified package from the specified module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExportsToAllUnnamed(Module source, String packageName) {
            jdk.internal.access.SharedSecrets.getJavaLangAccess().addExportsToAllUnnamed(source, packageName);
        }

        /**
         * Exports the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExports(Module source, String packageName) {
            jdk.internal.access.SharedSecrets.getJavaLangAccess().addExports(source, packageName);
        }

        /**
         * Opens the specified package from the specified module to the specified module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         */
        private static void addOpens(Module source, String packageName, Module target) {
            jdk.internal.access.SharedSecrets.getJavaLangAccess().addOpens(source, packageName, target);
        }

        /**
         * Opens the specified package from the specified module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addOpensToAllUnnamed(Module source, String packageName) {
            jdk.internal.access.SharedSecrets.getJavaLangAccess().addOpensToAllUnnamed(source, packageName);
        }

        /**
         * Opens the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @implNote Since {@link jdk.internal.access.JavaLangAccess} does not expose any methods to unconditionally open a package to all modules,
         * we use the special {@link #everyoneModule} instance as if it was any other normal module
         */
        private static void addOpens(Module source, String packageName) {
            addOpens(source, packageName, everyoneModule);
        }

        /**
         * Exports the specified package from the specified module to the specified module using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be exported to
         */
        private static void unsafeAddExports(Module source, String packageName, Module target) {
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addExports(source, packageName, target);
        }

        /**
         * Exports the specified package from the specified module to all unnamed modules using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void unsafeAddExportsToAllUnnamed(Module source, String packageName) {
            unsafeAddExports(source, packageName, allUnnamedModule);
        }

        /**
         * Exports the specified package from the specified module to all modules using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void unsafeAddExports(Module source, String packageName) {
            unsafeAddExports(source, packageName, everyoneModule);
        }

        /**
         * Opens the specified package from the specified module to the specified module using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         */
        private static void unsafeAddOpens(Module source, String packageName, Module target) {
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addOpens(source, packageName, target);
        }

        /**
         * Opens the specified package from the specified module to all unnamed modules using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void unsafeAddOpensToAllUnnamed(Module source, String packageName) {
            unsafeAddOpens(source, packageName, allUnnamedModule);
        }

        /**
         * Opens the specified package from the specified module to all modules using {@link sun.misc.Unsafe}.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void unsafeAddOpens(Module source, String packageName) {
            unsafeAddOpens(source, packageName, everyoneModule);
        }

        /**
         * Sets the references to {@link Module#ALL_UNNAMED_MODULE} and {@link Module#EVERYONE_MODULE}.
         *
         * @see #allUnnamedModule
         * @see #everyoneModule
         */
        private static void getSpecialModules() {
            final boolean allUnnamedIsPresent = allUnnamedModule != null;
            final boolean everyoneIsPresent = everyoneModule != null;

            // If the references are already set, return early
            if (allUnnamedIsPresent && everyoneIsPresent) {
                return;
            }

            // If only one reference is not null it means something went really wrong, so throw an exception
            if (allUnnamedIsPresent ^ everyoneIsPresent) {
                throw new IllegalStateException("Expected both allUnnamedModule and everyoneModule to be non null, but only " + (allUnnamedIsPresent ? "everyoneModule" : "allUnnamedModule") + " is not null!");
            }

            // If both references are null, then we can proceed
            try {
                allUnnamedModule = (Module) LookupHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "ALL_UNNAMED_MODULE", Module.class).invoke();
                everyoneModule = (Module) LookupHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "EVERYONE_MODULE", Module.class).invoke();
            } catch (Throwable t) {
                throw new RuntimeException("Could not find special module instances!", t);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private ModuleHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
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
        private static final long overrideOffset;

        static {
            // Don't allow initialization externally
            {
                final IllegalCallerException illegalCaller = new IllegalCallerException("ReflectionUtils$UnsafeHelper#<clinit> invoked from outside ReflectionUtils");
                final Class<?> caller;

                try {
                    caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
                } catch (IllegalCallerException ice) {
                    illegalCaller.addSuppressed(ice);
                    throw illegalCaller;
                }

                if (caller != ReflectionUtils.class) {
                    throw illegalCaller;
                }
            }

            // Needs to be done before calling enableJdkInternalsAccess() as that uses methods in ModuleHelper, which in turn use the sun.misc.Unsafe instance
            UNSAFE = findUnsafe();
            enableJdkInternalsAccess();
            INTERNAL_UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();
            // Bypass reflection filters by using the jdk.internal.misc.Unsafe instance
            overrideOffset = INTERNAL_UNSAFE.objectFieldOffset(AccessibleObject.class, "override");
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
        private static void unsafeSetAccesible(AccessibleObject object, boolean accessible) {
            INTERNAL_UNSAFE.putBoolean(object, overrideOffset, accessible);
        }

        /**
         * Gets a reference to the {@link sun.misc.Unsafe} instance without relying on the field name.
         *
         * @return the {@link sun.misc.Unsafe} instance
         */
        private static sun.misc.Unsafe findUnsafe() {
            final int unsafeModifiers = Modifier.STATIC | Modifier.FINAL;
            final List<Throwable> exceptions = new ArrayList<>();

            // We cannot rely on the field name, as it is an implementation detail and as such it can be different from version to version
            for (Field field : sun.misc.Unsafe.class.getDeclaredFields()) {

                // Verify that the field is of the correct type and has the correct access modifiers
                if (field.getType() != sun.misc.Unsafe.class || (field.getModifiers() & unsafeModifiers) != unsafeModifiers) {
                    continue;
                }

                try {
                    // Thankfully don't need to do anything fancy to set the field to be accessible
                    // since the jdk.unsupported module exports and opens the sun.misc package to all modules
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
         * Export the {@link jdk.internal.misc}, {@link jdk.internal.access} and {@link jdk.internal.reflect} packages to this class' module.
         */
        private static void enableJdkInternalsAccess() {
            if (UNSAFE == null) {
                throw new IllegalStateException(ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.UnsafeHelper.class) + "#enableJdkInternalsAccess() called before sun.misc.Unsafe instance could be obtained!");
            }

            final String internalPackageName = "jdk.internal";
            final String miscPackageName = internalPackageName + ".misc";
            final String accessPackageName = internalPackageName + ".access";
            final String reflectPackageName = internalPackageName + ".reflect";
            final String moduleName = "java.base";
            final Module javaBaseModule = Object.class
                    .getModule()
                    .getLayer()
                    .findModule(moduleName)
                    .orElseThrow(
                            () -> new RuntimeException("Could not find module " + moduleName + "!")
                    );

            // Need to use the unsafe version here as we obviously haven't gained access to the classes in the jdk.internal.access package (SharedSecrets, JavaLangAccess) yet
            ModuleHelper.unsafeAddExports(javaBaseModule, miscPackageName, UnsafeHelper.class.getModule());
            ModuleHelper.unsafeAddExports(javaBaseModule, accessPackageName, UnsafeHelper.class.getModule());
            ModuleHelper.unsafeAddExports(javaBaseModule, reflectPackageName, UnsafeHelper.class.getModule());
        }

        /**
         * Allocates a new instance of the given class without doing any initialization.
         *
         * @param clazz The class a new instance of which is to be created
         * @return the allocated instance
         * @throws RuntimeException if an {@link InstantiationException} occurs
         * @see jdk.internal.misc.Unsafe#allocateInstance(Class)
         */
        @SuppressWarnings("unchecked")
        @NotNull
        private static <T> T allocateInstance(@NotNull Class<T> clazz) {
            try {
                return (T) INTERNAL_UNSAFE.allocateInstance(clazz);
            } catch (InstantiationException ie) {
                throw new RuntimeException("Failed to allocate instance of " + getModuleInclusiveClassName(clazz), ie);
            }
        }

        /**
         * Private constructor to prevent instantiation.
         */
        private UnsafeHelper() {
            String callerBlame = "";
            try {
                callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
            } catch (IllegalCallerException ignored) {

            }
            throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.UnsafeHelper.class) + callerBlame);
        }
    }
}
