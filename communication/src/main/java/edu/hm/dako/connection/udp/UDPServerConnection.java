package edu.hm.dako.connection.udp;

import edu.hm.dako.connection.Connection;

import java.io.Serializable;

/**
 * Verbindung aus Sicht des Servers über UDP
 */
public class UDPServerConnection implements Connection {
    private final UDPSocket serverSocket;

    private UDPPseudoConnectionContext udpRemoteObject; // Empfangene Request-PDU

    /**
     * Konstruktor
     *
     * @param serverSocket socket des server zu dem verbunden wird
     */
    public UDPServerConnection(UDPSocket serverSocket) {
        this.serverSocket = serverSocket;
        udpRemoteObject = new UDPPseudoConnectionContext();
    }

    /**
     * Der Empfang der Daten vom UDP-Client erfolgt bereits im Konstruktor. Diese
     * Methode gibt nur die bereits empfangene Nachricht zurück.
     *
     * @see edu.hm.dako.connection.Connection#receive()
     */
    @Override
    public Serializable receive(int timeout) throws Exception {
        Object pdu = serverSocket.receive(timeout);
        udpRemoteObject = new UDPPseudoConnectionContext(serverSocket.getRemoteAddress(), serverSocket.getRemotePort(),
                pdu);
        return (Serializable) udpRemoteObject.getObject();
    }

    public Serializable receive() throws Exception {
        Object pdu = serverSocket.receive(0);
        udpRemoteObject = new UDPPseudoConnectionContext(serverSocket.getRemoteAddress(), serverSocket.getRemotePort(),
                pdu);
        return (Serializable) udpRemoteObject.getObject();
    }

    @Override
    public void send(Serializable message) throws Exception {
        serverSocket.send(udpRemoteObject.getRemoteAddress(), udpRemoteObject.getRemotePort(), message);
    }

    /**
     * Dies ist nur eine Dummy-Methode. Der ServerSocket darf nicht geschlossen
     * werden, da der Server sonst keine Requests mehr entgegennehmen kann. Es
     * gibt im Unterschied zu TCP-Sockets keine Verbindungssockets bei UDP,
     * sondern nur ein UDP-Socket, über das alles empfangen wird.
     */
    @Override
    public void close() {
    }
}