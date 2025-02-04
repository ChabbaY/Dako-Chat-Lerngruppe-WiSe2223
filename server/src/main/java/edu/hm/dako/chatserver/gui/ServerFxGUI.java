package edu.hm.dako.chatserver.gui;

import edu.hm.dako.chatserver.ServerFactory;
import edu.hm.dako.chatserver.ServerInterface;
import edu.hm.dako.chatserver.ServerStartData;
import edu.hm.dako.chatserver.ServerStarter;
import edu.hm.dako.common.AuditLogImplementationType;
import edu.hm.dako.common.ChatServerImplementationType;
import edu.hm.dako.common.ExceptionHandler;
import edu.hm.dako.common.SystemConstants;
import edu.hm.dako.common.Tupel;
import edu.hm.dako.common.gui.FxGUI;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benutzeroberfläche zum Starten des Chat-Servers
 *
 * @author Peter Mandl, edited by Lerngruppe
 */
public class ServerFxGUI extends FxGUI implements ServerGUIInterface {
    /**
     * Standard-Port des Servers
     */
    static final String DEFAULT_SERVER_PORT = "50001";

    /**
     * referencing the logger
     */
    private static final Logger LOG = LogManager.getLogger(ServerFxGUI.class);

    /**
     * Interface der Chat-Server-Implementierung
     */
    private static ServerInterface chatServer;

    /**
     * Zähler für die eingeloggten Clients und die empfangenen Request
     */
    private static AtomicInteger loggedInClientCounter, requestCounter;

    /**
     * Daten, die beim Start der GUI übergeben werden
     */
    private final ServerStartData data = new ServerStartData();

    /**
     * Mögliche Belegungen des Implementierungsfeldes in der GUI
     */
    final ObservableList<String> implTypeOptions = FXCollections.observableArrayList(
            SystemConstants.IMPL_TCP_SIMPLE, SystemConstants.IMPL_TCP_ADVANCED);
    final ObservableList<String> auditLogServerImplTypeOptions = FXCollections.observableArrayList(
            SystemConstants.AUDIT_LOG_SERVER_TCP_IMPL, SystemConstants.AUDIT_LOG_SERVER_UDP_IMPL,
            SystemConstants.AUDIT_LOG_SERVER_RMI_IMPL);

    /**
     * Server-Startzeit als String
     */
    private String startTimeAsString;

    /**
     * Kalender zur Umrechnung der Startzeit
     */
    private Calendar cal;

    /**
     * Flag, das angibt, ob der Server gestartet werden kann (alle Plausibilitätsprüfungen erfüllt)
     */
    private boolean startable = true;

    /**
     * ComboBox für Eingabe des Implementierungstyps
     */
    private ComboBox<String> comboBoxImplType;

    /**
     * ComboBox für AuditLogServer-Implementierung
     */
    private ComboBox<String> comboBoxAuditLogServerType;

    /**
     * Testfelder, Buttons und Labels der ServerGUI
     */
    private TextField serverPort, sendBufferSize, receiveBufferSize, auditLogServerHostnameOrIp, auditLogServerPort;
    private CheckBox enableAuditLogServerCheckbox;
    private Button startButton, stopButton, finishButton;
    private final TextField startTimeField, receivedRequests, loggedInClients;

    /**
     * saving args for further processing
     */
    private static String[] args;

    /**
     * Benutzeroberfläche zum Starten des Chat-Servers
     *
     * @param args available args, please do not change order:
     *             --protocol=tcpsimple (default; tcpadvanced not implemented yet)
     *             --port=50001 (default)
     *             --send-buffer=300000 (default)
     *             --receive-buffer=300000 (default)
     *             --auditlog=true | false (default true)
     *             --auditlog-host=localhost (default)
     *             --auditlog-port=40001 (default)
     *             --auditlog-protocol=tcp | udp | rmi (default tcp)
     */
    public static void main(String[] args) {
        // Log4j2-Logging aus Datei konfigurieren
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        File file = new File("config/log4j/log4j2.chatServer.xml");
        context.setConfigLocation(file.toURI());

        ServerFxGUI.args = args;

        // Anwendung starten
        launch(args);
    }

