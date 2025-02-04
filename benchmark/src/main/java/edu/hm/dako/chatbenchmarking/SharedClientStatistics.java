package edu.hm.dako.chatbenchmarking;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.concurrent.CountDownLatch;

/**
 * Die Klasse sammelt Statistikdaten zur Ermittlung von Round Trip Times (RTT) für einen Test zur
 * Kommunikation zwischen mehreren Client-Threads und einem Server.
 * Die Daten werden in einem Array gesammelt, das einen Eintrag für jeden Client enthält.
 * Jeder Client erhält eine Nummer, die als Zugriffsindex auf das Array verwendet wird.
 *
 * @author Peter Mandl, edited by Lerngruppe
 */
public class SharedClientStatistics {
    /**
     * referencing the logger
     */
    private static final Logger log = LogManager.getLogger(SharedClientStatistics.class);

    /**
     * Anzahl von Clients
     */
    private final int numberOfClients;

    /**
     * Anzahl der Nachrichten (Requests) eines Clients
     */
    private final int numberOfMessages;

    /**
     * Denkzeit eines Clients zwischen zwei Requests in ms
     */
    private final int clientThinkTime;

    /**
     * Alle Antwortnachrichten, die für den Test empfangen werden müssen
     */
    private final int numberOfAllMessages;

    /**
     * Kann benutzt werden, um ein gleichzeitiges Starten aller Client-Threads zu ermöglichen
     */
    private final CountDownLatch loginSignal;
    /**
     * Kann benutzt werden, um ein gleichzeitiges Logout aller Client-Threads zu ermöglichen,
     * erst nachdem alle Chat-Messages von allen Clients versendet wurden
     */
    private final CountDownLatch logoutSignal;

    /**
     * Client Statistik
     */
    private final ClientStatistics[] clientStatistics;

    /**
     * Alle Event-Nachrichten, die für den Test vom Server gesendet werden müssen
     */
    final long numberOfPlannedEventMessages;

    /**
     * Zählt angemeldete Clients
     */
    private int numberOfLoggedInClients;

    /**
     * Zählt abgemeldete Clients
     */
    private int numberOfLoggedOutClients;

    /**
     * Konstruktor
     *
     * @param numberOfClients  Anzahl an Clients
     * @param numberOfMessages Anzahl Nachrichten, die je Client gesendet werden sollen
     * @param clientThinkTime  Denkzeit
     */
    public SharedClientStatistics(int numberOfClients, int numberOfMessages, int clientThinkTime) {
        this.numberOfClients = numberOfClients;
        this.numberOfMessages = numberOfMessages;
        this.clientThinkTime = clientThinkTime;
        this.numberOfAllMessages = numberOfClients * numberOfMessages;
        this.numberOfPlannedEventMessages = (long) numberOfAllMessages * numberOfClients;
        loginSignal = new CountDownLatch(numberOfClients);
        logoutSignal = new CountDownLatch(numberOfClients);
        clientStatistics = new ClientStatistics[numberOfClients];

        // Initialisieren der Statistik-Tabelle
        for (int i = 0; i < numberOfClients; i++) {
            clientStatistics[i] = new ClientStatistics();
            clientStatistics[i].receivedResponses = 0;
            clientStatistics[i].sentRequests = 0;
            clientStatistics[i].numberOfRetries = 0;
            clientStatistics[i].numberOfSentEventMessages = 0;
            clientStatistics[i].numberOfReceivedConfirmEvents = 0;
            clientStatistics[i].numberOfLostConfirmEvents = 0;
            clientStatistics[i].numberOfRetriedEvents = 0;
            clientStatistics[i].averageRTT = 0;
            clientStatistics[i].maxRTT = 0;
            clientStatistics[i].minRTT = 0;
            clientStatistics[i].sumRTT = 0;
            clientStatistics[i].sumServerTime = 0;
            clientStatistics[i].maxHeapSize = 0;
            clientStatistics[i].rttList = new ArrayList<>();
        }
    }

