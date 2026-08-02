/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.google.common.base.Function;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.dialog.LauncherFrame;
import com.skcraft.launcher.dialog.ProcessConsoleFrame;
import com.skcraft.launcher.swing.MessageLog;
import lombok.NonNull;
import lombok.extern.java.Log;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * Handles post-process creation during launch.
 */
@Log
public class LaunchProcessHandler implements Function<Process, ProcessConsoleFrame> {

    private static final int CONSOLE_NUM_LINES = 10000;

    private final Launcher launcher;
    private ProcessConsoleFrame consoleFrame;

    public LaunchProcessHandler(@NonNull Launcher launcher) {
        this.launcher = launcher;
    }

    @Override
    public ProcessConsoleFrame apply(final Process process) {
        log.info("Watching process " + process);

        // El output del juego solo vivia en memoria, en una consola que ahora
        // esta oculta por default -- si el juego crasheaba rapido, no quedaba
        // rastro en ningun lado para diagnosticarlo despues (un jugador comun
        // ni sabe que existe el boton "Consola" en Opciones). Ahora tambien
        // se guarda siempre a un archivo real en disco, se vea o no la consola.
        File logFile = null;
        try {
            File logsDir = new File(launcher.getBaseDir(), "game-logs");
            logsDir.mkdirs();
            File latest = new File(logsDir, "latest.log");
            if (latest.exists()) {
                File previous = new File(logsDir, "previous.log");
                Files.move(latest.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            logFile = latest;
        } catch (IOException e) {
            log.log(Level.WARNING, "No se pudo preparar el archivo de log del juego", e);
        }
        final File finalLogFile = logFile;

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    consoleFrame = new ProcessConsoleFrame(CONSOLE_NUM_LINES, true);
                    consoleFrame.setProcess(process);
                    // No se muestra sola -- el jugador no tiene por que ver una consola
                    // de logs al entrar al juego. Sigue capturando el output igual, por
                    // si hace falta revisarlo despues (boton "Consola" en Opciones).
                    MessageLog messageLog = consoleFrame.getMessageLog();
                    OutputStream fileOut = openLogStream(finalLogFile);
                    teeStream(process.getInputStream(), messageLog.getOutputStream(), fileOut);
                    teeStream(process.getErrorStream(), messageLog.getOutputStream(messageLog.asError()), fileOut);
                }
            });

            // Wait for the process to end
            process.waitFor();
        } catch (InterruptedException e) {
            // Orphan process
        } catch (InvocationTargetException e) {
            log.log(Level.WARNING, "Unexpected failure", e);
        }

        log.info("Process ended, re-showing launcher...");

        // Restore the launcher
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (consoleFrame != null) {
                    consoleFrame.setProcess(null);
                    consoleFrame.requestFocus();
                }
            }
        });

        return consoleFrame;
    }

    private OutputStream openLogStream(File logFile) {
        if (logFile == null) return null;
        try {
            OutputStream out = new FileOutputStream(logFile);
            String header = "=== PokeWorld -- " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===\n";
            out.write(header.getBytes());
            out.flush();
            return out;
        } catch (IOException e) {
            log.log(Level.WARNING, "No se pudo abrir el archivo de log del juego", e);
            return null;
        }
    }

    /**
     * Lee un stream y lo manda tanto a la consola (in-memory, como antes) como
     * a un archivo en disco -- stdout y stderr comparten el mismo archivo asi
     * que la escritura va sincronizada para que no se mezclen a la mitad de
     * una linea entre los dos hilos.
     */
    private void teeStream(final InputStream in, final OutputStream consoleOut, final OutputStream fileOut) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                try {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        consoleOut.write(buffer, 0, len);
                        consoleOut.flush();
                        if (fileOut != null) {
                            synchronized (fileOut) {
                                fileOut.write(buffer, 0, len);
                                fileOut.flush();
                            }
                        }
                    }
                } catch (IOException e) {
                    // Stream closed, process ended
                } finally {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

}