    /**
     * Konstruktion der ServerGUI
     */
    public ServerFxGUI() {
        super("ChatServerGUI", 400, 540);

        loggedInClientCounter = new AtomicInteger(0);
        requestCounter = new AtomicInteger(0);
        startTimeField = createNotEditableTextField();
        receivedRequests = createNotEditableTextField();
        loggedInClients = createNotEditableTextField();
    }

    @Override
    public void start(final Stage stage) throws IllegalArgumentException {
        super.start(stage);

        stage.setOnCloseRequest(event -> {
            try {
                ServerFxGUI.chatServer.stop();
            } catch (Exception ex) {
                LOG.error("Fehler beim Stoppen des Chat-Servers");
                ExceptionHandler.logException(ex);
            }
        });

        pane.setPadding(new Insets(10, 10, 10, 10));

        HBox label_eingabe = createHeader("Eingabe");
        pane.getChildren().add(label_eingabe);
        pane.getChildren().add(createInputPane());

        HBox label_informationen = createHeader("Informationen");
        pane.getChildren().add(label_informationen);
        pane.getChildren().add(createInfoPane());

        pane.getChildren().add(createHeader(""));
        pane.getChildren().add(createButtonPane());

        String auditlog_protocol = SystemConstants.AUDIT_LOG_SERVER_TCP_IMPL;
        for(String s: args) {
            String[] values = s.split("=");
            switch (values[0]) {
                case "--protocol" -> {
                    if ("tcpadvanced".equals(values[1])) {
                        comboBoxImplType.setValue(SystemConstants.IMPL_TCP_ADVANCED);
                    }
                }
                case "--port" -> {
                    Tupel<Integer, Boolean> result = ServerStarter.validateServerPort(values[1]);
                    if (result.getY()) serverPort.setText(result.getX().toString());
                }
                case "--send-buffer" -> {
                    Tupel<Integer, Boolean> result = ServerStarter.validateSendBufferSize(values[1]);
                    if (result.getY()) sendBufferSize.setText(result.getX().toString());
                }
                case "--receive-buffer" -> {
                    Tupel<Integer, Boolean> result = ServerStarter.validateReceiveBufferSize(values[1]);
                    if (result.getY()) receiveBufferSize.setText(result.getX().toString());
                }
                case "--auditlog" -> {
                    if ("false".equals(values[1])) {
                        enableAuditLogServerCheckbox.setSelected(false);
                    }
                }
                case "--auditlog-protocol" -> {
                    if ("udp".equals(values[1])) {
                        auditlog_protocol = SystemConstants.AUDIT_LOG_SERVER_UDP_IMPL;
                    } else if ("rmi".equals(values[1])) {
                        auditlog_protocol = SystemConstants.AUDIT_LOG_SERVER_RMI_IMPL;
                    }
                    comboBoxAuditLogServerType.setValue(auditlog_protocol);
                }
                case "--auditlog-host" -> auditLogServerHostnameOrIp.setText(values[1]);
                case "--auditlog-port" -> {
                    Tupel<Integer, Boolean> result = ServerStarter.validateAuditLogServerPort(values[1],
                            auditlog_protocol);
                    if (result.getY()) auditLogServerPort.setText(result.getX().toString());
                }
            }
        }

        reactOnStartButton();
        reactOnStopButton();
        reactOnFinishButton();
        stopButton.setDisable(true);
    }

