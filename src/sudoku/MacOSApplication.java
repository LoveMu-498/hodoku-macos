/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small macOS application integration boundary that remains Java 8 compatible. */
public final class MacOSApplication {

	private static final String SCREEN_MENU_BAR_PROPERTY = "apple.laf.useScreenMenuBar";
	private static final String APPLICATION_NAME_PROPERTY = "apple.awt.application.name";
	private static final String APPEARANCE_PROPERTY = "apple.awt.application.appearance";
	private static Object aboutHandlerProxy;
	private static Object preferencesHandlerProxy;
	private static Object quitHandlerProxy;
	private static QuitCoordinator quitCoordinator;
	private static final AtomicBoolean relaunchScheduled = new AtomicBoolean(false);

	private MacOSApplication() {
	}

	/** Receives a native quit request and decides when it may proceed. */
	public interface QuitCallback {
		void quitRequested(QuitRequest request);
	}

	/** One view of the active native quit handshake. */
	public static final class QuitRequest {
		private final QuitSession session;
		private final boolean repeated;

		private QuitRequest(QuitSession session, boolean repeated) {
			this.session = session;
			this.repeated = repeated;
		}

		/** True when another quit request arrived before the first was answered. */
		public boolean isRepeated() {
			return repeated;
		}

		public boolean isCompleted() {
			return session.isCompleted();
		}

		/** Lets macOS terminate the application. */
		public void perform() {
			session.complete(true);
		}

		/** Cancels the native quit request. */
		public void cancel() {
			session.complete(false);
		}
	}

	/** Keeps repeated native quit requests in one exactly-once handshake. */
	static final class QuitCoordinator {
		private final QuitCallback callback;
		private QuitSession pendingSession;

		QuitCoordinator(QuitCallback callback) {
			if (callback == null) {
				throw new IllegalArgumentException("callback must not be null");
			}
			this.callback = callback;
		}

		void requestQuit(Object nativeResponse) {
			if (nativeResponse == null) {
				throw new IllegalArgumentException("nativeResponse must not be null");
			}

			QuitRequest request;
			synchronized (this) {
				boolean repeated = pendingSession != null;
				if (!repeated) {
					pendingSession = new QuitSession(this, nativeResponse);
				}
				request = new QuitRequest(pendingSession, repeated);
			}

			try {
				callback.quitRequested(request);
			} catch (RuntimeException ex) {
				if (!request.isCompleted()) {
					request.cancel();
				}
				throw ex;
			}
		}

		private synchronized void clear(QuitSession session) {
			if (pendingSession == session) {
				pendingSession = null;
			}
		}
	}

	private static final class QuitSession {
		private final QuitCoordinator coordinator;
		private final Object nativeResponse;
		private final AtomicBoolean completed = new AtomicBoolean(false);

		private QuitSession(QuitCoordinator coordinator, Object nativeResponse) {
			this.coordinator = coordinator;
			this.nativeResponse = nativeResponse;
		}

		private boolean isCompleted() {
			return completed.get();
		}

		private void complete(boolean perform) {
			if (!completed.compareAndSet(false, true)) {
				return;
			}

			try {
				invokeQuitResponse(nativeResponse, perform ? "performQuit" : "cancelQuit");
			} finally {
				coordinator.clear(this);
			}
		}
	}

	/**
	 * Sets properties that must be applied before AWT or Swing is initialized.
	 * This method only writes system properties and does not touch AWT.
	 */
	public static void setStartupProperties(String applicationName, AppearanceMode appearanceMode) {
		if (!isMacOS()) {
			return;
		}
		if (applicationName == null || applicationName.trim().isEmpty()) {
			throw new IllegalArgumentException("applicationName must not be blank");
		}
		if (appearanceMode == null) {
			throw new IllegalArgumentException("appearanceMode must not be null");
		}

		if (isMacOS()) {
			System.setProperty(SCREEN_MENU_BAR_PROPERTY, "true");
			System.setProperty(APPLICATION_NAME_PROPERTY, applicationName);
			System.setProperty(APPEARANCE_PROPERTY, appearanceMode.getMacOSPropertyValue());
		}
	}

