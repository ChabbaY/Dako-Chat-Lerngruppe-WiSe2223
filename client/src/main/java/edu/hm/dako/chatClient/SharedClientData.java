package edu.hm.dako.chatClient;

import edu.hm.dako.common.ClientConversationStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gemeinsame genutzte Daten, die sich der Chat-Client-Thread und die Message-Processing-Threads teilen
 *
 * @author Peter Mandl, edited by Lerngruppe
 */
public class SharedClientData {
    // Zähler für die Events aller Clients für
    // Testausgaben
    public static final AtomicInteger logoutEvents = new AtomicInteger(0);
    public static final AtomicInteger loginEvents = new AtomicInteger(0);
    public static final AtomicInteger messageEvents = new AtomicInteger(0);

    // LoginName des Clients
    public String userName;

    // Aktueller Zustand des Clients
    public ClientConversationStatus status;

    // Zähler für gesendete Chat-Nachrichten des Clients
    public AtomicInteger messageCounter;

    // Zähler für Logouts, empfangene Events und Confirms für
    // Testausgaben
    public AtomicInteger logoutCounter;
    public AtomicInteger eventCounter;
    public AtomicInteger confirmCounter;
}