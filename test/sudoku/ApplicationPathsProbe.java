/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Focused checks for durable app-data paths and legacy configuration migration. */
public final class ApplicationPathsProbe {

	private ApplicationPathsProbe() {
	}

	public static void main(String[] args) throws Exception {
		File root = Files.createTempDirectory("hodoku-paths-probe").toFile();
		File dataDirectory = new File(root, "Application Support/HoDoKu");
		File legacyDirectory = new File(root, "legacy-temp");
		require(legacyDirectory.mkdirs(), "legacy directory was not created");

		String oldDataDirectory = System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
		String oldTemporaryDirectory = System.getProperty("java.io.tmpdir");
		try {
			System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, dataDirectory.getPath());
			System.setProperty("java.io.tmpdir", legacyDirectory.getPath());

			File legacy = new File(legacyDirectory, Options.FILE_NAME);
			Files.write(legacy.toPath(), "legacy-options".getBytes(StandardCharsets.UTF_8));

			require(ApplicationPaths.getDataDirectory().equals(dataDirectory), "override directory was ignored");
			require(ApplicationPaths.getConfigurationFile().equals(new File(dataDirectory, Options.FILE_NAME)),
					"configuration path is incorrect");
			require(ApplicationPaths.getSessionFile().equals(new File(dataDirectory, "last-session.xml")),
					"session path is incorrect");

			require(ApplicationPaths.migrateLegacyConfiguration(), "legacy configuration was not migrated");
			File migrated = ApplicationPaths.getConfigurationFile();
			require(migrated.isFile(), "migrated configuration is missing");
			require("legacy-options".equals(new String(Files.readAllBytes(migrated.toPath()), StandardCharsets.UTF_8)),
					"migration changed the configuration");
			require(legacy.isFile(), "migration deleted the legacy configuration");

			Files.write(migrated.toPath(), "current-options".getBytes(StandardCharsets.UTF_8));
			require(!ApplicationPaths.migrateLegacyConfiguration(), "existing current configuration was replaced");
			require("current-options".equals(new String(Files.readAllBytes(migrated.toPath()), StandardCharsets.UTF_8)),
					"existing current configuration changed");

			String appearanceXml = "<java><object><void property=\"appearanceMode\">"
					+ "<string>DARK</string></void></object></java>";
			Files.write(migrated.toPath(), appearanceXml.getBytes(StandardCharsets.UTF_8));
			require(ApplicationPaths.readAppearanceMode() == AppearanceMode.DARK,
					"startup appearance was not read without loading Options");
		} finally {
			restore(ApplicationPaths.DATA_DIRECTORY_PROPERTY, oldDataDirectory);
			restore("java.io.tmpdir", oldTemporaryDirectory);
		}

		System.out.println("Application path checks passed");
	}

	private static void restore(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
