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
 * TODO: documentation, more helpers for internal methods <br>
 * Helper class that contains some useful methods for easier / more powerful reflection usage. <br>
 *
 * @author Lollopollqo
 */
@SuppressWarnings("unused")
public final class ReflectionUtils {
    /**
     * Handle for {@link AccessibleObject#setAccessible0(boolean)}
     */
    private static final MethodHandle setAccessible0;
    /**
     * Handle for {@link Module#implAddExports(String, Module)}
     */
    private static final MethodHandle addExportsToModule;
    /**
     * Handle for {@link Module#implAddExportsToAllUnnamed(String)}
     */
    private static final MethodHandle addExportsToAllUnnamed;
    /**
     * Handle for {@link Module#implAddExports(String)}
     */
    private static final MethodHandle addExportsToAll;
    /**
     * Handle for {@link Module#implAddOpens(String, Module)}
     */
    private static final MethodHandle addOpensToModule;
    /**
     * Handle for {@link Module#implAddOpensToAllUnnamed(String)}
     */
    private static final MethodHandle addOpensToAllUnnamed;
    /**
     * Handle for {@link Module#implAddOpens(String)}
     */
    private static final MethodHandle addOpensToAll;

    static {
        setAccessible0 = setAccessible0Handle();

        addExportsToModule = addExportsToModuleHandle();
        addExportsToAllUnnamed = addExportsToAllUnnamedHandle();
        addExportsToAll = addExportsToAllHandle();
        addOpensToModule = addOpensToModuleHandle();
        addOpensToAllUnnamed = addOpensToAllUnnamedHandle();
        addOpensToAll = addOpensToAllHandle();

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
     * @implNote This method still has the same restrictions as {@link Class#getDeclaredField(String)} in regard to what fields it can find (see {@link jdk.internal.reflect.Reflection#registerFieldsToFilter}).
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
            throw new RuntimeException("Could not force the accessibility of " + object + " to be set to " + accessible, e);
        }
    }

    /**
     * Exports the specified package from the specified module to the specified module.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     * @param to          The module the package is to be exported to
     */
    public static void exportPackageToModule(Module from, String packageName, Module to) {
        try {
            addExportsToModule.bindTo(from).invoke(packageName, to);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to module " + to.getName() + "!", e);
        }
    }

    /**
     * Exports the specified package from the specified module to all <em>unnamed</em> modules.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     */
    public static void exportPackageToAllUnnamed(Module from, String packageName) {
        try {
            addExportsToAllUnnamed.bindTo(from).invoke(packageName);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to all modules!", e);
        }
    }

    /**
     * Exports the specified package from the specified module to all modules.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     */
    public static void exportPackageToAll(Module from, String packageName) {
        try {
            addExportsToAll.bindTo(from).invoke(packageName);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to all modules!", e);
        }
    }

    /**
     * Opens the specified package from the specified module to the specified module.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     * @param to          The module the package is to be exported to
     */
    public static void openPackageToModule(Module from, String packageName, Module to) {
        try {
            addOpensToModule.bindTo(from).invoke(packageName, to);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to module " + to.getName() + "!", e);
        }
    }

    /**
     * Opens the specified package from the specified module to all <em>unnamed</em> modules.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     */
    public static void openPackageToAllUnnamed(Module from, String packageName) {
        try {
            addOpensToAllUnnamed.bindTo(from).invoke(packageName);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to all modules!", e);
        }
    }

