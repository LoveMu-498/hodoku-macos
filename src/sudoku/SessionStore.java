/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/** Persists and recovers the app-managed last work session. */
public final class SessionStore {

	private final File file;

	public SessionStore(File file) {
		if (file == null) {
			throw new IllegalArgumentException("file must not be null");
		}
		this.file = file;
	}

	public boolean exists() {
		File backup = AtomicFileIO.backupFor(file);
		return file.isFile() && file.length() > 0 || backup.isFile() && backup.length() > 0;
	}

	public void save(final SessionSnapshot snapshot) throws IOException {
		if (snapshot == null || snapshot.getGuiState() == null || snapshot.getGuiState().getSudoku() == null) {
			throw new IOException("Session snapshot is incomplete");
		}
		AtomicFileIO.write(file, new AtomicFileIO.FileAction() {
			@Override
			public void run(File target) throws Exception {
				encode(target, snapshot);
			}
		}, new AtomicFileIO.FileAction() {
			@Override
			public void run(File target) throws Exception {
				decode(target);
			}
		});
	}

	public SessionSnapshot load() throws IOException {
		try {
			return decode(file);
		} catch (IOException ex) {
			File backup = AtomicFileIO.backupFor(file);
			if (backup.isFile()) {
				return decode(backup);
			}
			throw ex;
		}
	}

	public void clear() throws IOException {
		Files.deleteIfExists(file.toPath());
		Files.deleteIfExists(AtomicFileIO.backupFor(file).toPath());
	}

	private static void encode(File target, SessionSnapshot snapshot) throws IOException {
		final IOException[] failure = new IOException[1];
		XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(target)));
		encoder.setExceptionListener(new ExceptionListener() {
			@Override
			public void exceptionThrown(Exception exception) {
				failure[0] = new IOException("Unable to encode session", exception);
			}
		});
		encoder.writeObject(snapshot);
		encoder.close();
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	private static SessionSnapshot decode(File source) throws IOException {
		if (!source.isFile() || source.length() == 0) {
			throw new IOException("Session file is missing or empty: " + source);
		}
		final IOException[] failure = new IOException[1];
		XMLDecoder decoder = null;
		try {
			decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(source)), null,
					new ExceptionListener() {
						@Override
						public void exceptionThrown(Exception exception) {
							failure[0] = new IOException("Unable to decode session", exception);
						}
					});
			Object value = decoder.readObject();
			if (failure[0] != null) {
				throw failure[0];
			}
			if (!(value instanceof SessionSnapshot)) {
				throw new IOException("Unsupported session data in " + source);
			}
			SessionSnapshot snapshot = (SessionSnapshot) value;
			if (snapshot.getFormatVersion() != 1 || snapshot.getGuiState() == null
					|| snapshot.getGuiState().getSudoku() == null) {
				throw new IOException("Invalid session data in " + source);
			}
			if (snapshot.getActiveRow() < 0 || snapshot.getActiveRow() >= Sudoku2.UNITS
					|| snapshot.getActiveCol() < 0 || snapshot.getActiveCol() >= Sudoku2.UNITS) {
				throw new IOException("Invalid active cell in " + source);
			}
			return snapshot;
		} catch (RuntimeException ex) {
			throw new IOException("Unable to read session " + source, ex);
		} finally {
			if (decoder != null) {
				decoder.close();
			}
		}
	}
}
