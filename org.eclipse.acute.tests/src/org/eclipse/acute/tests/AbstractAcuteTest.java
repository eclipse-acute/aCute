/*******************************************************************************
 * Copyright (c) 2017, 2025 Red Hat Inc. and others.
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
package org.eclipse.acute.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.acute.AcutePlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Takes care of creating a temporary project and resource before test and to clean
 * it up after.
 */
public class AbstractAcuteTest {

	private Map<String, IProject> provisionedProjects;

	@BeforeEach
	public void setUp() throws Exception {
		PlatformUI.getWorkbench().getIntroManager().closeIntro(PlatformUI.getWorkbench().getIntroManager().getIntro());
		this.provisionedProjects = new HashMap<>();
	}

	/**
	 *
	 * @param projectName the name that will be used as prefix for the project, and that will be used to find
	 * the content of the project from the plugin "projects" folder
	 * @throws IOException
	 * @throws CoreException
	 * @throws InterruptedException
	 */
	protected IProject provisionProject(String projectName) throws IOException, CoreException, InterruptedException {
		URL url = FileLocator.find(Platform.getBundle("org.eclipse.acute.tests"), Path.fromPortableString("projects/" + projectName), Collections.emptyMap());
		url = FileLocator.toFileURL(url);
		File folder = new File(url.getFile());
		if (folder != null && folder.exists()) {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName + "_" + getClass().getName() + "_" + System.currentTimeMillis());
			project.create(new NullProgressMonitor());
			this.provisionedProjects.put(projectName, project);
			FileUtils.copyDirectory(folder, project.getLocation().toFile());
			// workaround for https://github.com/OmniSharp/omnisharp-node-client/issues/265
			ProcessBuilder dotnetRestoreBuilder = new ProcessBuilder(AcutePlugin.getDotnetCommand(false), "restore");
			dotnetRestoreBuilder.directory(project.getLocation().toFile());
			assertEquals(0, dotnetRestoreBuilder.start().waitFor());
			project.open(new NullProgressMonitor());
			project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			return project;
		} else {
			return null;
		}
	}

	@AfterEach
	public void tearDown() throws CoreException {
		this.provisionedProjects.values().forEach(project -> {
			try {
				project.delete(true, new NullProgressMonitor());
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
	}

	/**
	 * @param projectPrefix the prefix of the project, as it can be found in plugin's "projects" folder
	 * @return a project with the content from the specified projectPrefix
	 * @throws CoreException
	 * @throws IOException
	 */
	protected IProject getProject(String projectPrefix) throws Exception {
		if (!this.provisionedProjects.containsKey(projectPrefix)) {
			provisionProject(projectPrefix);
		}
		return this.provisionedProjects.get(projectPrefix);
	}

	protected static ITextViewer getTextViewer(IEditorPart part) throws InvocationTargetException {
		try {
			if (part instanceof ITextEditor textEditor) {
				Method getSourceViewerMethod = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
				getSourceViewerMethod.setAccessible(true);
				return (ITextViewer) getSourceViewerMethod.invoke(textEditor);
			} else {
				fail("Unable to open editor");
				return null;
			}
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new InvocationTargetException(e);
		}
	}
}
