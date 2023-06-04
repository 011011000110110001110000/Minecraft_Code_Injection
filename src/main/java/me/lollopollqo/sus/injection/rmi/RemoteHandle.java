package me.lollopollqo.sus.injection.rmi;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * TODO: Add documentation
 *
 * @author Lollopollqo
 */
@FunctionalInterface
public interface RemoteHandle extends Remote {
    void submitTask(RemoteTask task) throws RemoteException;

    interface RemoteTask extends Serializable, Runnable {
    }
}
