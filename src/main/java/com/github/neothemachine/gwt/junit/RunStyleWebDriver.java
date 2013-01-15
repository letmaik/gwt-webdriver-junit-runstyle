package com.github.neothemachine.gwt.junit;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.junit.JUnitShell;
import com.google.gwt.junit.RunStyle;
import com.google.gwt.junit.RunStyleSelenium;
import com.thoughtworks.selenium.Selenium;

/**
 * This class was adapted from com.google.gwt.junit.RunStyleSelenium.
 * It was done in 1 hour and although it works for me using 
 * ghostdriver (PhantomJS), it might not work for other cases.
 *
 */
public class RunStyleWebDriver extends RunStyle {

	/**
	 * The maximum amount of time that a selenia can take to start in
	 * milliseconds. 10 minutes.
	 */
	private static final int LAUNCH_TIMEOUT = 10 * 60 * 1000;


	static class WebDriverWrapper {

		private static final Pattern PATTERN = Pattern
				.compile("([\\w\\.-]+):([\\d]+)/(.+)");

		/*
		 * Visible for testing.
		 */
		String browser;
		URL url;

		private WebDriver webDriver;
		private final String specifier;

		public WebDriverWrapper(String specifier) throws MalformedURLException {
			this.specifier = specifier;
			parseSpecifier();
			
			DesiredCapabilities caps = new DesiredCapabilities();
			caps.setBrowserName(browser);
			this.webDriver = new RemoteWebDriver(this.url, caps);
		}

		public WebDriver getWebDriver() {
			return webDriver;
		}

		public String getSpecifier() {
			return specifier;
		}

		private void parseSpecifier() throws MalformedURLException {
			Matcher matcher = PATTERN.matcher(specifier);
			if (!matcher.matches()) {
				throw new IllegalArgumentException(
						"Unable to parse WebDriver target "
								+ specifier
								+ " (expected format is [host]:[port]/[browser])");
			}
			this.browser = matcher.group(3);
			String host = matcher.group(1);
			int port = Integer.parseInt(matcher.group(2));
			this.url = new URL("http://" + host + ":" + port);
		}
	}

	/**
	 * A {@link Thread} used to interact with {@link WebDriver} instances.
	 * Selenium does not support execution of multiple methods at the same time,
	 * so its important to make sure that {@link WebDriverThread#isComplete()}
	 * returns true before calling more methods in {@link WebDriver}.
	 */
	class WebDriverThread extends Thread {

		/**
		 * {@link RunStyleSelenium#lock} is sometimes active when calling
		 * {@link #isComplete()}, so we need a separate lock to avoid deadlock.
		 */
		Object accessLock = new Object();

		/**
		 * The exception thrown while running this thread, if any.
		 */
		private Throwable exception;

		/**
		 * True if the webdriver has successfully completed the action. Protected
		 * by {@link #accessLock}.
		 */
		private boolean isComplete;

		private final WebDriverWrapper remote;

		/**
		 * Construct a new {@link WebDriverThread}.
		 * 
		 * @param remote
		 *            the {@link WebDriverWrapper} instance
		 */
		public WebDriverThread(WebDriverWrapper remote) {
			this.remote = remote;
			setDaemon(true);
		}

		/**
		 * Get the {@link Throwable} caused by the action.
		 * 
		 * @return the exception if one occurred, null if none occurred
		 */
		public Throwable getException() {
			synchronized (accessLock) {
				return exception;
			}
		}

		public WebDriverWrapper getRemote() {
			return remote;
		}

		public boolean isComplete() {
			synchronized (accessLock) {
				return isComplete;
			}
		}

		protected void markComplete() {
			synchronized (accessLock) {
				isComplete = true;
			}
		}

		protected void setException(Throwable e) {
			synchronized (accessLock) {
				this.exception = e;
				isComplete = true;
			}
		}
	}

	/**
	 * TODO don't know if this is needed for WebDriver
	 * 
	 * <p>
	 * The {@link Thread} used to launch a module on a single Selenium target.
	 * We launch {@link WebDriver} instances in a separate thread because
	 * {@link Selenium#start()} can hang if the browser cannot be opened
	 * successfully. Instead of blocking the test indefinitely, we use a
	 * separate thread and timeout if needed.
	 * </p>
	 * <p>
	 * We wait until {@link LaunchThread#isComplete()} returns <code>true</code>
	 * before starting the keep alive thread or creating a {@link StopThread},
	 * so no other thread can be accessing {@link Selenium} at the same time.
	 * </p>
	 */
	class LaunchThread extends WebDriverThread {

		private final String moduleName;

		/**
		 * Construct a new {@link LaunchThread}.
		 * 
		 * @param remote
		 *            the remote {@link WebDriverWrapper} instance
		 * @param moduleName
		 *            the module to load
		 */
		public LaunchThread(WebDriverWrapper remote, String moduleName) {
			super(remote);
			this.moduleName = moduleName;
		}

