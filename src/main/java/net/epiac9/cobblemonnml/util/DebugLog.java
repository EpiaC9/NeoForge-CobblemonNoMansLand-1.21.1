package net.epiac9.cobblemonnml.util;

import com.mojang.logging.LogUtils;
import net.epiac9.cobblemonnml.Config;
import org.slf4j.Logger;

import java.util.Locale;

public final class DebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DebugLog() {
    }

    public static void log(String message) {
        if (!Config.isDebugLoggingEnabled()) {
            return;
        }
        LOGGER.info(message);
    }

    public static void log(String message, Throwable throwable) {
        if (!Config.isDebugLoggingEnabled()) return;
        LOGGER.info(message, throwable);
    }

    public static void logf(String format, Object... args) {
        if (!Config.isDebugLoggingEnabled()) {
            return;
        }
        LOGGER.info(String.format(Locale.ROOT, format, args));
    }
}
