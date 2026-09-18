/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Focused regression checks for the reflection-only macOS application bridge. */
public final class MacOSApplicationProbe {

	private MacOSApplicationProbe() {
	}

	public static void main(String[] args) {
		testStartupProperties();
		testPackagedBundleResolution();
		testQuitHandshake();
		testJdkQuitResponseInterface();
		testNonMacRegistrationIsSafe();
		testJdkQuitProxyDispatch();
		testJdkDesktopRegistration();
		System.out.println("macOS application bridge checks passed");
	}

	private static void testPackagedBundleResolution() {
		java.io.File bundle = MacOSApplication.findApplicationBundle(
				"/Applications/HoDoKu.app/Contents/MacOS/HoDoKu");
		require(bundle != null && "HoDoKu.app".equals(bundle.getName()),
				"packaged launcher path did not resolve to its application bundle");
		require(MacOSApplication.findApplicationBundle("/tmp/Hodoku.jar") == null,
				"non-packaged launcher path was mistaken for an application bundle");
	}

	private static void testStartupProperties() {
		String oldOsName = System.getProperty("os.name");
		String oldMenuBar = System.getProperty("apple.laf.useScreenMenuBar");
		String oldName = System.getProperty("apple.awt.application.name");
		String oldAppearance = System.getProperty("apple.awt.application.appearance");
		try {
			System.setProperty("os.name", "Mac OS X");
			MacOSApplication.setStartupProperties("HoDoKu", AppearanceMode.SYSTEM);
			require("true".equals(System.getProperty("apple.laf.useScreenMenuBar")),
					"system menu bar property was not enabled");
			require("HoDoKu".equals(System.getProperty("apple.awt.application.name")),
					"application name property was not set");
			require("system".equals(System.getProperty("apple.awt.application.appearance")),
					"system appearance mapping is wrong");

			MacOSApplication.setStartupProperties("HoDoKu", AppearanceMode.LIGHT);
			require("NSAppearanceNameAqua".equals(System.getProperty("apple.awt.application.appearance")),
					"light appearance mapping is wrong");

			MacOSApplication.setStartupProperties("HoDoKu", AppearanceMode.DARK);
			require("NSAppearanceNameDarkAqua".equals(System.getProperty("apple.awt.application.appearance")),
					"dark appearance mapping is wrong");

			System.setProperty("os.name", "Linux");
			System.clearProperty("apple.awt.application.name");
			MacOSApplication.setStartupProperties("Ignored", AppearanceMode.LIGHT);
			require(System.getProperty("apple.awt.application.name") == null,
					"startup properties changed on non-macOS");
		} finally {
			restoreProperty("os.name", oldOsName);
			restoreProperty("apple.laf.useScreenMenuBar", oldMenuBar);
			restoreProperty("apple.awt.application.name", oldName);
			restoreProperty("apple.awt.application.appearance", oldAppearance);
		}
	}

	private static void testQuitHandshake() {
		final MacOSApplication.QuitRequest[] firstRequest = new MacOSApplication.QuitRequest[1];
		final MacOSApplication.QuitRequest[] latestRequest = new MacOSApplication.QuitRequest[1];
		final int[] callbackCount = new int[1];
		MacOSApplication.QuitCoordinator coordinator = new MacOSApplication.QuitCoordinator(
				new MacOSApplication.QuitCallback() {
					@Override
					public void quitRequested(MacOSApplication.QuitRequest request) {
						callbackCount[0]++;
						latestRequest[0] = request;
						if (callbackCount[0] == 1) {
							firstRequest[0] = request;
						}
					}
				});

		FakeQuitResponse firstResponse = new FakeQuitResponse();
		FakeQuitResponse secondResponse = new FakeQuitResponse();
		coordinator.requestQuit(firstResponse);
		require(firstRequest[0] != null && !firstRequest[0].isRepeated(),
				"first quit request was marked as repeated");
		coordinator.requestQuit(secondResponse);
		require(latestRequest[0] != null && latestRequest[0].isRepeated(),
				"second pending quit request was not marked as repeated");

		latestRequest[0].perform();
		latestRequest[0].cancel();
		require(firstResponse.performCount == 1 && firstResponse.cancelCount == 0,
				"quit response was not performed exactly once");
		require(secondResponse.performCount == 0 && secondResponse.cancelCount == 0,
				"repeated native response replaced the active quit handshake");

		FakeQuitResponse thirdResponse = new FakeQuitResponse();
		coordinator.requestQuit(thirdResponse);
		require(!latestRequest[0].isRepeated(), "completed quit handshake remained pending");
		latestRequest[0].cancel();
		require(thirdResponse.cancelCount == 1 && thirdResponse.performCount == 0,
				"cancel did not reach the native quit response");
	}

