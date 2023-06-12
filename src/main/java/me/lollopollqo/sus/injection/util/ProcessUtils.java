package me.lollopollqo.sus.injection.util;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class contains utility methods to gather information about running Java Virtual Machines.
 *
 * @author Lollopollqo
 */
public final class ProcessUtils {
    /**
     * Used to determine the Process ID (PID) of the running Java process whose entrypoint class has the given name.
     *
     * @param fullyQualifiedName the fully qualified name of the entrypoint class for the process
     * @return the Process ID of the desired process, or <code>-1</code> if the process could not be found
     */
    public static long findProcessIdForClass(@NotNull String fullyQualifiedName) {
        return findProcessIdForClass(fullyQualifiedName, true);
    }

    /**
     * Used to determine the Process ID (PID) of the running Java process whose entrypoint class has the given name.
     *
     * @param name the name of the entrypoint class for the process
     * @param fullyQualified whether the provided name is fully qualified or just the simple name
     *
     * @return the Process ID of the desired process, or <code>-1</code> if the process could not be found
     */
    public static long findProcessIdForClass(@NotNull String name, boolean fullyQualified) {
        long pid = -1;

        for (VirtualMachineDescriptor vm : VirtualMachine.list()) {
            // Check if we found the correct process
            if (name.equals(mainClass(vm, fullyQualified))) {
                pid = Long.parseLong(vm.id());
                break;
            }
        }

        return pid;
    }

    /**
     * Used to obtain the name of the main class for the given virtual machine descriptor. <br>
     *
     * @param vm the virtual machine descriptor
     * @param fullyQualified whether to return the fully qualified name of the main class or just the simple name
     *
     * @return the fully qualified name of the main class for the given virtual machine descriptor if <code>fullyQualified</code> is <code>true</code>,
     * the simple name if <code>fullyQualified</code> is <code>false</code>,
     * or <code>null</code> if the name could not be determined.
     *
     * <br><br><em><b>Note</b>: Implementation mostly stolen from <code>sun.jvmstat.monitor.MonitoredVmUtil#mainClass(MonitoredVm, boolean)String</code></em>
     */
    @Nullable
    public static String mainClass(VirtualMachineDescriptor vm, boolean fullyQualified) {
        // This should return the result of sun.jvmstat.monitor.MonitoredVmUtil#commandLine(MonitoredVm)String if available
        // We could also abuse Unsafe and break the module system to get access to the internal JDK modules and call the method ourselves, but it is unnecessary
        String displayName = vm.displayName();

        // If the process ID and the display name are equal, it means something went wrong when trying to get the actual command line string, so return null
        if (displayName.equals(String.valueOf(vm.id()))) {
            return null;
        }

        int firstSpace = displayName.indexOf(' ');
        if (firstSpace > 0) {
            displayName = displayName.substring(0, firstSpace);
        }

        if (fullyQualified) {
            return displayName;
        }

        final int lastSlash = displayName.lastIndexOf("/");
        final int lastBackslash = displayName.lastIndexOf("\\");
        final int lastSeparator = Math.max(lastSlash, lastBackslash);

        if (lastSeparator > 0) {
            displayName = displayName.substring(lastSeparator + 1);
        }

        int lastPackageSeparator = displayName.lastIndexOf('.');
        if (lastPackageSeparator > 0) {
            final String lastPart = displayName.substring(lastPackageSeparator + 1);

            if (lastPart.equals("jar")) {
                return displayName;
            }

            return lastPart;
        }

        return displayName;
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ProcessUtils() {
        String callerBlame = "";
        try {
            callerBlame = " by " + ReflectionUtils.getModuleInclusiveClassName(StackWalker.getInstance().getCallerClass());
        } catch (IllegalCallerException ignored) {

        }
        throw new UnsupportedOperationException("Instantiation attempted for " + ReflectionUtils.getModuleInclusiveClassName(ProcessUtils.class) + callerBlame);
    }
}
