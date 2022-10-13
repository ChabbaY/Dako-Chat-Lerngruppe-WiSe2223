package edu.hm.dako.rmiBeispiel;

import java.io.Serial;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Beispiel für eine RMI RemoteObject-Implementierung
 *
 * @author Peter Mandl, edited by Lerngruppe
 */
public class oneServerImpl extends UnicastRemoteObject implements OneInterface {
    @Serial
    private static final long serialVersionUID = -12345L;

    /**
     * Standardkonstruktor muss RemoteException werfen
     */
    public oneServerImpl() throws RemoteException {
    }

    /**
     * Operation 1
     */
    public int op1(String s) throws RemoteException {
        try {
            System.out.println("op1 aufgerufen mit Parameter: " + s);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return (1);
    }

    /**
     * Operation 2
     */
    public long op2(String s) throws RemoteException {
        try {
            System.out.println("op2 aufgerufen mit Parameter: " + s);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return (2);
    }
}