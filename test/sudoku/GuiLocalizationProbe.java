/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.event.InputEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;
import javax.swing.JSeparator;

/** Builds the real Swing menu tree and checks Chinese macOS accelerators. */
public final class GuiLocalizationProbe {

	private GuiLocalizationProbe() {
	}

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.CHINESE);
		SolutionType.resetStepNames();
		SolutionCategory.resetCategoryNames();

		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					verifyMenus(frame);
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("GUI localization check failed", failure[0]);
		}
		System.out.println("Chinese GUI and macOS accelerator checks passed");
		System.exit(0);
	}

	@SuppressWarnings("deprecation")
	private static void verifyMenus(MainFrame frame) {
		JMenuBar menuBar = frame.getJMenuBar();
		require(SudokuUtil.isMacOS(), "GUI probe must run on macOS");
		require(menuBar != null, "menu bar is missing");

		Map<KeyStroke, String> accelerators = new LinkedHashMap<KeyStroke, String>();
		Map<String, JMenuItem> items = new LinkedHashMap<String, JMenuItem>();
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			JMenu menu = menuBar.getMenu(i);
			if (menu != null) {
				collect(menu, menu.getText(), accelerators, items);
			}
		}

		require(items.containsKey("文件"), "File menu was not localized");
		require(items.containsKey("编辑"), "Edit menu was not localized");
		require(items.containsKey("新建随机数独"), "New command was not localized");
		require(items.containsKey("新建空白数独"), "New blank command was not localized");
		require(!items.containsKey("偏好设置..."),
				"macOS Edit menu still contains the duplicate Preferences command");
		JMenu editMenu = findMenu(menuBar, "编辑");
		require(!(editMenu.getMenuComponent(editMenu.getMenuComponentCount() - 1) instanceof JSeparator),
				"removing macOS Preferences left a trailing separator in the Edit menu");
		require(items.containsKey("复制解题步骤"), "Copy step command was not localized");
		require(items.get("播放操作音效") instanceof JCheckBoxMenuItem,
				"operation sounds setting is missing from the Options menu");
		require(!((JCheckBoxMenuItem) items.get("播放操作音效")).isSelected(),
				"operation sounds must default to off");

		assertCommand(items.get("新建随机数独").getAccelerator(), "新建随机数独");
		assertCommandOptionN(items.get("新建空白数独").getAccelerator(), "新建空白数独");
		assertCommand(items.get("撤销").getAccelerator(), "撤销");
		assertKeyWithModifiers(items.get("开始游戏").getAccelerator(),
				java.awt.event.KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK, "开始游戏");
		require(items.get("退出").getAccelerator() == null,
				"macOS File > Exit must not keep the old Option+X accelerator");
		assertKeyWithModifiers(items.get("题目另存为...").getAccelerator(),
				java.awt.event.KeyEvent.VK_S,
				InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
				"题目另存为");
		assertKeyWithModifiers(items.get("仅显示数独").getAccelerator(),
				java.awt.event.KeyEvent.VK_0,
				InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
				"仅显示数独");
		assertKeyWithModifiers(items.get("创建存档点（Savepoint）...").getAccelerator(),
				java.awt.event.KeyEvent.VK_S,
				InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
				"创建存档点");
		assertKeyWithModifiers(items.get("恢复存档点（Savepoint）...").getAccelerator(),
				java.awt.event.KeyEvent.VK_O,
				InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
				"恢复存档点");
		assertCommandOption(items.get("复制解题步骤").getAccelerator(), "复制解题步骤");
		require(items.get("重做").getAccelerator().getKeyCode() == java.awt.event.KeyEvent.VK_Y,
				"redo primary accelerator is not Command+Y");
		KeyStroke redoAlternate = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
				java.awt.event.InputEvent.SHIFT_MASK | SudokuUtil.getMenuShortcutMask());
		require("redoAlternate".equals(frame.getRootPane()
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.get(redoAlternate)), "redo alternate Command+Shift+Z binding is missing");
		require(!redoAlternate.equals(items.get("复制解题步骤").getAccelerator()),
				"redo alternate accelerator collides with Copy Step");
		require(!redoAlternate.equals(items.get("粘贴").getAccelerator()),
				"redo alternate accelerator collides with Paste");
	}

	private static JMenu findMenu(JMenuBar menuBar, String text) {
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			JMenu menu = menuBar.getMenu(i);
			if (menu != null && text.equals(menu.getText())) {
				return menu;
			}
		}
		throw new AssertionError("missing menu " + text);
	}

	@SuppressWarnings("deprecation")
	private static void collect(JMenuItem item, String path, Map<KeyStroke, String> accelerators,
			Map<String, JMenuItem> items) {
		if (item.getText() != null) {
			items.put(item.getText(), item);
		}
		KeyStroke accelerator = item.getAccelerator();
		if (accelerator != null) {
			int modifiers = accelerator.getModifiers();
			require((modifiers & (InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK)) == 0,
					"Ctrl accelerator remains on macOS: " + path + " = " + accelerator);
			String previous = accelerators.put(accelerator, path);
			require(previous == null, "duplicate accelerator " + accelerator + ": " + previous + " and " + path);
		}
		if (item instanceof JMenu) {
			JMenu menu = (JMenu) item;
			for (int i = 0; i < menu.getItemCount(); i++) {
				JMenuItem child = menu.getItem(i);
				if (child != null) {
					collect(child, path + " > " + child.getText(), accelerators, items);
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	private static void assertCommand(KeyStroke stroke, String name) {
		require(stroke != null, name + " accelerator is missing");
		require((stroke.getModifiers() & (InputEvent.META_MASK | InputEvent.META_DOWN_MASK)) != 0,
				name + " does not use Command: " + stroke);
	}

	@SuppressWarnings("deprecation")
	private static void assertOption(KeyStroke stroke, String name) {
		require(stroke != null, name + " accelerator is missing");
		require((stroke.getModifiers() & (InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)) != 0,
				name + " does not use Option: " + stroke);
	}

	@SuppressWarnings("deprecation")
	private static void assertCommandOption(KeyStroke stroke, String name) {
		require(stroke != null && stroke.getKeyCode() == java.awt.event.KeyEvent.VK_C,
				name + " accelerator is not Option+Command+C: " + stroke);
		require((stroke.getModifiers() & (InputEvent.META_MASK | InputEvent.META_DOWN_MASK)) != 0,
				name + " is missing Command: " + stroke);
		require((stroke.getModifiers() & (InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)) != 0,
				name + " is missing Option: " + stroke);
	}

	@SuppressWarnings("deprecation")
	private static void assertCommandOptionN(KeyStroke stroke, String name) {
		require(stroke != null && stroke.getKeyCode() == java.awt.event.KeyEvent.VK_N,
				name + " accelerator is not Option+Command+N: " + stroke);
		require((stroke.getModifiers() & (InputEvent.META_MASK | InputEvent.META_DOWN_MASK)) != 0,
				name + " is missing Command: " + stroke);
		require((stroke.getModifiers() & (InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)) != 0,
				name + " is missing Option: " + stroke);
	}

	private static void assertKeyWithModifiers(KeyStroke stroke, int keyCode,
			int requiredModifiers, String name) {
		require(stroke != null && stroke.getKeyCode() == keyCode,
				name + " uses the wrong key: " + stroke);
		require((stroke.getModifiers() & requiredModifiers) == requiredModifiers,
				name + " is missing required modifiers: " + stroke);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