    /**
     * Test, ob Client-Id im gültigen Bereich ist
     *
     * @param i Client-Id
     * @return true, falls Client-Id im gültigen Bereich ist. Sonst false.
     */
    private boolean notInRange(int i) {
        if ((i < 0) || (i > numberOfClients)) {
            log.error("Client-Id nicht im gültigen Bereich");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Login-Signal für das gleichzeitige Anlaufen von Clients ermitteln
     *
     * @return Login-Signal
     */
    public CountDownLatch getLoginSignal() {
        return loginSignal;
    }

    /**
     * Logout-Signal für das gleichzeitige Beenden von Clients ermitteln
     *
     * @return Login-Signal
     */
    public CountDownLatch getLogoutSignal() {
        return logoutSignal;
    }

    /**
     * Anzahl der angemeldeten Clients erhöhen
     */
    public synchronized void increaseNumberOfLoggedInClients() {
        numberOfLoggedInClients++;

        if (numberOfLoggedInClients == numberOfClients) {
            log.debug("Alle " + numberOfClients + " Test-Clients angemeldet");
        }
    }

    /**
     * Anzahl der angemeldeten Clients ausgeben
     *
     * @return Anzahl eingeloggter Clients
     */
    public synchronized int getNumberOfLoggedInClients() {
        return numberOfLoggedInClients;
    }

    /**
     * Anzahl der abgemeldeten Clients erhöhen
     */
    public synchronized void increaseNumberOfLoggedOutClients() {
        numberOfLoggedOutClients++;
        log.debug(numberOfLoggedOutClients + " Test-Clients abgemeldet");
        if (numberOfLoggedOutClients == numberOfClients) {
            log.debug("Alle " + numberOfClients + " Test-Clients abgemeldet");
        }
    }

    /**
     * Anzahl der gesendeten Nachrichten eines Clients erhöhen
     *
     * @param i Client-Id
     */
    public synchronized void increaseSentMsgCounter(int i) {
        if (notInRange(i)) return;
        clientStatistics[i].sentRequests++;
    }

    /**
     * Anzahl der gesendeten Events setzen
     *
     * @param i  Nummer des Client-Threads
     * @param nr Anzahl der gesendeten Event-Nachrichten
     */
    public synchronized void setNumberOfSentEventMessages(int i, long nr) {
        if (notInRange(i)) return;
        clientStatistics[i].numberOfSentEventMessages = nr;
    }

    /**
     * Anzahl der gesendeten Events lesen
     *
     * @param i Client-Id
     * @return Anzahl gesendeter Message Events
     */
    public synchronized long getNumberOfSentEventMessages(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].numberOfSentEventMessages;
    }

    /**
     * Anzahl der verlorenen Event-Bestätigungen setzen
     *
     * @param i  Client-Id
     * @param nr Anzahl an verloren gegangenen Confirm-Nachrichten
     */
    public synchronized void setNumberOfLostConfirmEvents(int i, long nr) {
        if (notInRange(i)) return;
        clientStatistics[i].numberOfLostConfirmEvents = nr;
    }

    /**
     * Anzahl der verlorenen Confirms lesen
     *
     * @param i Client-Id
     * @return Anzahl verlorener Confirm Events
     */
    public synchronized long getNumberOfLostConfirmEvents(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].numberOfLostConfirmEvents;
    }

    /**
     * Anzahl der empfangenen Event-Bestätigungen setzen
     *
     * @param i  Client-Id
     * @param nr Anzahl an empfangenen Confirm-Nachrichten
     */
    public synchronized void setNumberOfReceivedConfirmEvents(int i, long nr) {
        if (notInRange(i)) return;
        clientStatistics[i].numberOfReceivedConfirmEvents = nr;
    }

