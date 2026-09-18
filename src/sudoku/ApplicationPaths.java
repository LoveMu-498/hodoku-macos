/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the small set of files managed by the desktop application. */
public final class ApplicationPaths {

	public static final String DATA_DIRECTORY_PROPERTY = "hodoku.data.dir";
	private static final String APPLICATION_DIRECTORY_NAME = "HoDoKu";
	private static final String SESSION_FILE_NAME = "last-session.xml";
	private static final Pattern APPEARANCE_PATTERN = Pattern.compile(
			"property=\"appearanceMode\"[^>]*>\\s*<string>(SYSTEM|LIGHT|DARK)</string>",
			Pattern.CASE_INSENSITIVE);

	private ApplicationPaths() {
	}

	public static File getDataDirectory() {
		String override = System.getProperty(DATA_DIRECTORY_PROPERTY);
		if (override != null && !override.trim().isEmpty()) {
			return new File(override);
		}

		String osName = System.getProperty("os.name", "").toLowerCase();
		if (osName.startsWith("mac")) {
			return new File(new File(System.getProperty("user.home"), "Library/Application Support"),
					APPLICATION_DIRECTORY_NAME);
		}
		return new File(System.getProperty("java.io.tmpdir"));
	}

	public static File getConfigurationFile() {
		return new File(getDataDirectory(), Options.FILE_NAME);
	}

	public static File getSessionFile() {
		return new File(getDataDirectory(), SESSION_FILE_NAME);
	}

	public static File getLegacyConfigurationFile() {
		return new File(System.getProperty("java.io.tmpdir"), Options.FILE_NAME);
	}

	/**
	 * Copies the old temporary-directory configuration once. The source is deliberately
	 * retained as a fallback for older builds.
	 */
	public static boolean migrateLegacyConfiguration() throws IOException {
		File target = getConfigurationFile().getAbsoluteFile();
		File legacy = getLegacyConfigurationFile().getAbsoluteFile();
		if (target.isFile() || !legacy.isFile() || target.equals(legacy)) {
			return false;
		}

		File parent = target.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
			throw new IOException("Unable to create application data directory: " + parent);
		}
		final File source = legacy;
		AtomicFileIO.write(target, new AtomicFileIO.FileAction() {
			@Override
			public void run(File temporary) throws Exception {
				Files.copy(source.toPath(), temporary.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		}, new AtomicFileIO.FileAction() {
			@Override
			public void run(File temporary) {
				// A byte-for-byte migration is intentionally format agnostic; Options validates it later.
			}
		});
		return true;
	}

	/** Reads only the startup-critical appearance value without initializing AWT. */
	public static AppearanceMode readAppearanceMode() {
		File[] candidates = { getConfigurationFile(), AtomicFileIO.backupFor(getConfigurationFile()),
				getLegacyConfigurationFile() };
		for (File candidate : candidates) {
			if (!candidate.isFile() || candidate.length() == 0) {
				continue;
			}
			try {
				String xml = new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
				Matcher matcher = APPEARANCE_PATTERN.matcher(xml);
				if (matcher.find()) {
					return AppearanceMode.fromName(matcher.group(1));
				}
			} catch (IOException ex) {
				// Fall through to the next recoverable source and finally the system default.
			}
		}
		return AppearanceMode.SYSTEM;
	}
}