		@Override
		public void run() {
			WebDriverWrapper remote = getRemote();
			try {
				String domain = "http://" + getLocalHostName() + ":"
						+ shell.getPort() + "/";
				String url = shell.getModuleUrl(moduleName);

				// Create the selenium instance and open the browser.
				if (shell.getTopLogger().isLoggable(TreeLogger.TRACE)) {
					shell.getTopLogger().log(
							TreeLogger.TRACE,
							"Starting with domain: " + domain
									+ " Opening URL: " + url);
				}
				remote.getWebDriver().get(url);

				markComplete();
			} catch (Throwable e) {
				shell.getTopLogger().log(
						TreeLogger.ERROR,
						"Error launching browser via WebDriver API at "
								+ remote.getSpecifier(), e);
				setException(e);
			}
		}
	}

	/**
	 * <p>
	 * The {@link Thread} used to stop a selenium instance.
	 * </p>
	 * <p>
	 * We stop the keep alive thread before creating {@link StopThread}s, and we
	 * do not create {@link StopThread}s if a {@link LaunchThread} is still
	 * running for a {@link Selenium} instance, so no other thread can possible
	 * be accessing {@link Selenium} at the same time.
	 * </p>
	 */
	class StopThread extends WebDriverThread {

		public StopThread(WebDriverWrapper remote) {
			super(remote);
		}

		@Override
		public void run() {
			WebDriverWrapper remote = getRemote();
			try {
				remote.getWebDriver().quit();
				markComplete();
			} catch (Throwable e) {
				shell.getTopLogger().log(
						TreeLogger.WARN,
						"Error stopping WebDriver session at "
								+ remote.getSpecifier(), e);
				setException(e);
			}
		}
	}

	/**
	 * The list of hosts that were interrupted. Protected by {@link #lock}.
	 */
	private Set<String> interruptedHosts;

	/**
	 * We keep a list of {@link LaunchThread} instances so that we know which
	 * selenia successfully started. Only selenia that have been successfully
	 * started should be stopped when the test is finished. Protected by
	 * {@link #lock};
	 */
	private List<LaunchThread> launchThreads = new ArrayList<LaunchThread>();

	/**
	 * Indicates that testing has stopped, and we no longer need to run keep
	 * alive checks. Protected by {@link #lock}.
	 */
	private boolean stopped;

	private WebDriverWrapper remotes[];

	/**
	 * A separate lock to control access to {@link Selenium}, {@link #stopped},
	 * {@link #remotes}, and {@link #interruptedHosts}. This ensures that the
	 * keepAlive thread doesn't call getTitle after the shutdown thread calls
	 * {@link Selenium#stop()}.
	 */
	private final Object lock = new Object();

	public RunStyleWebDriver(final JUnitShell shell) {
		super(shell);
	}

	@Override
	public String[] getInterruptedHosts() {
		synchronized (lock) {
			if (interruptedHosts == null) {
				return null;
			}
			return interruptedHosts
					.toArray(new String[interruptedHosts.size()]);
		}
	}