    /**
     * Anzahl der empfangenen Confirms lesen
     *
     * @param i Client-Id
     * @return Anzahl empfangener Confirm-Events
     */
    public synchronized long getNumberOfReceivedConfirmEvents(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].numberOfReceivedConfirmEvents;
    }

    /**
     * Anzahl der Event-Wiederholungen setzen
     *
     * @param i  Client-Id
     * @param nr Anzahl der Wiederholungen von Event-Nachrichten
     */
    public synchronized void setNumberOfRetriedEvents(int i, long nr) {
        if (notInRange(i)) return;
        clientStatistics[i].numberOfRetriedEvents = nr;
    }

    /**
     * Anzahl der wiederholten Confirms lesen
     *
     * @param i Client-Id
     * @return Anzahl der Wiederholungen von Event-Nachrichten
     */
    public synchronized long getNumberOfRetriedEvents(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].numberOfRetriedEvents;
    }

    /**
     * Anzahl der empfangenen Nachrichten eines Clients erhöhen
     *
     * @param i          Client-Id
     * @param rtt        RoundTrip Time
     * @param serverTime Die Zeit, die der Server benötigt hat
     */
    public synchronized void increaseReceivedMsgCounter(int i, long rtt, long serverTime) {
        if (notInRange(i)) return;

        clientStatistics[i].receivedResponses++;

        if (clientStatistics[i].receivedResponses > 1) {
            clientStatistics[i].minRTT = Math.min(rtt, clientStatistics[i].minRTT);
            clientStatistics[i].maxRTT = Math.max(rtt, clientStatistics[i].maxRTT);
        } else {
            clientStatistics[i].minRTT = rtt;
            clientStatistics[i].maxRTT = rtt;
        }

        clientStatistics[i].sumRTT = clientStatistics[i].sumRTT + rtt;
        clientStatistics[i].averageRTT = clientStatistics[i].sumRTT
                / clientStatistics[i].receivedResponses;
        clientStatistics[i].sumServerTime = clientStatistics[i].sumServerTime + serverTime;

        clientStatistics[i].avgServerTime = clientStatistics[i].sumServerTime
                / clientStatistics[i].receivedResponses;

        clientStatistics[i].rttList.add(rtt);
        if (clientStatistics[i].maxHeapSize < usedMemory()) {
            clientStatistics[i].maxHeapSize = usedMemory();
        }
    }

    /**
     * Test, ob alle Clients angemeldet sind
     *
     * @return true angemeldet; false nicht angemeldet
     */
    public synchronized boolean allClientsLoggedIn() {
        return numberOfLoggedInClients == numberOfClients;
    }

    /**
     * Anzahl aller empfangenen Event-Confirm-Nachrichten ermitteln
     *
     * @return Anzahl empfangener Event-Confirm-Nachrichten
     */
    public synchronized int getSumOfAllReceivedConfirmEvents() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].numberOfReceivedConfirmEvents;
        }
        return sum;
    }

    /**
     * Anzahl aller verlorenen Event-Confirm-Nachrichten ermitteln
     *
     * @return Anzahl verlorener Event-Confirm-Nachrichten
     */
    public synchronized int getSumOfAllLostConfirmEvents() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].numberOfLostConfirmEvents;
        }
        return sum;
    }

    /**
     * Anzahl aller Wiederholungen von Event-Nachrichten ermitteln
     *
     * @return Anzahl verlorener Event-Nachrichten
     */
    public synchronized int getSumOfAllRetriedEvents() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].numberOfRetriedEvents;
        }
        return sum;
    }

    /**
     * Anzahl aller gesendeten Event-Nachrichten ermitteln
     *
     * @return Anzahl gesendeten Event-Nachrichten
     */
    public synchronized int getSumOfAllSentEventMessages() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].numberOfSentEventMessages;
        }
        return sum;
    }

    /**
     * Anzahl aller empfangenen Nachrichten ermitteln
     *
     * @return Anzahl empfangener Nachrichten
     */
    public synchronized int getSumOfAllReceivedMessages() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].receivedResponses;
        }
        return sum;
    }

    /**
     * Anzahl aller Übertragungswiederholungen ermitteln
     *
     * @return Anzahl Übertragungswiederholungen
     */
    public synchronized int getSumOfAllRetries() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].numberOfRetries;
        }
        return sum;
    }

    /**
     * Durchschnittliche RTT ermitteln
     *
     * @return Durchschnittliche RTT
     */
    public synchronized long getAverageRTT() {
        long sum = 0;
        int nrClients = 0;

        for (int i = 0; i < numberOfClients; i++) {
            // Nur Threads, die mindestens eine Antwort bekommen haben,
            // verwenden
            if (clientStatistics[i].receivedResponses > 0) {
                sum = sum + clientStatistics[i].averageRTT;
                nrClients++;
            }
        }

        if (nrClients > 0)
            return (sum / nrClients);
        else
            return 0;
    }

    /**
     * Durchschnittliche RTT eines Clients ermittelt
     *
     * @param i Client-Id
     * @return Durchschnittliche RTT
     */
    public long getAverageRTT(int i) {
        return clientStatistics[i].averageRTT;
    }

    /**
     * Minimale RTT über alle Clients ermitteln
     *
     * @return Minimale RTT
     */
    public synchronized long getMinimumRTT() {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < numberOfClients; i++) {
            // Nur Threads, die mindestens eine Antwort bekommen haben verwenden
            if (clientStatistics[i].receivedResponses > 0) {
                min = Math.min(clientStatistics[i].minRTT, min);
            }
        }

        if (min == Long.MAX_VALUE) {
            return 0;
        } else {
            return min;
        }
    }

    /**
     * Maximale RTT über alle Clients ermitteln
     *
     * @return Maximale RTT
     */
    public synchronized long getMaximumRTT() {
        long max = -1;

        for (int i = 0; i < numberOfClients; i++) {
            // Nur Threads, die mindestens eine Antwort bekommen haben,
            // verwenden
            if (clientStatistics[i].receivedResponses > 0) {
                max = Math.max(clientStatistics[i].maxRTT, max);
            }
        }

        if (max == -1) {
            return 0;
        } else {
            return max;
        }
    }

    /**
     * Anzahl der gesendeten Requests aller Clients liefern
     *
     * @return Anzahl gesendeter Requests
     */
    public synchronized int getNumberOfSentRequests() {
        int sum = 0;

        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].sentRequests;
        }

        return sum;
    }

    /**
     * Anzahl der gesendeten Requests eines Clients liefern
     *
     * @param i Client-Id
     * @return Anzahl gesendeter Requests des Clients i
     */
    public synchronized int getNumberOfSentRequests(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].sentRequests;
    }

    /**
     * Anzahl der empfangenden Responses liefern
     *
     * @return Anzahl empfangener Responses
     */
    public synchronized int getNumberOfReceivedResponses() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].receivedResponses;
        }
        return sum;
    }

    /**
     * Anzahl der empfangenen Responses eines Clients liefern
     *
     * @param i Client-Id
     * @return Anzahl empfangenerResponses des Clients i
     */
    public synchronized int getNumberOfReceivedResponses(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].receivedResponses;
    }

    /**
     * Anzahl der verlorenen Responses liefern
     *
     * @return Anzahl verlorenen Responses
     */
    public synchronized int getNumberOfLostResponses() {
        int sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].sentRequests;
        }
        return sum - getNumberOfReceivedResponses();
    }

    /**
     * Anzahl der verlorenen Responses eines Clients liefern
     *
     * @param i Client-Id
     * @return Anzahl verlorenen Responses des Clients i
     */
    public synchronized int getNumberOfLostResponses(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].sentRequests - getNumberOfReceivedResponses(i);
    }

    /**
     * Anzahl der Übertragungswiederholungen eines Clients liefern
     *
     * @param i Client-Id
     * @return Anzahl Übertragungswiederholungen des Clients i
     */
    public synchronized int getNumberOfRetries(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].numberOfRetries;
    }

    /**
     * Gesamte RTT über einen Client ermitteln
     *
     * @param i Client-Id
     * @return RTT oder -1 bei falscher Client-Id
     */
    public synchronized long getSumRTT(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].sumRTT;
    }

    /**
     * Gesamte RTT über alle Clients ermitteln
     *
     * @return RTT
     */
    public synchronized long getSumRTT() {
        long sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].sumRTT;
        }
        return sum;
    }

    /**
     * Serverzeit eines Clients ermitteln
     *
     * @param i Client-Id
     * @return Serverzeit
     */
    public synchronized long getSumServerTime(int i) {
        if (notInRange(i)) return (-1);
        return clientStatistics[i].sumServerTime;
    }

    /**
     * Gesamtliste über alle RTTs bilden
     *
     * @return ArrayList
     */
    public synchronized DistributionMetrics calculateMetrics() {
        ArrayList<Long> completeList = new ArrayList<>();
        DistributionMetrics distributionMetrics = new DistributionMetrics();

        // RTT-Listen aller Clients zusammenfügen
        for (int i = 0; i < numberOfClients; i++) {
            completeList.addAll(clientStatistics[i].rttList);
        }

        // Sortieren der gesamten RTT-Liste
        Collections.sort(completeList);
        double[] doubleList = new double[completeList.size()];

        try {
            // RTT-Werte konvertieren zu double-Werten
            for (int i = 0; i < completeList.size(); i++) {
                doubleList[i] = completeList.get(i).doubleValue();
            }

            // Percentile berechnen
            Percentile percentile = new Percentile();
            percentile.setData(doubleList);
            distributionMetrics.setPercentile10(percentile.evaluate(10) / 1000000.0);
            distributionMetrics.setPercentile25(percentile.evaluate(25) / 1000000.0);
            distributionMetrics.setPercentile50(percentile.evaluate(50) / 1000000.0);
            distributionMetrics.setPercentile75(percentile.evaluate(75) / 1000000.0);
            distributionMetrics.setPercentile90(percentile.evaluate(90) / 1000000.0);

            distributionMetrics.setInterQuartilRange(
                    distributionMetrics.percentile75 - distributionMetrics.percentile25);

            // Maximum berechnen
            Max max = new Max();
            max.setData(doubleList);
            distributionMetrics.setMaximum(max.evaluate() / 1000000.0);

            // Minimum berechnen
            Min min = new Min();
            min.setData(doubleList);
            distributionMetrics.setMinimum(min.evaluate() / 1000000.0);

            // Spannweite und IQR berechnen
            distributionMetrics
                    .setRange(distributionMetrics.maximum - distributionMetrics.minimum);

            // Arithmetisches Mittel berechnen
            Mean mean = new Mean();
            mean.setData(doubleList);
            distributionMetrics.setMean(mean.evaluate() / 1000000.0);

            // Varianz berechnen
            Variance variance = new Variance();
            variance.setData(doubleList);
            distributionMetrics.setVariance(variance.evaluate() / 1000000.0);

            // Standardabweichung berechnen
            StandardDeviation standardDeviation = new StandardDeviation();
            standardDeviation.setData(doubleList);
            distributionMetrics.setStandardDeviation(standardDeviation.evaluate() / 1000000.0);

        } catch (MathIllegalArgumentException e) {
            log.error("Fehler bei der Berechnung der Verteilungsmetriken");
        }
        return distributionMetrics;
    }

    /**
     * Gesamte Serverzeit über alle Clients ermitteln
     *
     * @return Serverzeit
     */
    public synchronized long getSumServerTime() {
        long sum = 0;
        for (int i = 0; i < numberOfClients; i++) {
            sum += clientStatistics[i].sumServerTime;
        }
        return sum;
    }

    /**
     * Durchschnittliche Serverbearbeitungszeit ermitteln
     *
     * @return Serverbearbeitungszeit
     */
    public synchronized long getAverageServerTime() {
        long sum = 0;
        long nrClients = 0;

        for (int i = 0; i < numberOfClients; i++) {
            // Nur Threads, die mindestens eine Antwort bekommen haben,
            // verwenden
            if (clientStatistics[i].receivedResponses > 0) {
                sum = sum + clientStatistics[i].avgServerTime;
                nrClients++;
            }
        }

        if (nrClients > 0)
            return (sum / nrClients);
        else
            return 0;
    }

    /**
     * Maximale Heap-Größe über alle Clients ermitteln
     *
     * @return Maximale Heap-Größe
     */
    public synchronized long getMaxHeapSize() {
        long max = -1;

        for (int i = 0; i < numberOfClients; i++) {
            // Nur Threads, die mindestens eine Antwort bekommen haben,
            // verwenden
            if (clientStatistics[i].receivedResponses > 0) {
                if (clientStatistics[i].maxHeapSize > max) {
                    max = clientStatistics[i].maxHeapSize;
                }
            }
        }
        return max;
    }

    /**
     * Ausgabe Statistikdaten für einen Client
     *
     * @param i Client-Id
     */
    public synchronized void printClientStatistic(int i) {
        if (notInRange(i)) return;

        System.out
                .println("********************** Client-Statistik *****************************"
                        + "\n" + "Sende-/Empfangsstatistik des Clients mit Id " + i + " ("
                        + Thread.currentThread().getName() + ")" + "\n" + "Anzahl Requests gesendet: "
                        + this.getNumberOfSentRequests(i) + "\n" + "Anzahl empfangener Responses: "
                        + this.getNumberOfReceivedResponses(i) + "\n"
                        + "Anzahl verlorener Responses: " + this.getNumberOfLostResponses(i) + "\n"
                        + "Anzahl Übertragungswiederholungen: " + this.getNumberOfRetries(i) + "\n"
                        + "Anzahl aller gesendeten Events: " + this.getNumberOfSentEventMessages(i)
                        + "\n" + "Anzahl aller gesendeten Events-Confirms: "
                        + this.getNumberOfReceivedConfirmEvents(i) + "\n"
                        + "Anzahl aller nicht empfangenen Event-Confirms: "
                        + this.getNumberOfLostConfirmEvents(i) + "\n"
                        + "Anzahl aller Event-Wiederholungen: " + this.getNumberOfRetriedEvents(i)
                        + "\n" + "Durchschnittliche RTT: " + this.getAverageRTT(i) + " ns = "
                        + this.getAverageRTT(i) / 1000000 + " ms" + "\n" + "Gesamte RTT: "
                        + this.getSumRTT(i) + " ns = " + this.getSumRTT(i) / 1000000 + " ms" + "\n"
                        + "Gesamte Serverzeit: " + this.getSumServerTime(i) + " ns = "
                        + this.getSumServerTime(i) / 1000000 + " ms" + "\n"
                        + "Gesamte Kommunikationszeit: "
                        + (this.getSumRTT(i) - this.getSumServerTime(i)) + " ns = "
                        + (this.getSumRTT(i) - this.getSumServerTime(i) / 1000000) + " ms" + "\n"
                        + "********************** Ende Client-Statistik ************************");
    }

    /**
     * Ausgabe aller Statistikdaten
     */
    public synchronized void printStatistic() {
        NumberFormat n = NumberFormat.getInstance();
        // n.setMaximumFractionDigits(2);
        String usedMemoryAsString = n.format(usedMemory() / (1024 << 10));

        System.out.println(
                "*********************************************************************" + "\n"
                        + "***************************** Statistik *****************************"
                        + "\n" + "Geplante Requests: " + numberOfAllMessages + "\n"
                        + "Anzahl Clients: " + numberOfClients + "\n"
                        + "Denkzeit des Clients zwischen zwei Requests: " + clientThinkTime + " ms"
                        + "\n" + "Anzahl gesendeter Requests: " + this.getNumberOfSentRequests()
                        + "\n" + "Anzahl empfangener Responses: " + this.getSumOfAllReceivedMessages()
                        + " von erwarteten " + numberOfAllMessages + "\n"
                        + "Anzahl Übertragungswiederholungen: " + this.getSumOfAllRetries() + "\n"
                        + "Anzahl geplanter Events (nur für Chat-Nachrichten): "
                        + numberOfPlannedEventMessages + "\n" + "Anzahl aller gesendeten Events: "
                        + this.getSumOfAllSentEventMessages() + "\n"
                        + "Anzahl aller gesendeten Events-Confirms: "
                        + this.getSumOfAllReceivedConfirmEvents() + "\n"
                        + "Anzahl aller nicht empfangenen Event-Confirms: "
                        + this.getSumOfAllLostConfirmEvents() + "\n"
                        + "Anzahl aller Event-Wiederholungen: " + this.getSumOfAllRetriedEvents()
                        + "\n" + "\n" + "Gesamte RTT über alle Clients: " + this.getSumRTT()
                        + " ns (" + (this.getSumRTT() / 1000000.0) + " ms)" + "\n"
                        + "Gesamte Serverzeit über alle Clients: " + this.getSumServerTime()
                        + " ns  (" + (this.getSumServerTime() / 1000000.0) + " ms)" + "\n"
                        + "Reine Kommunikationszeit über alle Clients: "
                        + (this.getSumRTT() - this.getSumServerTime()) + " ns ("
                        + ((this.getSumRTT() - this.getSumServerTime()) / 1000000.0) + " ms)" + "\n\n"
                        + "Durchschnittswerte über alle Clients:" + "\n" + "RTT: "
                        + this.getAverageRTT() + " ns (" + this.getAverageRTT() / 1000000.0 + " ms)"
                        + "\n" + "Minimum RTT: " + this.getMinimumRTT() + " ns ("
                        + this.getMinimumRTT() / 1000000 + " ms)" + "\n" + "Maximum RTT: "
                        + this.getMaximumRTT() + " ns (" + this.getMaximumRTT() / 1000000.0 + " ms)"
                        + "\n" + "Reine Serverzeit: " + this.getAverageServerTime() + " ns ("
                        + (this.getAverageServerTime() / numberOfClients) / 1000000.0 + " ms)" + "\n"
                        + "Maximal erreichte Heap-Belegung: " + usedMemoryAsString + " MByte"

                        + "\n"
                        + "************************ Ende Statistik *****************************"
                        + "\n"
                        + "*********************************************************************");
    }

    /**
     * Ausgabe eines Auswertungssatzes für eine Messung (einen Benchmark-Lauf) in eine Datei im CSV-Dateiformat in
     * folgender Form:
     * <p>
     * 01 Messungstyp als String
     * <p>
     * 02 Implementierungstyp als String
     * <p>
     * 03 Anzahl Client-Threads
     * <p>
     * 04 Anzahl (geplante) Nachrichten je Client
     * <p>
     * 05 10%-Percentile
     * <p>
     * 06 25%-Percentile
     * <p>
     * 07 50%-Percentile
     * <p>
     * 08 75%-Percentile
     * <p>
     * 09 90%-Percentile
     * <p>
     * 10 Spannweite (Range)
     * <p>
     * 11 Interquartilsabstand (IQR)
     * <p>
     * 12 Minimum
     * <p>
     * 13 Maximum
     * <p>
     * 14 arithmetisches Mittel
     * <p>
     * 15 Standardabweichung
     * <p>
     * 16 Durchschnittliche Serverbearbeitungszeit
     * <p>
     * 17 Anzahl geplanter Requests/Responses
     * <p>
     * 18 Anzahl gesendeter Requests
     * <p>
     * 19 Anzahl empfangener Responses
     * <p>
     * 20 Anzahl verlorener Echo Responses
     * <p>
     * 21 Anzahl aller Übertragungswiederholungen (nur für UDP relevant)
     * <p>
     * 22 Anzahl aller vom Server gesendete Events-Nachrichten
     * <p>
     * 23 Anzahl aller vom Server empfangenen Event-Bestätigungen (Confirm-Event)
     * <p>
     * 24 Anzahl aller vom Server nicht erhaltenen Event-Bestätigungen
     * <p>
     * 25 Anzahl aller Wiederholungen von Events (für unzuverlässige Verbindungen wie UDP)
     * <p>
     * 26 Maximale Heap-Size des Clients
     * <p>
     * 27 durchschnittliche CPU-Auslastung des Clients in %
     * <p>
     * 28 Startzeit der Messung
     * <p>
     * 29 Endzeit der Messung
     * <p>
     * Der Satz wird an das Ende einer bestehenden Datei angehängt.
     * Die Datei kann zur Testauswertung in Excel weiterverarbeitet werden.
     *
     * @param fileName       Name der Datei
     * @param implType       Typ der Implementierung
     * @param measureType    Typ der Messung
     * @param startTime      Startzeitpunkt der Messung
     * @param endTime        Endzeitpunkt der Messung
     * @param averageCpuTime Durchschnittliche CPU-Nutzungszeit
     */
    public synchronized void writeStatisticSet(String fileName, String implType, String measureType, String startTime,
                                               String endTime, float averageCpuTime) {
        File file = new File(fileName);

        // Verteilungsmetriken berechnen
        DistributionMetrics distributionMetrics = calculateMetrics();

        // Datei anlegen, wenn notwendig
        try {
            boolean exist = file.createNewFile();
            if (!exist) {
                log.debug("Datei " + fileName + " existierte bereits");
            } else {
                log.debug("Datei " + fileName + " erfolgreich angelegt");
            }

            // Datei zum Erweitern öffnen
            FileWriter fileWriter = new FileWriter(fileName, StandardCharsets.UTF_8, true);
            BufferedWriter out = new BufferedWriter(fileWriter);

            StringBuilder sb = new StringBuilder();
            Formatter formatter = new Formatter();

            sb.append(formatter.format(
                    "%s | %s | %d | %d | %05.2f | %05.2f | %05.2f | %05.2f | %05.2f | %05.2f | %05.2f | %05.2f | " +
                            "%05.2f | %05.2f |  %05.2f |  %05.2f | %d | %d | %d | %d | %d | %d | %d | %d | %d | " +
                            "%d | %02.2f | %s | %s%n",
                    measureType, implType, numberOfClients, numberOfMessages,
                    distributionMetrics.getPercentile10(), distributionMetrics.getPercentile25(),
                    distributionMetrics.getPercentile50(), distributionMetrics.getPercentile75(),
                    distributionMetrics.getPercentile90(), distributionMetrics.getRange(),
                    distributionMetrics.getInterQuartilRange(), distributionMetrics.getMinimum(),
                    distributionMetrics.getMaximum(), distributionMetrics.getMean(),
                    distributionMetrics.getStandardDeviation(), this.getAverageServerTime() / 1000000.0,
                    this.numberOfAllMessages, this.getNumberOfSentRequests(), this.getNumberOfReceivedResponses(),
                    this.getNumberOfLostResponses(), this.getSumOfAllRetries(), this.getSumOfAllSentEventMessages(),
                    this.getSumOfAllReceivedConfirmEvents(), this.getSumOfAllLostConfirmEvents(),
                    this.getSumOfAllRetriedEvents(), this.getMaxHeapSize() / (1024 << 10),
                    (double) (averageCpuTime * 100), startTime, endTime));

            out.append(sb);
            formatter.close();
            System.out.println("Auswertungssatz in Datei " + fileName + " geschrieben");
            out.flush();
            out.close();
        } catch (IOException e) {
            log.error("Fehler beim Schreiben des Auswertungssatzes in Datei " + fileName);
        }
    }

    /**
     * Berechnet den tatsächlich benutzten Heap-Speicher Heap-Größe in MiB
     *
     * @return Verwendeter Heap-Speicher
     */
    private long usedMemory() {
        Runtime r = Runtime.getRuntime();
        return ((r.totalMemory() - r.freeMemory()));
    }

    /**
     * Statistikdaten eines Clients
     */
    private static class ClientStatistics {
        /**
         * Anzahl gesendeter Nachrichten
         */
        int sentRequests;

        /**
         * Anzahl empfangener Antworten
         */
        int receivedResponses;

        /**
         * Anzahl an Übertragungswiederholungen (für unzuverlässige Verbindungen wie UDP)
         */
        int numberOfRetries;

        /**
         * Anzahl gesendeter Events für den Client
         */
        long numberOfSentEventMessages;

        /**
         * Anzahl empfangener Responses für den Client
         */
        long numberOfReceivedConfirmEvents;

        /**
         * Anzahl verlorener Event-Bestätigungen für den Client
         */
        long numberOfLostConfirmEvents;

        /**
         * Anzahl von wiederholten Events für den Client (für unzuverlässige Verbindungen wie UDP)
         */
        long numberOfRetriedEvents;

        /**
         * Durchschnittliche Round Trip Time in ns
         */
        long averageRTT;

        /**
         * Maximale Round Trip Time in ns
         */
        long maxRTT;

        /**
         * Minimale Round Trip Time in ns
         */
        long minRTT;

        /**
         * Summe aller RTTs in ns
         */
        long sumRTT;

        /**
         * Alle RTTs werden hier für die Quartils-Ermittlung gesammelt
         */
        ArrayList<Long> rttList;

        /**
         * Zeit, die der Server insgesamt für alle Requests benötigt in ns
         */
        long sumServerTime;

        /**
         * Zeit, die der Server im Durchschnitt für einen Request benötigt in ns
         */
        long avgServerTime;

        /**
         * Maximale Heap-Größe in Bytes während eines Testlaufs
         */
        long maxHeapSize;
    }
}