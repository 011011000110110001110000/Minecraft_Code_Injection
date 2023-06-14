package me.lollopollqo.sus.injection.util;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>WIP</b> <br>
 * TODO: documentation, more helpers for internal methods, support for Openj9 VMs <br>
 * Utility class that contains some useful methods for easier / more powerful reflection usage. <br>
 *
 * @author Lollopollqo
 * @apiNote Due to the different implementation of {@link MethodHandles.Lookup}, this class is not compatible with <br>
 * <a href="https://www.eclipse.org/openj9/">OpenJ9</a> VMs (see {@link MethodHandleHelper}).
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
     * @param owner      The class that declares the method
     * @param name       The name of the method
     * @param paramTypes The types of the parameters of the method, in order
     * @return the method with the specified owner, name and parameter types
     * @throws NoSuchMethodException if a field with the specified name and parameter types could not be found in the specified class
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredField(String)} in regard to what methods it can find (see {@link jdk.internal.reflect.Reflection#registerMethodsToFilter}).
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
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredField(String)} in regard to what fields it can find (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     */
    public static Field forceGetDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        forceSetAccessible(field, true);
        return field;
    }

    /**
     * Gets a field with the specified type and access modifiers and forces it to be accessible. <br>
     * Use this instead of {@link #forceGetDeclaredField(Class, String)} if the field name is not known. <br>
     * If there is more than one field with the specified modifiers and type in the target class, <br>
     * then this method will return the first field with the specified modifiers and type it can find in the target class. <br>
     * The order in which fields are checked is determined by {@link Class#getDeclaredFields()}.
     *
     * @param owner     The class that declares the field
     * @param modifiers The access modifiers of the field
     * @param type      The type of the field
     * @return the field with the specified owner, access modifiers and type
     * @throws NoSuchFieldException if a field with the specified type and modifiers could not be found in the specified class
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredFields()} in regard to what fields it can find (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     */
    public static Field forceGetDeclaredField(Class<?> owner, int modifiers, Class<?> type) throws NoSuchFieldException {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getModifiers() == modifiers && field.getType() == type) {
                forceSetAccessible(field, true);
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
    private static void forceSetAccessible(AccessibleObject object, boolean accessible) {
        setAccessible(object, accessible);
    }

    /**
     * Forces the given {@link AccessibleObject} instance to have the desired accessibility. <br>
     * Unlike {@link AccessibleObject#setAccessible(boolean)}, this method does not perform any permission checks.
     *
     * @param object     The object whose accessibility is to be forcefully set
     * @param accessible the accessibility to be forcefully set
     * @return <code>accessible</code>
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setAccessible(AccessibleObject object, boolean accessible) {
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to module " + target.getName() + "!", t);
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules!", t);
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules!", t);
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to module " + target.getName() + "!", t);
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules!", t);
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
            throw new RuntimeException("Could not export package " + packageName + " from module " + source.getName() + " to all modules!", t);
        }
    }

    /**
     * Gets the value of the field with the specified name and type in the specified class. <br>
     * This method bypasses any limitations that {@link Class#getDeclaredField(String)} has (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
     *
     * @param owner The class that declares the field
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
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeNonStatic(Object owner, String name, Class<T> returnType, Class<?>[] argumentTypes, Object... arguments) {
        return (T) invokeNonStatic(owner, name, MethodType.methodType(returnType, argumentTypes));
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
            return MethodHandleHelper.LOOKUP.bind(owner, name, type).invokeWithArguments(arguments);
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
     * @see java.lang.invoke.MethodHandles.Lookup#findGetter(Class, String, Class)
     */
    public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findGetter(owner, name, type);
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
        return MethodHandleHelper.LOOKUP.in(owner).findStaticGetter(owner, name, type);
    }

    public static MethodHandle findSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findSetter(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findSetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findSetter(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStaticSetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findStaticSetter(owner, name, type);
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

    public static MethodHandle findVirtual(Class<?> owner, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return findVirtual(owner, name, MethodType.methodType(returnType, params));
    }

    public static MethodHandle findVirtual(Class<?> owner, String name, MethodType type) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findVirtual(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return findVirtualAndBind(owner, instance, name, MethodType.methodType(returnType, params));
    }

    public static <O, T extends O> MethodHandle findVirtualAndBind(Class<T> owner, O instance, String name, MethodType type) throws ReflectiveOperationException {
        return findVirtual(owner, name, type).bindTo(instance);
    }

    public static MethodHandle findStatic(Class<?> owner, String name, Class<?> returnType, Class<?>... params) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findStatic(owner, name, MethodType.methodType(returnType, params));
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
            MethodHandleHelper.LOOKUP.in(clazz).ensureInitialized(clazz);
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
     * Helper class that makes working with {@link MethodHandle}s easier. <br>
     *
     * @author Lollopollqo
     * @apiNote Due to the different implementation of {@link MethodHandles.Lookup},
     * this class is not compatible with <a href="https://www.eclipse.org/openj9/">OpenJ9</a> VMs. <br>
     * This is due to the fact that, in the OpenJ9 implementation, access modes are different, <br>
     * and teleporting the lookup removes permissions even if the lookup object has full privileges. <br>
     * Technically, if we manage to get hold of the <code>IMPL_LOOKUP</code> instance, we have a fully privileged lookup instance,
     * but the methods that use this class do not account for the aforementioned fact that teleporting the lookup
     * with {@link java.lang.invoke.MethodHandles.Lookup#in(Class)} still results in privilege loss.
     * @implNote Initializing this class from outside of {@link UnsafeHelper} <b>will</b> result in an access violation due to incorrect classloading order
     */
    private static final class MethodHandleHelper {
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
         * Gets or creates a {@link MethodHandles.Lookup} instance with <code>TRUSTED</code> access.
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
                    // If for some reason we couldn't get the trusted lookup via reflection, create a new trusted instance ourselves

                    // See MethodHandles.Lookup#TRUSTED
                    final int trustedMode = -1;
                    final long overrideOffset = UnsafeHelper.INTERNAL_UNSAFE.objectFieldOffset(AccessibleObject.class, "override");

                    if (overrideOffset == -1) {
                        throw new RuntimeException("Could not locate AccessibleObject#override!");
                    }

                    final Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);

                    UnsafeHelper.unsafeSetAccesible(lookupConstructor, true);
                    implLookup = lookupConstructor.newInstance(Object.class, null, trustedMode);
                }
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not create trusted lookup!", roe);
            }

            return implLookup;
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
     * Helper class that simplifies the process of bypassing module access checks <br>
     * (meaning exporting / opening packages to modules they aren't normally exported / opened to).
     *
     * @author Lollopollqo
     */
    private static final class ModuleHelper {
        /**
         * An instance of {@link ModuleLayer.Controller}, used to export / open packages without restrictions
         */
        private static final ModuleLayer.Controller layerController;
        /**
         * The cached offset (in bytes) for the {@link ModuleLayer.Controller#layer} field in a {@link ModuleLayer.Controller} instance
         */
        private static final long layerFieldOffset;
        /**
         * Special module that represents all unnamed modules (see {@link Module#ALL_UNNAMED_MODULE}) <br>
         * Cannot be final due to classloading ordering issues
         */
        private static Module allUnnamedModule;
        /**
         * Special module that represents all modules (see {@link Module#EVERYONE_MODULE}) <br>
         * Cannot be final due to classloading ordering issues
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

                if (caller != (UnsafeHelper.class)) {
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
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addExports(source, packageName, target);
        }

        /**
         * Exports the specified package from the specified module to all the unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExportsToAllUnnamed(Module source, String packageName) {
            addExports(source, packageName, allUnnamedModule);
        }

        /**
         * Exports the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addExports(Module source, String packageName) {
            addExports(source, packageName, everyoneModule);
        }

        /**
         * Opens the specified package from the specified module to the specified module.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         * @param target      The module the package is to be opened to
         */
        private static void addOpens(Module source, String packageName, Module target) {
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addOpens(source, packageName, target);
        }

        /**
         * Opens the specified package from the specified module to all unnamed modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addOpensToAllUnnamed(Module source, String packageName) {
            addOpens(source, packageName, allUnnamedModule);
        }

        /**
         * Opens the specified package from the specified module to all modules.
         *
         * @param source      The module the package belongs to
         * @param packageName The name of the package
         */
        private static void addOpens(Module source, String packageName) {
            addOpens(source, packageName, everyoneModule);
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
                allUnnamedModule = (Module) MethodHandleHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "ALL_UNNAMED_MODULE", Module.class).invoke();
                everyoneModule = (Module) MethodHandleHelper.LOOKUP.in(Module.class).findStaticGetter(Module.class, "EVERYONE_MODULE", Module.class).invoke();
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
     * @author Lollopollqo
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

            // Needs to be done before calling enableJdkInternalsAccess() as that uses methods in ModuleHelper, which in turn uses the sun.misc.Unsafe instance
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
         * Export the {@link jdk.internal.misc} and {@link jdk.internal.access} packages to this class' module.
         */
        private static void enableJdkInternalsAccess() {
            if (UNSAFE == null) {
                throw new IllegalStateException(ReflectionUtils.getModuleInclusiveClassName(ReflectionUtils.UnsafeHelper.class) + "#enableJdkInternalsAccess() called before sun.misc.Unsafe instance could be obtained!");
            }

            final String internalPackageName = "jdk.internal";
            final String miscPackageName = internalPackageName + ".misc";
            final String accessPackageName = internalPackageName + ".access";
            final String moduleName = "java.base";
            final Module javaBaseModule = Object.class
                                              .getModule()
                                              .getLayer()
                                              .findModule(moduleName)
                                              .orElseThrow(
                                                  () -> new RuntimeException("Could not find module " + moduleName + "!")
                                              );

            ModuleHelper.addExports(javaBaseModule, miscPackageName, UnsafeHelper.class.getModule());
            ModuleHelper.addExports(javaBaseModule, accessPackageName, UnsafeHelper.class.getModule());
        }

        /**
         * Allocates a new instance of the given class, throwing a {@link RuntimeException} if an {@link InstantiationException} occurs.
         *
         * @param clazz The class a new instance of which is to be created
         * @return the allocated instance
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
