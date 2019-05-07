package com.airvoy.model.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.*;

public class LoggerFactory {

    private static FileHandler fh;
    private Logger logger;

    public static void startLogger(String logFilePath) {
        try {
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    ("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %3$s: %5$s%6$s%n"));
            fh = new FileHandler(logFilePath, true);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (IOException io) {
            System.out.println("Error starting logger! " + io.getMessage());
            io.printStackTrace();
        }
    }

    public LoggerFactory(String className) {
        Logger newLogger = Logger.getLogger(className);
        try {
            newLogger.addHandler(fh);
            logger = newLogger;
        } catch (Exception e) {
            e.printStackTrace();
            logger = Logger.getAnonymousLogger();
        }
    }

    public void info(String message, Object... args) {
        logger.log(Level.INFO, message, Arrays.asList(args).toArray());
    }

    public void info(String message, Object arg) {
        logger.log(Level.INFO, message, arg);
    }

    public void warn(String message, Object... args) {
        logger.log(Level.WARNING, message, Arrays.asList(args).toArray());
    }

    public void warn(String message, Object arg) {
        logger.log(Level.WARNING, message, arg);
    }

    public void debug(String message, Object... args) {
        logger.log(Level.parse("DEBUG"), message, Arrays.asList(args).toArray());
    }

    public void debug(String message, Object arg) {
        logger.log(Level.parse("DEBUG"), message, arg);
    }

}

