package me.lollopollqo.sus.injection.agent;

import me.lollopollqo.sus.injection.rmi.RemoteHandle;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.NoSuchObjectException;
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
        Thread.currentThread().setName("Lollopollqo's Java Agent");
        System.out.println(AGENT_HELLO_MESSAGE);

        try {
            sayHello();
        } catch (Exception e) {
            System.err.println("WARNING: could not send welcome message in-game!");
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

        // Bind the remote handle's stub in the registry
        try {
            Agent.registry.rebind(REMOTE_HANDLE_NAME, Agent.handleStub);
            System.out.println("Handle binding complete!");
        } catch (Exception e) {
            System.err.println("Could not bind handle!");
            e.printStackTrace(System.err);
        }

    }

    private static void sayHello() throws Exception {
        sendSystemMessage(AGENT_HELLO_MESSAGE);
    }

    public static void sendSystemMessage(String message) throws Exception {
        final Class<?> MinecraftClass = Class.forName("emh");
        final Class<?> ComponentClass = Class.forName("tj");

        final Method literal = ComponentClass.getDeclaredMethod("b", String.class);
        literal.setAccessible(true);
        final Object textComponent = literal.invoke(ComponentClass, message);

        final Field instanceField = MinecraftClass.getDeclaredField("F");
        instanceField.setAccessible(true);

        final Object instance = instanceField.get(MinecraftClass);

        final Field playerField = MinecraftClass.getDeclaredField("t");
        playerField.setAccessible(true);

        final Object player = playerField.get(instance);

        if (player != null) {
            final Method sendSystemMessage = player.getClass().getDeclaredMethod("a", ComponentClass);
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
