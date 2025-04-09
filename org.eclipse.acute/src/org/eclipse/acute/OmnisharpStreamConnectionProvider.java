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
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.osgi.util.NLS;

public class OmnisharpStreamConnectionProvider implements StreamConnectionProvider {

	private boolean DEBUG = Boolean.parseBoolean(System.getProperty("omnisharp.lsp.debug")); //$NON-NLS-1$

	private Process process;

	public OmnisharpStreamConnectionProvider() {
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
					Messages.omnisharpStreamConnection_dotnetRestoreError));
		}

		String commandLine = System.getenv("OMNISHARP_LANGUAGE_SERVER_COMMAND"); //$NON-NLS-1$
		if (commandLine == null) {
			File serverPath = getServer();
			commandLine = getDefaultCommandLine(serverPath);
		}
		if (commandLine != null) {
			this.process = Runtime.getRuntime().exec(commandLine);
		} else {
			ILog.get().log(new Status(IStatus.ERROR,
					AcutePlugin.getDefault().getBundle().getSymbolicName(),
					Messages.omnisharpStreamConnection_omnisharpNotFoundError));
		}
	}

	/**
	 *
	 * @return path to server, unzipping it if necessary. Can be null is fragment is missing.
	 */
	private @Nullable File getServer() throws IOException {
		File serverPath = new File(AcutePlugin.getDefault().getStateLocation().toFile(), "omnisharp-roslyn"); //$NON-NLS-1$
		if (!serverPath.exists()) {
			serverPath.mkdirs();
			try (
				InputStream stream = FileLocator.openStream(AcutePlugin.getDefault().getBundle(), new Path("omnisharp-roslyn.tar"), true); //$NON-NLS-1$
				TarArchiveInputStream tarStream = new TarArchiveInputStream(stream);
			) {
				TarArchiveEntry entry = null;
				while ((entry = tarStream.getNextEntry()) != null) {
					if (!entry.isDirectory()) {
						File targetFile = new File(serverPath, entry.getName());
						targetFile.getParentFile().mkdirs();
						InputStream in = BoundedInputStream.builder().setMaxCount(entry.getSize()).setInputStream(tarStream).get(); // mustn't be closed
						try (
							FileOutputStream out = new FileOutputStream(targetFile);
						) {
							IOUtils.copy(in, out);
							if (!Platform.OS_WIN32.equals(Platform.getOS())) {
								int xDigit = entry.getMode() % 10;
								targetFile.setExecutable(xDigit > 0, (xDigit & 1) == 1);
								int wDigit = (entry.getMode() / 10) % 10;
								targetFile.setWritable(wDigit > 0, (wDigit & 1) == 1);
								int rDigit = (entry.getMode() / 100) % 10;
								targetFile.setReadable(rDigit > 0, (rDigit & 1) == 1);
							}
						}
					}
				}
			}
		}
		return serverPath;
	}

	/**
	 *
	 * @param commandLine
	 * @return the command-line to run the server, or null is expected resources are not found
	 * @throws IOException
	 */
	protected @Nullable String getDefaultCommandLine(File serverPath) throws IOException {
		File serverFileUrl = null;
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			serverFileUrl = new File(serverPath, "server/Omnisharp.exe"); //$NON-NLS-1$
		} else {
			serverFileUrl = new File(serverPath, "OmniSharp"); //$NON-NLS-1$
		}

		if (!serverFileUrl.exists()) {
			AcutePlugin.logError(NLS.bind(Messages.omnisharpStreamConnection_serverNotFoundError,serverPath));
			return null;
		} else if (!serverFileUrl.canExecute()) {
			AcutePlugin.logError(NLS.bind(Messages.omnisharpStreamConnection_serverNotExecutableError, serverFileUrl));
			// return value anyway
		}
		return serverFileUrl.getAbsolutePath() + " -lsp"; //$NON-NLS-1$
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