    /**
     * Opens the specified package from the specified module to all modules.
     *
     * @param from        The module the package belongs to
     * @param packageName The name of the package
     */
    public static void openPackageToAll(Module from, String packageName) {
        try {
            addOpensToAll.bindTo(from).invoke(packageName);
        } catch (Throwable e) {
            throw new RuntimeException("Could not export package " + packageName + " from module " + from.getName() + " to all modules!", e);
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

    @SuppressWarnings("unchecked")
    public static <T> T invokeNonStatic(Object owner, String name, Class<T> returnType, Class<?>[] argumentTypes, Object... arguments) {
        return (T) invokeNonStatic(owner, name, MethodType.methodType(returnType, argumentTypes));
    }

    public static Object invokeNonStatic(Object owner, String name, MethodType type, Object... arguments) {
        try {
            return MethodHandleHelper.LOOKUP.bind(owner, name, type).invokeWithArguments(arguments);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke " + getModuleInclusiveClassName(owner.getClass()) + "." + name + type, e);
        }
    }

    public static MethodHandle findGetter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        return MethodHandleHelper.LOOKUP.in(owner).findGetter(owner, name, type);
    }

    public static <O, T extends O> MethodHandle findGetterAndBind(Class<T> owner, O instance, String name, Class<?> type) throws ReflectiveOperationException {
        return findGetter(owner, name, type).bindTo(instance);
    }

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

    /* Caching for important MethodHandles */

    /**
     * Gets a {@link MethodHandle} for {@link Module#addExportsToAll0(Module, String)}.
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
     * Gets a {@link MethodHandle} for {@link Module#addExports0(Module, String, Module)}.
     *
     * @return the acquired {@link MethodHandle}
     */
    private static MethodHandle addExports0Handle() {
        try {
            return findStatic(Module.class, "addExports0", void.class, Module.class, String.class, Module.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#addExports0(Module, String, Module) handle!", e);
        }
    }

    /**
     * Gets a {@link MethodHandle} for {@link Module#addExportsToAll0(Module, String)}.
     *
     * @return the acquired {@link MethodHandle}
     */
    private static MethodHandle addExportsToAll0Handle() {
        try {
            return findStatic(Module.class, "addExportsToAll0", void.class, Module.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#addExportsToAll0(Module, String) handle!", e);
        }
    }

    private static MethodHandle addExportsToAllHandle() {
        try {
            return findVirtual(Module.class, "implAddExports", void.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddExports(String) handle!", e);
        }
    }

    private static MethodHandle addExportsToAllUnnamedHandle() {
        try {
            return findVirtual(Module.class, "implAddExportsToAllUnnamed", void.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddExportsToAllUnnamed(String) handle!", e);
        }
    }

    private static MethodHandle addExportsToModuleHandle() {
        try {
            return findVirtual(Module.class, "implAddExports", void.class, String.class, Module.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddExports(String, Module) handle!", e);
        }
    }

    private static MethodHandle addOpensToAllHandle() {
        try {
            return findVirtual(Module.class, "implAddOpens", void.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddOpens(String) handle!", e);
        }
    }

    private static MethodHandle addOpensToAllUnnamedHandle() {
        try {
            return findVirtual(Module.class, "implAddOpensToAllUnnamed", void.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddOpensToAllUnnamed(String) handle!", e);
        }
    }

    private static MethodHandle addOpensToModuleHandle() {
        try {
            return findVirtual(Module.class, "implAddOpens", void.class, String.class, Module.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not get Module#implAddOpens(String, Module) handle!", e);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ReflectionUtils() {
        String callerBlame = "";
        try {
            callerBlame = " by " + getModuleInclusiveClassName(StackWalker.getInstance().getCallerClass());
        } catch (IllegalCallerException ignored) {

        }
        throw new UnsupportedOperationException("Instantiation attempted for " + getModuleInclusiveClassName(ReflectionUtils.class) + callerBlame);
    }

    /**
     * Helper class that makes working with {@link MethodHandle}s easier. <br>
     *
     * @apiNote Due to the different implementation of {@link MethodHandles.Lookup}, this class is not compatible with <a href="https://www.eclipse.org/openj9/">OpenJ9</a> VMs.
     *
     * @author Lollopollqo
     */
    private static final class MethodHandleHelper {

        /**
         * Trusted lookup
         */
        private static final MethodHandles.Lookup LOOKUP;

        static {
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
                    final int trusted = -1;
                    final long overrideOffset = UnsafeHelper.INTERNAL_UNSAFE.objectFieldOffset(AccessibleObject.class, "override");

                    if (overrideOffset == -1) {
                        throw new RuntimeException("Could not locate AccessibleObject#override!");
                    }

                    final Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);

                    UnsafeHelper.unsafeSetAccesible(lookupConstructor, true);
                    implLookup = lookupConstructor.newInstance(Object.class, null, trusted);
                }
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException("Could not create trusted lookup!", roe);
            }

            return implLookup;
        }
    }

    /**
     * Helper class that simplifies the process of bypassing module access checks <br>
     * (meaning exporting / opening packages to modules they aren't normally exported / opened to).
     *
     * @author Lollopollqo
     */
    static final class ModuleHelper {
        /**
         * An instance of {@link ModuleLayer.Controller}, used to export / open packages without restrictions
         */
        private static final ModuleLayer.Controller layerController;
        /**
         * The cached offset (in bytes) for the {@link ModuleLayer.Controller#layer} field in a {@link ModuleLayer.Controller} instance
         */
        private static final long layerFieldOffset;

        static {
            try {
                layerController = (ModuleLayer.Controller) UnsafeHelper.UNSAFE.allocateInstance(ModuleLayer.Controller.class);
                layerFieldOffset = UnsafeHelper.UNSAFE.objectFieldOffset(ModuleLayer.Controller.class.getDeclaredField("layer"));
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException(roe);
            }
        }

        private static void addExports(Module source, String packageName, Module target) {
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addExports(source, packageName, target);
        }

        private static void addOpens(Module source, String packageName, Module target) {
            UnsafeHelper.UNSAFE.putObject(layerController, layerFieldOffset, source.getLayer());
            layerController.addOpens(source, packageName, target);
        }
    }

    /**
     * Helper class that holds references to the {@link sun.misc.Unsafe} and {@link jdk.internal.misc.Unsafe} instances. <br>
     * This class also contains a few methods that make working with the two <code>Unsafe</code> classes easier.
     *
     * @author Lollopollqo
     */
    public static final class UnsafeHelper {
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
            // Needs to be done before calling enableJdkInternalsAccess() as it uses the sun.misc.Unsafe instance
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
            final String internalPackageName = "jdk.internal";
            final String miscPackageName = internalPackageName + ".misc";
            final String accessPackageName = internalPackageName + ".access";
            final String moduleName = "java.base";
            final Module javaBaseModule = Object.class.getModule().getLayer().findModule(moduleName).orElseThrow(
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
    }
}