	@Override
	public int initialize(String args) {
		if (args == null || args.length() == 0) {
			getLogger()
					.log(TreeLogger.ERROR,
							"WebDriver runstyle requires comma-separated WebDriver server targets");
			return -1;
		}
		String[] targetsIn = args.split(",");
		WebDriverWrapper targets[] = new WebDriverWrapper[targetsIn.length];

		for (int i = 0; i < targets.length; ++i) {
			try {
				targets[i] = createWebDriverWrapper(targetsIn[i]);
			} catch (IllegalArgumentException e) {
				getLogger().log(TreeLogger.ERROR, e.getMessage());
				return -1;
			}
		}

		// We don't need a lock at this point because we haven't started the
		// keep-
		// alive thread.
		this.remotes = targets;

		// Install a shutdown hook that will close all of our outstanding
		// Selenium
		// sessions. The hook is only executed if the JVM is exited normally. If
		// the
		// process is terminated, the shutdown hook will not run, which leaves
		// browser instances open on the Selenium server. We'll need to modify
		// Selenium Server to do its own cleanup after a timeout.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				List<StopThread> stopThreads = new ArrayList<StopThread>();
				synchronized (lock) {
					stopped = true;
					for (LaunchThread launchThread : launchThreads) {
						// Closing selenium instances that have not successfully
						// started
						// results in an error on the selenium client. By doing
						// this check,
						// we are ensuring that no other calls to the remote
						// instance are
						// being done by another thread.
						if (launchThread.isComplete()) {
							StopThread stopThread = new StopThread(launchThread
									.getRemote());
							stopThreads.add(stopThread);
							stopThread.start();
						}
					}
				}

				// Wait for all threads to stop.
				try {
					waitForThreadsToComplete(stopThreads, false, "stop", 500);
				} catch (UnableToCompleteException e) {
					// This should never happen.
				}
			}
		});
		return targets.length;
	}

	@Override
	public void launchModule(String moduleName)
			throws UnableToCompleteException {
		// Startup all the selenia and point them at the module url.
		for (WebDriverWrapper remote : remotes) {
			LaunchThread thread = new LaunchThread(remote, moduleName);
			synchronized (lock) {
				launchThreads.add(thread);
			}
			thread.start();
		}

		// Wait for all selenium targets to start.
		waitForThreadsToComplete(launchThreads, true, "start", 1000);

		// Check if any threads have thrown an exception. We wait until all
		// threads
		// have had a change to start so that we don't shutdown while some
		// threads
		// are still starting.
		synchronized (lock) {
			for (LaunchThread thread : launchThreads) {
				if (thread.getException() != null) {
					// The thread has already logged the exception.
					throw new UnableToCompleteException();
				}
			}
		}

		// Start the keep alive thread.
		start();
	}

	/**
	 * Factory method for {@link WebDriverWrapper}.
	 * 
	 * @param webDriverSpecifier
	 *            Specifies the Selenium instance to create
	 * @return an instance of {@link WebDriverWrapper}
	 * @throws MalformedURLException
	 */
	protected WebDriverWrapper createWebDriverWrapper(String webDriverSpecifier) {
		try {
			return new WebDriverWrapper(webDriverSpecifier);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Create the keep-alive thread.
	 */
	protected void start() {
		// This will periodically check for failure of the Selenium session and
		// stop
		// the test if something goes wrong.
		Thread keepAliveThread = new Thread() {
			@Override
			public void run() {
				do {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
						break;
					}
				} while (doKeepAlives());
			}
		};
		keepAliveThread.setDaemon(true);
		keepAliveThread.start();
	}

	private boolean doKeepAlives() {
		synchronized (lock) {
			if (remotes != null) {
				// If the shutdown thread has already executed, then we can stop
				// this
				// thread.
				if (stopped) {
					return false;
				}

				for (WebDriverWrapper remote : remotes) {
					// Use getTitle() as a cheap way to see if the Selenium
					// server's still
					// responding (Selenium seems to provide no way to check the
					// server
					// status directly).
					try {
						if (remote.getWebDriver() != null) {
							remote.getWebDriver().getTitle();
						}
					} catch (Throwable e) {
						// If we ask for the title of the page while a new
						// module is
						// loading, IE will throw a permission denied exception.
						String message = e.getMessage();
						if (message == null
								|| !message.toLowerCase().contains(
										"permission denied")) {
							if (interruptedHosts == null) {
								interruptedHosts = new HashSet<String>();
							}
							interruptedHosts.add(remote.getSpecifier());
						}
					}
				}
			}
			return interruptedHosts == null;
		}
	}

	/**
	 * Get the display list of specifiers for threads that did not complete.
	 * 
	 * @param threads
	 *            the list of threads
	 * @return a list of specifiers
	 */
	private <T extends WebDriverThread> String getIncompleteSpecifierList(
			List<T> threads) {
		String list = "";
		for (WebDriverThread thread : threads) {
			if (!thread.isComplete()) {
				list += "  " + thread.getRemote().getSpecifier() + "\n";
			}
		}
		return list;
	}

	/**
	 * Iterate over a list of {@link WebDriverThread}s, waiting for them to
	 * finish.
	 * 
	 * @param <T>
	 *            the thread type
	 * @param threads
	 *            the list of threads
	 * @param fatalExceptions
	 *            true to treat all exceptions as errors, false to treat
	 *            exceptions as warnings
	 * @param action
	 *            the action being performed by the thread
	 * @param sleepTime
	 *            the amount of time to sleep in milliseconds
	 * @throws UnableToCompleteException
	 *             if the thread times out and fatalExceptions is true
	 */
	private <T extends WebDriverThread> void waitForThreadsToComplete(
			List<T> threads, boolean fatalExceptions, String action,
			int sleepTime) throws UnableToCompleteException {
		boolean allComplete;
		long endTime = System.currentTimeMillis() + LAUNCH_TIMEOUT;
		do {
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// This should not happen.
				throw new UnableToCompleteException();
			}

			allComplete = true;
			synchronized (lock) {
				for (WebDriverThread thread : threads) {
					if (!thread.isComplete()) {
						allComplete = false;
					}
				}
			}

			// Check if we have timed out.
			if (!allComplete && endTime < System.currentTimeMillis()) {
				allComplete = true;
				String message = "The following Selenium instances did not "
						+ action + " within " + LAUNCH_TIMEOUT + "ms:\n";
				synchronized (lock) {
					message += getIncompleteSpecifierList(threads);
				}
				if (fatalExceptions) {
					shell.getTopLogger().log(TreeLogger.ERROR, message);
					throw new UnableToCompleteException();
				} else {
					shell.getTopLogger().log(TreeLogger.WARN, message);
				}
			}
		} while (!allComplete);
	}
}
