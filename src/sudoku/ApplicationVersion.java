/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

/** One packaged version shared by the UI and distribution scripts. */
public final class ApplicationVersion {
    private ApplicationVersion() { }

    public static String get() {
        Properties properties = new Properties();
        try (InputStream stream = ApplicationVersion.class.getResourceAsStream("/version.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Missing version.properties");
            }
            properties.load(stream);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read application version", ex);
        }
        String value = properties.getProperty("version", "");
        if (!value.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalStateException("Invalid application version");
        }
        return value;
    }
}
