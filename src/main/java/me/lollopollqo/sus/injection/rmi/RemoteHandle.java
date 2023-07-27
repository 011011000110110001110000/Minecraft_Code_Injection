package me.lollopollqo.sus.injection.rmi;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * TODO: Add documentation
 *
 * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
 */
@FunctionalInterface
public interface RemoteHandle extends Remote {
    void submitTask(RemoteTask task) throws RemoteException;

    /**
     * Represents a task that will be sent to a {@link RemoteHandle} via RMI for execution.
     *
     * @author <a href=https://github.com/011011000110110001110000>011011000110110001110000</a>
     */
    interface RemoteTask extends Serializable, Runnable {
    }
}
