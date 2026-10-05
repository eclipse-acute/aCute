/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.acute;

import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.swt.widgets.Display;

public class RoslynLSConnectionProvider implements StreamConnectionProvider {

	private static boolean installationAlreadySuggested = false;

	private boolean DEBUG = Boolean.parseBoolean(System.getProperty("roslyn.lsp.debug")) || //$NON-NLS-1$
			Boolean.parseBoolean(System.getProperty("omnisharp.lsp.debug")); //$NON-NLS-1$ // for backward compatibility

	private Process process;

	public RoslynLSConnectionProvider() {
	}

	private boolean showDotnetCommandError = true;

	@Override
	public void start() throws IOException {
		// workaround for https://github.com/OmniSharp/omnisharp-node-client/issues/265
		try {
			Process restoreProcess = Runtime.getRuntime().exec(new String[] { AcutePlugin.getDotnetCommand(showDotnetCommandError), "restore" }); //$NON-NLS-1$
			showDotnetCommandError = true;
			try {
				restoreProcess.waitFor();
			} catch (InterruptedException e) {
				AcutePlugin.logError(e);
			}
		} catch (IllegalStateException e) {
			showDotnetCommandError = false;
			ILog.get().log(new Status(IStatus.ERROR,
					AcutePlugin.getDefault().getBundle().getSymbolicName(),
					Messages.roslynLSStreamConnection_dotnetRestoreError));
		}

		String commandLine = System.getenv("ROSLYN_LANGUAGE_SERVER_COMMAND"); //$NON-NLS-1$
		if (commandLine == null) {
			// for backward compatibility
			commandLine = System.getenv("OMNISHARP_LANGUAGE_SERVER_COMMAND"); //$NON-NLS-1$
		}
		if (commandLine == null) {
			commandLine = "roslyn-language-server --stdio --autoLoadProjects"; //$NON-NLS-1$
		}
		try {
			this.process = launch(List.of(commandLine.trim().split("\\s+"))); //$NON-NLS-1$
		} catch (IOException ex) {
			if (!installationAlreadySuggested) {
				Display.getDefault().asyncExec(this::suggestInstallation);
			}
		}
	}

	/**
	 * Launches the server, falling back to the directory
	 * {@code dotnet tool install --global} writes to.
	 * <p>
	 * The IDE inherits the PATH of whatever started it, and a desktop launcher does
	 * not run the shell profile that normally adds that directory, so a perfectly
	 * well installed server looks missing.
	 */
	private static Process launch(List<String> command) throws IOException {
		String toolsDirectory = dotnetToolsDirectory();
		try {
			return start(command, toolsDirectory);
		} catch (IOException notOnPath) {
			// The JVM resolves the program against the PATH the IDE itself was
			// started with, never the one handed to the child, so the global tool
			// has to be named by its full path to be reachable at all.
			Path installed = dotnetTool(toolsDirectory, command.get(0));
			if (installed == null) {
				throw notOnPath;
			}
			List<String> resolved = new ArrayList<>(command);
			resolved.set(0, installed.toString());
			return start(resolved, toolsDirectory);
		}
	}

	private static Process start(List<String> command, String toolsDirectory) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		if (toolsDirectory != null) {
			// The server shells out to dotnet, so it wants the directory too.
			Map<String, String> environment = builder.environment();
			// On Windows this map is case insensitive, so "PATH" also finds "Path".
			String path = environment.get("PATH"); //$NON-NLS-1$
			if (path == null || path.isBlank()) {
				environment.put("PATH", toolsDirectory); //$NON-NLS-1$
			} else if (!List.of(path.split(Pattern.quote(File.pathSeparator))).contains(toolsDirectory)) {
				// Appended, not prepended, so an explicitly installed server wins.
				environment.put("PATH", path + File.pathSeparator + toolsDirectory); //$NON-NLS-1$
			}
		}
		return builder.start();
	}

	private static String dotnetToolsDirectory() {
		// The dotnet CLI puts its per-user files under DOTNET_CLI_HOME when set.
		String home = System.getenv("DOTNET_CLI_HOME"); //$NON-NLS-1$
		if (home == null || home.isBlank()) {
			home = System.getProperty("user.home"); //$NON-NLS-1$
		}
		return home == null || home.isBlank() ? null : Path.of(home, ".dotnet", "tools").toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * @return the globally installed tool of that name, or null when the name is
	 *         already a path, or nothing is installed under it
	 */
	private static Path dotnetTool(String toolsDirectory, String program) {
		if (toolsDirectory == null || program.indexOf('/') >= 0 || program.indexOf(File.separatorChar) >= 0) {
			return null;
		}
		for (String suffix : executableSuffixes()) {
			Path candidate = Path.of(toolsDirectory, program + suffix);
			if (Files.isExecutable(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/** The empty suffix, plus what Windows considers executable. */
	private static List<String> executableSuffixes() {
		List<String> suffixes = new ArrayList<>();
		suffixes.add(""); //$NON-NLS-1$
		String pathExtensions = System.getenv("PATHEXT"); //$NON-NLS-1$
		if (pathExtensions != null) {
			for (String extension : pathExtensions.split(Pattern.quote(File.pathSeparator))) {
				if (!extension.isBlank()) {
					suffixes.add(extension);
				}
			}
		}
		return suffixes;
	}

	// Runs in UI Thread
	private void suggestInstallation() {
		if (installationAlreadySuggested) {
			return;
		}
		installationAlreadySuggested = true;
		MessageDialog.openError(Display.getCurrent().getActiveShell(),
			Messages.roslynLSStreamConnection_roslynLSNotStarted_Title,
			Messages.roslynLSStreamConnection_roslynLSNotFoundError);
	}

	@Override
	public InputStream getInputStream() {
		if (DEBUG) {
			return new FilterInputStream(process.getInputStream()) {
				@Override
				public int read() throws IOException {
					int res = super.read();
					System.err.print((char) res);
					return res;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int bytes = super.read(b, off, len);
					byte[] payload = new byte[bytes];
					System.arraycopy(b, off, payload, 0, bytes);
					System.err.print(new String(payload));
					return bytes;
				}

				@Override
				public int read(byte[] b) throws IOException {
					int bytes = super.read(b);
					byte[] payload = new byte[bytes];
					System.arraycopy(b, 0, payload, 0, bytes);
					System.err.print(new String(payload));
					return bytes;
				}
			};
		} else {
			return process.getInputStream();
		}
	}

	@Override
	public OutputStream getOutputStream() {
		if (DEBUG) {
			return new FilterOutputStream(process.getOutputStream()) {
				@Override
				public void write(int b) throws IOException {
					System.err.print((char) b);
					super.write(b);
				}

				@Override
				public void write(byte[] b) throws IOException {
					System.err.print(new String(b));
					super.write(b);
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					byte[] actual = new byte[len];
					System.arraycopy(b, off, actual, 0, len);
					System.err.print(new String(actual));
					super.write(b, off, len);
				}
			};
		} else {
			return process.getOutputStream();
		}
	}

	@Override
	public void stop() {
		if (process != null) {
			process.destroy();
		}
	}

	@Override public @Nullable InputStream getErrorStream() {
		return process.getErrorStream();
	}

}
