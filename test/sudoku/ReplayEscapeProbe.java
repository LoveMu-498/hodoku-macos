package sudoku;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;

/** A held modifier must not trap the reader in the read-only replay view. */
public final class ReplayEscapeProbe {
	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = new MainFrame(null);
			try {
				ReplayController controller = frame.getReplayController();
				ReplayBoard before = new ReplayBoard(frame.getSudokuPanel().getSudoku());
				controller.openViewer(new ReplaySession(before, System.currentTimeMillis()));
				ReplayViewer viewer = controller.viewer();
				if (viewer == null) throw new AssertionError("replay did not open");
				KeyEvent escape = new KeyEvent(viewer, KeyEvent.KEY_PRESSED,
						System.currentTimeMillis(), InputEvent.SHIFT_DOWN_MASK,
						KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
				if (!viewer.handleKeyEvent(escape) || !escape.isConsumed()
						|| controller.viewer() != null
						|| !before.equals(new ReplayBoard(frame.getSudokuPanel().getSudoku()))) {
					throw new AssertionError("modified Escape did not close replay safely");
				}
			} finally { frame.dispose(); }
		});
		System.out.println("Modified Escape closed read-only replay without board mutation");
		System.exit(0);
	}
}
