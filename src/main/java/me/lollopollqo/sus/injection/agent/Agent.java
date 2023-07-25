package me.lollopollqo.sus.injection.agent;

import me.lollopollqo.sus.injection.rmi.RemoteHandle;
import me.lollopollqo.sus.injection.util.ReflectionUtils;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * TODO: Add documentation
 *
 * @author Lollopollqo
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
        Thread.currentThread().setName("Lollopollqo's Java Agent");
        System.out.println(AGENT_HELLO_MESSAGE);

        try {
            sendMessageInGame();
        } catch (Exception e) {
            System.err.println("WARNING: could not send welcome message ingame!");
            e.printStackTrace(System.err);
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

        // Bind the remote handler's stub in the registry
        try {
            Agent.registry.rebind(REMOTE_HANDLE_NAME, Agent.handleStub);
            System.out.println("Handle binding complete!");
        } catch (Exception e) {
            System.err.println("Could not bind handle!");
            e.printStackTrace(System.err);
        }

    }

    private static void sendMessageInGame() throws Exception {
        sendSystemMessage(AGENT_HELLO_MESSAGE);
    }

    public static void sendSystemMessage(String message) throws Exception {
        final Class<?> MinecraftClass = ReflectionUtils.loadClass("emh");
        final Class<?> ComponentClass = ReflectionUtils.loadClass("tj");

        final Method literal = ReflectionUtils.getAccessibleDeclaredMethod(ComponentClass, "b", String.class);
        final Object textComponent = literal.invoke(ComponentClass, message);

        final Object instance = ReflectionUtils.getAccessibleDeclaredField(MinecraftClass, "F").get(MinecraftClass);

        final Object player = ReflectionUtils.getAccessibleDeclaredField(MinecraftClass, "t").get(instance);

        if (player != null) {
            final Method sendSystemMessage = ReflectionUtils.getAccessibleDeclaredMethod(player.getClass(), "a", ComponentClass);
            sendSystemMessage.invoke(player, textComponent);
        } else {
            System.err.println("Could not find player! Make sure you have joined a world.");
        }
    }

    public static void shutdown() {
        try {
            UnicastRemoteObject.unexportObject(Agent.handle, true);
        } catch (NoSuchObjectException e) {
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

    private static class Handle implements RemoteHandle {
        @Override
        public void submitTask(RemoteTask task) throws RemoteException {
            Agent.runRemoteTask(task);
        }
    }
}
