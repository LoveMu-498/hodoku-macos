/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Toolkit;
import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

/** Optional, non-blocking feedback for explicit clipboard and filter actions. */
final class OperationSoundPlayer {

	enum Sound {
		COPY("Glass.aiff"), PASTE("Pop.aiff"), UNAVAILABLE("Purr.aiff"), NO_RESULTS("Tink.aiff");

		private final String fileName;

		Sound(String fileName) {
			this.fileName = fileName;
		}
	}

	private static final String SYSTEM_SOUND_DIRECTORY = "/System/Library/Sounds";
	private static final long MINIMUM_INTERVAL_MILLIS = 100;
	private static long lastPlayMillis;

	private OperationSoundPlayer() {
	}

	static void play(final Sound sound) {
		if (!Options.getInstance().isOperationSoundsEnabled() || !reservePlayback()) {
			return;
		}
		Thread player = new Thread(new Runnable() {
			@Override
			public void run() {
				if (!playSystemSound(sound) && sound == Sound.UNAVAILABLE) {
					Toolkit.getDefaultToolkit().beep();
				}
			}
		}, "hodoku-operation-sound");
		player.setDaemon(true);
		player.start();
	}

	private static synchronized boolean reservePlayback() {
		long now = System.currentTimeMillis();
		if (now - lastPlayMillis < MINIMUM_INTERVAL_MILLIS) {
			return false;
		}
		lastPlayMillis = now;
		return true;
	}

	private static boolean playSystemSound(Sound sound) {
		File file = new File(SYSTEM_SOUND_DIRECTORY, sound.fileName);
		if (!SudokuUtil.isMacOS() || !file.isFile()) {
			return false;
		}
		Clip clip = null;
		try {
			clip = AudioSystem.getClip();
			final Clip activeClip = clip;
			clip.addLineListener(new LineListener() {
				@Override
				public void update(LineEvent event) {
					if (event.getType() == LineEvent.Type.STOP) {
						activeClip.close();
					}
				}
			});
			try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
				clip.open(input);
			}
            if(sound==Sound.NO_RESULTS && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),-10f)));
            }
			clip.start();
			return true;
		} catch (Exception ex) {
			if (clip != null) {
				clip.close();
			}
			return false;
		}
	}
}