    /**
     * Eingabe-Pane erzeugen
     *
     * @return pane
     */
    private GridPane createInputPane() {
        final GridPane inputPane = new GridPane();

        final Label label = createLabel("Serverauswahl");
        label.setMinSize(100, 25);
        label.setMaxSize(100, 25);
        Label auditLogServerHostnameOrIpLabel = createLabel("AuditLogServer Hostname/IP-Adr.");

        Label serverPortLabel = createLabel("Serverport");
        Label sendBufferSizeLabel = createLabel("Sendepuffer in Byte");
        Label receiveBufferSizeLabel = createLabel("Empfangspuffer in Byte");
        Label auditLogServerPortLabel = createLabel("AuditLogServer/RMI-Registry Port");
        sendBufferSize = createEditableTextField(SystemConstants.DEFAULT_SEND_BUFFER_SIZE);
        receiveBufferSize = createEditableTextField(SystemConstants.DEFAULT_RECEIVE_BUFFER_SIZE);

        Label auditLogActivate = createLabel("AuditLog aktivieren");
        Label auditLogConnectionType = createLabel("AuditLog-Server Verbindungstyp");

        inputPane.setPadding(new Insets(5, 5, 5, 5));
        inputPane.setVgap(1);

        comboBoxImplType = createImplTypeComboBox(implTypeOptions);
        serverPort = createEditableTextField(DEFAULT_SERVER_PORT);
        auditLogServerHostnameOrIp = createEditableTextField(SystemConstants.DEFAULT_AUDIT_LOG_SERVER_NAME);
        auditLogServerPort = createEditableTextField(SystemConstants.DEFAULT_AUDIT_LOG_SERVER_PORT);
        comboBoxAuditLogServerType = createAuditLogTypeComboBox(auditLogServerImplTypeOptions);

        enableAuditLogServerCheckbox = new CheckBox();
        enableAuditLogServerCheckbox.setSelected(true);

        enableAuditLogServerCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (enableAuditLogServerCheckbox.isSelected()) {
                auditLogServerHostnameOrIp.setEditable(true);
                auditLogServerHostnameOrIp.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;" +
                        "-fx-border-radius: 5px, 5px, 5px, 5px");
                auditLogServerPort.setEditable(true);
                auditLogServerPort.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;" +
                        "-fx-border-radius: 5px, 5px, 5px, 5px");
                comboBoxAuditLogServerType.setEditable(true);
                comboBoxAuditLogServerType.setStyle("-fx-background-color: white; -fx-border-color: lightgrey;" +
                        "-fx-border-radius: 5px, 5px, 5px, 5px");
            } else {
                auditLogServerHostnameOrIp.setEditable(false);
                auditLogServerHostnameOrIp.setStyle("-fx-background-color: gray;");
                auditLogServerPort.setEditable(false);
                auditLogServerPort.setStyle("-fx-background-color: gray;");
                comboBoxAuditLogServerType.setEditable(false);
                comboBoxAuditLogServerType.setStyle("-fx-background-color: gray;");
            }
        });

        inputPane.add(label, 1, 3);
        inputPane.add(comboBoxImplType, 3, 3);
        inputPane.add(serverPortLabel, 1, 5);
        inputPane.add(serverPort, 3, 5);

        inputPane.add(sendBufferSizeLabel, 1, 7);
        inputPane.add(sendBufferSize, 3, 7);
        inputPane.add(receiveBufferSizeLabel, 1, 9);
        inputPane.add(receiveBufferSize, 3, 9);

        inputPane.add(auditLogActivate, 1, 11);
        inputPane.add(enableAuditLogServerCheckbox, 3, 11);
        inputPane.add(auditLogServerHostnameOrIpLabel, 1, 13);
        inputPane.add(auditLogServerHostnameOrIp, 3, 13);
        inputPane.add(auditLogServerPortLabel, 1, 15);
        inputPane.add(auditLogServerPort, 3, 15);
        inputPane.add(auditLogConnectionType, 1, 17);
        inputPane.add(comboBoxAuditLogServerType, 3, 17);

        return inputPane;
    }

    /**
     * Info-Pain erzeugen
     *
     * @return pane
     */
    private GridPane createInfoPane() {
        GridPane infoPane = new GridPane();
        infoPane.setPadding(new Insets(5, 5, 5, 5));
        infoPane.setVgap(1);

        infoPane.add(createLabel("Startzeit"), 1, 3);
        infoPane.add(startTimeField, 3, 3);

        infoPane.add(createLabel("Empfangene Requests"), 1, 5);
        infoPane.add(receivedRequests, 3, 5);

        infoPane.add(createLabel("Angemeldete Clients"), 1, 7);
        infoPane.add(loggedInClients, 3, 7);
        return infoPane;
    }

    /**
     * Pane für Buttons erzeugen
     *
     * @return HBox
     */
    private HBox createButtonPane() {
        final HBox buttonPane = new HBox(5);

        startButton = new Button("Server starten");
        stopButton = new Button("Server stoppen");
        finishButton = new Button("Beenden");

        buttonPane.getChildren().addAll(startButton, stopButton, finishButton);
        buttonPane.setAlignment(Pos.CENTER);
        return buttonPane;
    }

    /**
     * Aufbau der ComboBox für die Serverauswahl in der GUI
     *
     * @param options Optionen für Implementierungstyp
     * @return ComboBox
     */
    private ComboBox<String> createImplTypeComboBox(ObservableList<String> options) {
        return createComboBox(options);
    }

    /**
     * Aufbau der ComboBox für die AuditLog-Server Verbindung
     *
     * @param options Optionen für Verbindungstyp
     * @return ComboBox
     */
    private ComboBox<String> createAuditLogTypeComboBox(ObservableList<String> options) {
        return createComboBox(options);
    }

    /**
     * Reaktion auf das Betätigen des Start-Buttons
     */
    private void reactOnStartButton() {
        startButton.setOnAction(event -> {
            startable = true;

            // CHat-Server-Port aus GUI lesen
            int serverPortInt = readServerPort();

            // Zähler in der GUI initialisieren
            receivedRequests.setText("0");
            loggedInClients.setText("0");

            // Puffergrößen für Verbindung zu Chat-Clients aus GUI lesen
            int sendBufferSizeInt = readSendBufferSize();
            int receiveBufferSizeInt = readReceiveBufferSize();

            // Hostname für AuditLog-Server
            String auditLogServerHostname;

            String auditLogServerImplType = readAuditLogComboBox();
            if (Objects.equals(auditLogServerImplType, SystemConstants.AUDIT_LOG_SERVER_RMI_IMPL)) {
                // RMI für AuditLog-Server ausgewählt, GUI-Einstellungen für RMI-AuditLog-Server anpassen
                //auditLogServerPort.setText(SystemConstants.DEFAULT_AUDIT_LOG_SERVER_RMI_REGISTRY_PORT);
            }

            if (startable) {
                // Implementierungstyp, der zu starten ist, ermitteln und Chat-Server starten
                String implType = readImplTypeComboBox();

                if (enableAuditLogServerCheckbox.isSelected()) {
                    auditLogServerHostname = readAuditLogServerHostnameOrIp();
                    auditLogServerImplType = readAuditLogComboBox();
                    int auditLogServerPortInt = readAuditLogServerPort(auditLogServerImplType);

                    try {
                        startChatServerWithAuditLogServer(implType, serverPortInt, sendBufferSizeInt,
                                receiveBufferSizeInt, auditLogServerHostname, auditLogServerPortInt,
                                auditLogServerImplType);
                    } catch (Exception e) {
                        setAlert(
                                "Der Server konnte nicht gestartet werden oder die Verbindung zum AuditLogServer" +
                                        "konnte nicht hergestellt werden," +
                                        "eventuell läuft ein anderer Server mit dem Port");
                        return;
                    }
                } else {
                    try {
                        startChatServer(implType, serverPortInt, sendBufferSizeInt, receiveBufferSizeInt);
                    } catch (Exception e) {
                        setAlert(
                                "Der Server konnte nicht gestartet werden," +
                                        "eventuell läuft ein anderer Server mit dem Port");
                        return;
                    }
                }

                startButton.setDisable(true);
                stopButton.setDisable(false);
                finishButton.setDisable(true);

                // Startzeit ermitteln
                cal = Calendar.getInstance();
                startTimeAsString = getCurrentTime(cal);
                showStartData(data);
            } else {
                setAlert("Bitte korrigieren Sie die rot markierten Felder");
            }
        });
    }

    /**
     * Reaktion auf das Betätigen des Stop-Buttons
     */
    private void reactOnStopButton() {
        stopButton.setOnAction(event -> {
            try {
                chatServer.stop();
            } catch (Exception e) {
                LOG.error("Fehler beim Stoppen des Chat-Servers");
                ExceptionHandler.logException(e);
            }

            // Zähler für Clients und Requests auf 0 stellen
            requestCounter.set(0);
            loggedInClientCounter.set(0);

            startButton.setDisable(false);
            stopButton.setDisable(true);
            finishButton.setDisable(false);
            enableAuditLogServerCheckbox.setDisable(false);

            // GUI-Einstellungen wieder auf Standard setzen
            startTimeField.setText("");
            receivedRequests.setText("");
            loggedInClients.setText("");
            auditLogServerPort.setText(SystemConstants.DEFAULT_AUDIT_LOG_SERVER_PORT);
            sendBufferSize.setText(SystemConstants.DEFAULT_SEND_BUFFER_SIZE);
            receiveBufferSize.setText(SystemConstants.DEFAULT_RECEIVE_BUFFER_SIZE);
        });
    }

    /**
     * Reaktion auf das Betätigen des Finish-Buttons
     */
    private void reactOnFinishButton() {
        finishButton.setOnAction(event -> {
            LOG.debug("Schliessen-Button betätigt");
            try {
                ServerFxGUI.chatServer.stop();
            } catch (Exception var3) {
                LOG.debug("Fehler beim Stoppen des Chat-Servers, Chat-Server eventuell noch gar nicht aktiv");
            }
            System.out.println("ChatServer-GUI ordnungsgemäß beendet");
            super.exit();
        });
    }

    private void startChatServerWithAuditLogServer(String implType, int serverPort, int sendBufferSize,
                                                   int receiveBufferSize, String auditLogServerHostname,
                                                   int auditLogServerPort, String auditLogServerImplType)
            throws Exception {
        ChatServerImplementationType serverImpl;
        if (implType.equals(SystemConstants.IMPL_TCP_ADVANCED)) {
            serverImpl = ChatServerImplementationType.TCPAdvancedImplementation;
        } else {
            serverImpl = ChatServerImplementationType.TCPSimpleImplementation;
        }

        AuditLogImplementationType auditLogImplementationType = switch (auditLogServerImplType) {
            case SystemConstants.AUDIT_LOG_SERVER_UDP_IMPL ->
                    AuditLogImplementationType.AuditLogServerUDPImplementation;
            case SystemConstants.AUDIT_LOG_SERVER_RMI_IMPL ->
                    AuditLogImplementationType.AuditLogServerRMIImplementation;
            default -> AuditLogImplementationType.AuditLogServerTCPImplementation;
        };

        try {
            LOG.debug("ChatServer soll mit AuditLog gestartet werden");
            chatServer = ServerFactory.getServerWithAuditLog(serverImpl, serverPort, sendBufferSize, receiveBufferSize,
                    this, auditLogImplementationType, auditLogServerHostname, auditLogServerPort);
        } catch (Exception e) {
            LOG.error("Fehler beim Starten des Chat-Servers: {}", e.getMessage());
            ExceptionHandler.logException(e);
            throw new Exception(e);
        }

        if (!startable) {
            setAlert("Bitte Korrigieren sie die rot markierten Felder");
        } else {
            if (!ServerFactory.isAuditLogServerConnected()) {
                // AuditLog-Server Verbindung nicht vorhanden, in der GUI zeigen
                enableAuditLogServerCheckbox.setSelected(false);
                auditLogServerHostnameOrIp.setEditable(false);
                auditLogServerHostnameOrIp.setStyle("-fx-background-color: gray;");
                comboBoxAuditLogServerType.setEditable(false);
                comboBoxAuditLogServerType.setStyle("-fx-background-color: gray;");
            }

            // Server starten
            chatServer.start();
        }
    }

    /**
     * Chat-Server starten
     *
     * @param implType          Implementierungstyp, der zu starten ist
     * @param serverPort        Serverport, die der Server als Listener-Port nutzen soll
     * @param sendBufferSize    Sendepuffergröße, die der Server nutzen soll
     * @param receiveBufferSize Empfangspuffergröße, die der Server nutzen soll
     */
    private void startChatServer(String implType, int serverPort, int sendBufferSize, int receiveBufferSize)
            throws Exception {
        ChatServerImplementationType serverImpl;
        if (implType.equals(SystemConstants.IMPL_TCP_ADVANCED)) {
            serverImpl = ChatServerImplementationType.TCPAdvancedImplementation;
        } else {
            serverImpl = ChatServerImplementationType.TCPSimpleImplementation;
        }

        try {
            chatServer = ServerFactory.getServer(serverImpl, serverPort, sendBufferSize, receiveBufferSize,
                    this);
        } catch (Exception e) {
            LOG.error("Fehler beim Starten des Chat-Servers: " + e.getMessage());
            ExceptionHandler.logException(e);
            throw new Exception(e);
        }
        if (!startable) {
            setAlert("Bitte Korrigieren sie die rot markierten Felder");
        } else {
            // Server starten
            chatServer.start();
        }
    }

    /**
     * AuditLogServer-Typ aus GUI auslesen
     */
    private String readAuditLogComboBox() {
        String implType;
        if (comboBoxAuditLogServerType.getValue() == null) {
            implType = SystemConstants.AUDIT_LOG_SERVER_TCP_IMPL;
        } else {
            implType = comboBoxAuditLogServerType.getValue();
        }

        return (implType);
    }

    /**
     * Gewählten Implementierungstyp aus GUI auslesen
     */
    private String readImplTypeComboBox() {
        return (comboBoxImplType.getValue());
    }

    /**
     * Lesen des HostNamens oder der Serveradresse aus der GUI als String
     *
     * @return Hostname oder IP-Adresse als String
     */
    private String readAuditLogServerHostnameOrIp() {
        return auditLogServerHostnameOrIp.getText();
    }

    /**
     * Lesen des Ports des AuditLog-Servers aus der GUI
     *
     * @return Port
     */
    private int readAuditLogServerPort(String auditLogServerImplType) {
        String port = auditLogServerPort.getText();
        Tupel<Integer, Boolean> result = ServerStarter.validateAuditLogServerPort(port, auditLogServerImplType);
        startable = result.getY();
        return result.getX();
    }

    /**
     * Serverport aus GUI auslesen und prüfen
     *
     * @return Verwendeter Serverport
     */
    private int readServerPort() {
        String port = serverPort.getText();
        Tupel<Integer, Boolean> result = ServerStarter.validateServerPort(port);
        startable = result.getY();
        return result.getX();
    }

    /**
     * Größe des Sendepuffers in Byte auslesen und prüfen
     *
     * @return Eingegebene Sendepuffer-Größe
     */
    private int readSendBufferSize() {
        String size = sendBufferSize.getText();
        Tupel<Integer, Boolean> result = ServerStarter.validateSendBufferSize(size);
        startable = result.getY();
        return result.getX();
    }

    /**
     * Größe des Empfangspuffers in Byte auslesen und prüfen
     *
     * @return Eingegebene Empfangspuffer-Größe
     */
    private int readReceiveBufferSize() {
        String size = receiveBufferSize.getText();
        Tupel<Integer, Boolean> result = ServerStarter.validateReceiveBufferSize(size);
        startable = result.getY();
        return result.getX();
    }

    private String getCurrentTime(Calendar cal) {
        return new SimpleDateFormat("dd.MM.yy HH:mm:ss:SSS").format(cal.getTime());
    }

    /**
     * GUI-Feld für eingeloggte Clients über Event-Liste des JavaFX-GUI-Threads aktualisieren
     */
    private void updateLoggedInClients() {
        Platform.runLater(() -> {
            LOG.debug("runLater: run-Methode wird ausgeführt");
            LOG.debug("runLater: Logged in Clients: {}", loggedInClientCounter.get());
            loggedInClients.setText(String.valueOf(loggedInClientCounter.get()));
        });
    }

    /**
     * GUI-Feld für Anzahl empfangener Requests über Event-Liste des JavaFX-GUI-Threads aktualisieren
     */
    private void updateNumberOfRequests() {
        Platform.runLater(() -> {
            LOG.debug("runLater: run-Methode wird ausgeführt");
            LOG.debug("runLater: Received Requests: " + requestCounter.get());
            receivedRequests.setText(String.valueOf(requestCounter.get()));
        });
    }

    @Override
    public void showStartData(ServerStartData data) {
        startTimeField.setText(startTimeAsString);
    }

    @Override
    public void increaseNumberOfLoggedInClients() {
        loggedInClientCounter.getAndIncrement();
        LOG.debug("Eingeloggte Clients: " + loggedInClientCounter.get());
        updateLoggedInClients();
    }

    @Override
    public void decreaseNumberOfLoggedInClients() {
        loggedInClientCounter.getAndDecrement();
        LOG.debug("Eingeloggte Clients: " + loggedInClientCounter.get());
        updateLoggedInClients();
    }

    @Override
    public void increaseNumberOfRequests() {
        requestCounter.getAndIncrement();
        LOG.debug(requestCounter.get() + " empfangene Message Requests");
        updateNumberOfRequests();
    }
}