	private static void testNonMacRegistrationIsSafe() {
		String oldOsName = System.getProperty("os.name");
		String oldMenuBar = System.getProperty("apple.laf.useScreenMenuBar");
		String oldName = System.getProperty("apple.awt.application.name");
		String oldAppearance = System.getProperty("apple.awt.application.appearance");
		try {
			System.setProperty("os.name", "Linux");
			System.setProperty("apple.laf.useScreenMenuBar", "unchanged-menu");
			System.setProperty("apple.awt.application.name", "unchanged-name");
			System.setProperty("apple.awt.application.appearance", "unchanged-appearance");
			MacOSApplication.setStartupProperties("HoDoKu", AppearanceMode.DARK);
			require("unchanged-menu".equals(System.getProperty("apple.laf.useScreenMenuBar")),
					"non-macOS startup changed the menu bar property");
			require("unchanged-name".equals(System.getProperty("apple.awt.application.name")),
					"non-macOS startup changed the application name property");
			require("unchanged-appearance".equals(System.getProperty("apple.awt.application.appearance")),
					"non-macOS startup changed the appearance property");
			boolean installed = MacOSApplication.installHandlers(null, null,
					new MacOSApplication.QuitCallback() {
						@Override
						public void quitRequested(MacOSApplication.QuitRequest request) {
							throw new AssertionError("non-macOS bridge invoked a quit callback");
						}
					});
			require(!installed, "macOS handlers were reported installed on Linux");
		} finally {
			restoreProperty("os.name", oldOsName);
			restoreProperty("apple.laf.useScreenMenuBar", oldMenuBar);
			restoreProperty("apple.awt.application.name", oldName);
			restoreProperty("apple.awt.application.appearance", oldAppearance);
		}
	}

	private static void testJdkQuitResponseInterface() {
		try {
			Class<?> responseInterface = Class.forName("java.awt.desktop.QuitResponse");
			final int[] performCount = new int[1];
			Object response = Proxy.newProxyInstance(responseInterface.getClassLoader(),
					new Class<?>[] { responseInterface }, new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							if ("performQuit".equals(method.getName())) {
								performCount[0]++;
							}
							return null;
						}
					});
			MacOSApplication.QuitCoordinator coordinator = new MacOSApplication.QuitCoordinator(
					new MacOSApplication.QuitCallback() {
						@Override
						public void quitRequested(MacOSApplication.QuitRequest request) {
							request.perform();
						}
					});
			coordinator.requestQuit(response);
			require(performCount[0] == 1, "public JDK QuitResponse interface was not invoked");
		} catch (ClassNotFoundException ex) {
			// Expected when this Java-8-compatible code is actually run on a Java 8 runtime.
		}
	}

	private static void testJdkQuitProxyDispatch() {
		try {
			Class<?> handlerClass = Class.forName("java.awt.desktop.QuitHandler");
			Class<?> eventClass = Class.forName("java.awt.desktop.QuitEvent");
			Class<?> responseClass = Class.forName("java.awt.desktop.QuitResponse");
			final MacOSApplication.QuitRequest[] request = new MacOSApplication.QuitRequest[1];
			MacOSApplication.QuitCoordinator coordinator = new MacOSApplication.QuitCoordinator(
					new MacOSApplication.QuitCallback() {
						@Override
						public void quitRequested(MacOSApplication.QuitRequest value) {
							request[0] = value;
						}
					});
			Object handler = MacOSApplication.createQuitProxy(handlerClass, coordinator);
			final int[] cancelCount = new int[1];
			Object response = Proxy.newProxyInstance(responseClass.getClassLoader(),
					new Class<?>[] { responseClass }, new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							if ("cancelQuit".equals(method.getName())) {
								cancelCount[0]++;
							}
							return null;
						}
					});
			Method nativeCallback = handlerClass.getMethod("handleQuitRequestWith", eventClass, responseClass);
			nativeCallback.invoke(handler, new Object[] { null, response });
			require(request[0] != null, "JDK QuitHandler proxy did not dispatch the quit callback");
			request[0].cancel();
			require(cancelCount[0] == 1, "JDK QuitResponse proxy did not receive cancelQuit");
		} catch (ClassNotFoundException ex) {
			// Java 8 runtime: the bridge intentionally remains unavailable.
		} catch (Exception ex) {
			throw new AssertionError("JDK QuitHandler proxy check failed", ex);
		}
	}

	private static void testJdkDesktopRegistration() {
		String osName = System.getProperty("os.name", "").toLowerCase();
		if (!osName.contains("mac")) {
			return;
		}
		try {
			Class.forName("java.awt.desktop.QuitHandler");
		} catch (ClassNotFoundException ex) {
			return;
		}

		boolean installed = MacOSApplication.installHandlers(new Runnable() {
			@Override
			public void run() {
				// Native dispatch is exercised by the real application.
			}
		}, new Runnable() {
			@Override
			public void run() {
				// Native dispatch is exercised by the real application.
			}
		}, new MacOSApplication.QuitCallback() {
			@Override
			public void quitRequested(MacOSApplication.QuitRequest request) {
				request.cancel();
			}
		});
		require(installed, "JDK Desktop application handlers were not installed on macOS");
	}

	private static void restoreProperty(String key, String value) {
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

	public static final class FakeQuitResponse {
		private int performCount;
		private int cancelCount;

		public void performQuit() {
			performCount++;
		}

		public void cancelQuit() {
			cancelCount++;
		}
	}

}
