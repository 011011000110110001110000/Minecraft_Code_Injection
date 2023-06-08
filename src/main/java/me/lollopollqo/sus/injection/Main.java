package me.lollopollqo.sus.injection;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import me.lollopollqo.sus.injection.agent.Agent;
import me.lollopollqo.sus.injection.rmi.RemoteHandle;
import me.lollopollqo.sus.injection.util.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class Main {
    public static final int REGISTRY_PORT = Registry.REGISTRY_PORT;
    public static final String STOP_COMMAND = "STOP";
    private static final String MULTIMC_ENTRYPOINT = "org.multimc.EntryPoint";
    private static final String RMI_HELLO_MESSAGE = "This hello message was sent thanks to RMI!";
    private static long minecraftPID = -1;
    private static Registry registry;
    private static RemoteHandle agentHandleStub;

    public static void main(String[] args) throws RemoteException {
        Thread.currentThread().setName("Main Injector Thread");
        final String agentPath;

        try {
            agentPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            System.err.println("Could not determine agent jar path!");
            e.printStackTrace(System.err);
            return;
        }

        // Find Minecraft PID
        Main.minecraftPID = ProcessUtils.findProcessIdForClass(MULTIMC_ENTRYPOINT);

        if (Main.minecraftPID <= 0) {
            System.err.println("Could not find Minecraft process");
            return;
        }

        // Start RMI registry
        try {
            Main.registry = LocateRegistry.createRegistry(Main.REGISTRY_PORT);
        } catch (RemoteException e) {
            System.err.println("Could not find / create RMI registry!");
            e.printStackTrace(System.err);
            return;
        }

        final VirtualMachine vm;

        // Attach the agent
        try {
            vm = VirtualMachine.attach(String.valueOf(Main.minecraftPID));
            vm.loadAgent(agentPath);
        } catch (IOException | AttachNotSupportedException | AgentInitializationException | AgentLoadException e) {
            System.err.println("Something went wrong while attaching the agent!");
            e.printStackTrace(System.err);
            return;
        }

        try {
            Main.agentHandleStub = (RemoteHandle) Main.registry.lookup(Agent.REMOTE_HANDLE_NAME);
            System.out.println("Remote agent handle lookup was successful!");
        } catch (Exception e) {
            System.err.println("Could not find remote agent handle!");
            e.printStackTrace(System.err);
            return;
        }

        try {
            Main.agentHandleStub.submitTask(
                    () -> {
                        System.out.println(RMI_HELLO_MESSAGE);
                        try {
                            Agent.sendSystemMessage(RMI_HELLO_MESSAGE);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        } catch (ConnectException ce) {
            System.err.println("Could not print welcome message because the remote endpoint is unreachable!");
            ce.printStackTrace(System.err);
            return;
        }

        try (final Scanner scanner = new Scanner(System.in)) {
            final String prefix = "> ";
            System.out.println("You can now send messages to the remote process:");
            while (true) {
                System.out.print(prefix);
                if (scanner.hasNextLine()) {
                    final String message = scanner.nextLine();

                    if (message.equals(STOP_COMMAND)) {
                        try {
                            Main.agentHandleStub.submitTask(Agent::shutdown);
                            vm.detach();
                        } catch (RemoteException re) {
                            System.err.println("Could not submit Agent shutdown task!");
                            re.printStackTrace(System.err);
                        } catch (IOException ioe) {
                            System.err.println("Could not detach from target VM!");
                            ioe.printStackTrace(System.err);
                        }
                        return;
                    }

                    Main.agentHandleStub.submitTask(
                            () -> {
                                try {
                                    Agent.sendSystemMessage(message);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );
                }
            }
        } catch (ConnectException ce) {
            System.err.println("The remote agent handle is unreachable!");
        }
    }
}