	/**
	 * Schedules the currently running packaged application to reopen during JVM
	 * shutdown. The shutdown hook is intentionally installed only after the caller
	 * has saved application state successfully.
	 */
	public static boolean scheduleRelaunch() {
		if (!isMacOS()) return false;
		final File applicationBundle = resolveApplicationBundle();
		if (applicationBundle == null || !applicationBundle.isDirectory()) return false;
		if (!relaunchScheduled.compareAndSet(false, true)) return true;
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override public void run() {
					try {
						new ProcessBuilder("/usr/bin/open", "-n", applicationBundle.getAbsolutePath()).start();
					} catch (IOException ex) {
						// The old process is already shutting down; Finder remains the manual fallback.
					}
				}
			}, "hodoku-relaunch"));
			return true;
		} catch (IllegalStateException ex) {
			relaunchScheduled.set(false);
			return false;
		} catch (SecurityException ex) {
			relaunchScheduled.set(false);
			return false;
		}
	}

	static File resolveApplicationBundle() {
		File bundle = findApplicationBundle(System.getProperty("jpackage.app-path"));
		if (bundle != null) return bundle;
		try {
			return findApplicationBundle(MacOSApplication.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI().getPath());
		} catch (Exception ex) {
			return null;
		}
	}

	static File findApplicationBundle(String path) {
		if (path == null || path.trim().isEmpty()) return null;
		for (File current = new File(path).getAbsoluteFile(); current != null; current = current.getParentFile()) {
			if (current.getName().endsWith(".app")) return current;
		}
		return null;
	}

	/**
	 * Installs application-menu callbacks through the Java 9+ Desktop API without
	 * adding a Java 9 compile-time dependency. Calling this method may initialize
	 * AWT; call {@link #setStartupProperties(String, AppearanceMode)} first.
	 *
	 * @return true when at least one handler was installed
	 */
	public static synchronized boolean installHandlers(Runnable aboutAction, Runnable preferencesAction,
			QuitCallback quitCallback) {
		if (!isMacOS()) {
			return false;
		}

		try {
			Class<?> desktopClass = Class.forName("java.awt.Desktop");
			Method isDesktopSupported = desktopClass.getMethod("isDesktopSupported");
			if (!Boolean.TRUE.equals(isDesktopSupported.invoke(null))) {
				return false;
			}
			Object desktop = desktopClass.getMethod("getDesktop").invoke(null);
			boolean installed = false;

			if (aboutAction != null) {
				Class<?> handlerClass = Class.forName("java.awt.desktop.AboutHandler");
				Object proxy = createRunnableProxy(handlerClass, "handleAbout", aboutAction);
				desktopClass.getMethod("setAboutHandler", handlerClass).invoke(desktop, proxy);
				aboutHandlerProxy = proxy;
				installed = true;
			}

			if (preferencesAction != null) {
				Class<?> handlerClass = Class.forName("java.awt.desktop.PreferencesHandler");
				Object proxy = createRunnableProxy(handlerClass, "handlePreferences", preferencesAction);
				desktopClass.getMethod("setPreferencesHandler", handlerClass).invoke(desktop, proxy);
				preferencesHandlerProxy = proxy;
				installed = true;
			}

			if (quitCallback != null) {
				Class<?> handlerClass = Class.forName("java.awt.desktop.QuitHandler");
				QuitCoordinator coordinator = new QuitCoordinator(quitCallback);
				Object proxy = createQuitProxy(handlerClass, coordinator);
				desktopClass.getMethod("setQuitHandler", handlerClass).invoke(desktop, proxy);
				quitCoordinator = coordinator;
				quitHandlerProxy = proxy;
				installed = true;
			}

			return installed;
		} catch (ClassNotFoundException ex) {
			return false;
		} catch (NoSuchMethodException ex) {
			return false;
		} catch (IllegalAccessException ex) {
			return false;
		} catch (InvocationTargetException ex) {
			return false;
		} catch (SecurityException ex) {
			return false;
		} catch (LinkageError ex) {
			return false;
		}
	}

	private static boolean isMacOS() {
		String osName = System.getProperty("os.name", "");
		return osName.toLowerCase(Locale.ROOT).contains("mac");
	}

	private static Object createRunnableProxy(Class<?> handlerClass, final String callbackMethod,
			final Runnable action) {
		return Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[] { handlerClass },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) {
						Object objectResult = handleObjectMethod(proxy, method, args);
						if (objectResult != NOT_AN_OBJECT_METHOD) {
							return objectResult;
						}
						if (callbackMethod.equals(method.getName())) {
							action.run();
						}
						return null;
					}
				});
	}

	static Object createQuitProxy(Class<?> handlerClass, final QuitCoordinator coordinator) {
		return Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[] { handlerClass },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) {
						Object objectResult = handleObjectMethod(proxy, method, args);
						if (objectResult != NOT_AN_OBJECT_METHOD) {
							return objectResult;
						}
						if ("handleQuitRequestWith".equals(method.getName()) && args != null && args.length == 2) {
							coordinator.requestQuit(args[1]);
						}
						return null;
					}
				});
	}

	private static final Object NOT_AN_OBJECT_METHOD = new Object();

	private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
		if (method.getDeclaringClass() != Object.class) {
			return NOT_AN_OBJECT_METHOD;
		}
		if ("toString".equals(method.getName())) {
			return "HoDoKu " + proxy.getClass().getInterfaces()[0].getSimpleName();
		}
		if ("hashCode".equals(method.getName())) {
			return Integer.valueOf(System.identityHashCode(proxy));
		}
		if ("equals".equals(method.getName())) {
			return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
		}
		return null;
	}

	private static void invokeQuitResponse(Object response, String methodName) {
		try {
			Class<?> quitResponseClass = Class.forName("java.awt.desktop.QuitResponse");
			Method method = quitResponseClass.isInstance(response)
					? quitResponseClass.getMethod(methodName)
					: response.getClass().getMethod(methodName);
			method.invoke(response);
		} catch (ClassNotFoundException ex) {
			throw new IllegalStateException("Native quit response API is unavailable", ex);
		} catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Unsupported native quit response", ex);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException("Unable to access native quit response", ex);
		} catch (InvocationTargetException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException("Native quit response failed", cause);
		}
	}
}
