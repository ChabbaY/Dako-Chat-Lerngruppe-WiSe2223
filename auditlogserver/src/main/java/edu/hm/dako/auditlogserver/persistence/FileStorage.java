package edu.hm.dako.auditlogserver.persistence;

import edu.hm.dako.auditlogserver.gui.ALServerGUIInterface;
import edu.hm.dako.common.AuditLogPDU;
import edu.hm.dako.common.AuditLogRMIInterface;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * storing audit log data in a text file (e.g. ChatAuditLog.dat)
 *
 * @author Linus Englert
 */
public class FileStorage implements AuditLogRMIInterface, StorageInterface, Serializable {
    /**
     * referencing the logger
     */
    private static final Logger log = LogManager.getLogger(FileStorage.class);

    /**
     * file to save to
     */
    private final String fileName; //parametrisieren, um pro Verbindung mit einem ChatServer eine Datei zu haben?

    private ALServerGUIInterface counter;

    public FileStorage(String fileName, ALServerGUIInterface serverGuiInterface){
        this.fileName = fileName;
        counter = serverGuiInterface;
    }

    @Override
    public void audit(AuditLogPDU pdu) {
        Storage.updateCounter(pdu, counter);

        File file = new File(fileName);
        log.debug("Die file heißt: "+ fileName + "und liegt in: "+ file.toPath().toString());

        //create file if necessary
        try {
            boolean exist = file.createNewFile();
            if (!exist) {
                log.error("Datei " + fileName + " existierte bereits");
               // throw new IllegalArgumentException("Datei existiert bereits");
            } else {
                log.debug("Datei " + fileName + " erfolgreich angelegt");
            }

            // Datei zum Erweitern öffnen
            FileWriter fileWriter = new FileWriter(fileName, StandardCharsets.UTF_8, true);
            BufferedWriter out = new BufferedWriter(fileWriter);

            StringBuilder sb = new StringBuilder();
            Formatter formatter = new Formatter();

            sb.append(formatter.format("%s | %s | %s | %s | %s | %s\n",
                    pdu.getPduType(),
                    pdu.getUserName(),
                    pdu.getClientThreadName(),
                    pdu.getServerThreadName(),
                    pdu.getAuditTime(),
                    pdu.getMessage()));

            out.append(sb);
            formatter.close();
            System.out.println("Audit Log PDU in Datei " + fileName + " geschrieben");
            out.flush();
            out.close();
        } catch (IOException e) {
            log.error("Fehler beim Schreiben von Audit Log PDU in Datei " + fileName);
        }
    }
}