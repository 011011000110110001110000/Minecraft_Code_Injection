package me.lollopollqo.sus.injection.agent;

import me.lollopollqo.sus.injection.rmi.RemoteHandle;
import me.lollopollqo.sus.injection.util.ReflectionUtils;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * TODO: Add documentation
 * <br>
 * The agent class, whose {@link #agentmain(String, Instrumentation)} method
 * will run once it is attached to the target process.
 *
 * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
 */
public class Agent {
    private static final String AGENT_HELLO_MESSAGE = "Hello from a Java agent!";
    public static final String REMOTE_HANDLE_NAME = "_remoteHandle_";
    public static final int REMOTE_HANDLE_PORT = 8888;
    private static final RemoteHandle handle = new Handle();
    public static Registry registry;
    private static RemoteHandle handleStub;

    @SuppressWarnings("unused")
    public static void agentmain(String args, Instrumentation in) {
        Thread.currentThread().setName("Java Agent Thread");
        System.out.println(AGENT_HELLO_MESSAGE);

        try {
            sayHello();
        } catch (Throwable t) {
            System.err.println("WARNING: could not send welcome message in-game!");
            t.printStackTrace(System.err);
        }

        // Export the remote handle
        try {
            Agent.handleStub = (RemoteHandle) UnicastRemoteObject.exportObject(Agent.handle, REMOTE_HANDLE_PORT);
        } catch (RemoteException re) {
            System.err.println("Could not export remote agent handle!");
            re.printStackTrace(System.err);
            return;
        }

        // Get RMI registry reference
        try {
            Agent.registry = LocateRegistry.getRegistry();
        } catch (Exception e) {
            System.err.println("Could not get RMI registry!!");
            e.printStackTrace(System.err);
            return;
        }

        // Bind the remote handle's stub in the registry
        try {
            Agent.registry.rebind(REMOTE_HANDLE_NAME, Agent.handleStub);
            System.out.println("Handle binding complete!");
        } catch (Exception e) {
            System.err.println("Could not bind handle!");
            e.printStackTrace(System.err);
        }

    }

    private static void sayHello() throws Throwable {
        sendSystemMessage(AGENT_HELLO_MESSAGE);
    }

    public static void sendSystemMessage(String message) throws Throwable {
        final Object textComponent = Minecraft.literalHandle.invoke(message);
        final Object player = Minecraft.playerHandle.get(Minecraft.instanceHandle.get());

        if (player != null) {
            Minecraft.sendSystemMessageHandle.invoke(player, textComponent);
        } else {
            System.err.println("Could not find player! Make sure you have joined a world.");
        }
    }

    public static void shutdown() {
        try {
            sendSystemMessage("Bye!");
            UnicastRemoteObject.unexportObject(Agent.handle, true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void runRemoteTask(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            System.err.println("An exception occurred while executing remote task: ");
            e.printStackTrace(System.err);
        }
    }

    /**
     * Helper class that holds {@link MethodHandle}s and {@link VarHandle}s for methods / fields in the Minecraft jar.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static class Minecraft {
        private static final MethodHandle sendSystemMessageHandle;
        private static final MethodHandle literalHandle;
        private static final VarHandle instanceHandle;
        private static final VarHandle playerHandle;

        static {
            try {
                MethodHandles.Lookup lookup;

                final Class<?> MinecraftClass = ReflectionUtils.loadClass("emh");
                final Class<?> ComponentClass = ReflectionUtils.loadClass("tj");

                lookup = ReflectionUtils.lookupIn(ComponentClass);
                literalHandle = lookup.unreflect(ReflectionUtils.getAccessibleDeclaredMethod(ComponentClass, "b", String.class));

                lookup = ReflectionUtils.lookupIn(MinecraftClass);
                instanceHandle = lookup.unreflectVarHandle(ReflectionUtils.getAccessibleDeclaredField(MinecraftClass, "F"));
                playerHandle = lookup.unreflectVarHandle(ReflectionUtils.getAccessibleDeclaredField(MinecraftClass, "t"));

                final Class<?> playerClass = playerHandle.varType();

                sendSystemMessageHandle = lookup.unreflect(ReflectionUtils.getAccessibleDeclaredMethod(playerClass, "a", ComponentClass));

            } catch (Throwable t) {
                throw new RuntimeException("", t);
            }
        }

    }

    /**
     * Basic {@link RemoteHandle} implementation that just executes the given {@link me.lollopollqo.sus.injection.rmi.RemoteHandle.RemoteTask}.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    private static class Handle implements RemoteHandle {
        @Override
        public void submitTask(RemoteTask task) throws RemoteException {
            Agent.runRemoteTask(task);
        }
    }
}
