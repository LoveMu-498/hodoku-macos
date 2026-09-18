/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Small same-directory atomic replacement helper with one recoverable backup. */
final class AtomicFileIO {

	interface FileAction {
		void run(File file) throws Exception;
	}

	private AtomicFileIO() {
	}

	static void write(File target, FileAction writer, FileAction validator) throws IOException {
		File parent = target.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
			throw new IOException("Unable to create directory: " + parent);
		}

		File temporary = File.createTempFile(target.getName() + ".", ".tmp", parent);
		try {
			writer.run(temporary);
			validator.run(temporary);

			File backup = backupFor(target);
			if (target.isFile()) {
				boolean currentIsValid = false;
				try {
					validator.run(target);
					currentIsValid = true;
				} catch (Exception ex) {
					// Never replace a known-good backup with a corrupt current file.
				}
				if (currentIsValid) {
					Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.COPY_ATTRIBUTES);
				}
			}

			try {
				Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception ex) {
			if (ex instanceof IOException) {
				throw (IOException) ex;
			}
			throw new IOException("Unable to write " + target, ex);
		} finally {
			Files.deleteIfExists(temporary.toPath());
		}
	}

	static File backupFor(File target) {
		return new File(target.getPath() + ".bak");
	}
}
