/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.acute.tests;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
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
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;

/**
 * Takes care of creating a temporary project and resource before test and to clean
 * it up after.
 */
public class AbstractAcuteTest {

	private Map<String, IProject> provisionedProjects;

	@Before
	public void setUp() throws Exception {
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
			ProcessBuilder dotnetRestoreBuilder = new ProcessBuilder(AcutePlugin.getDotnetCommand(), "restore");
			dotnetRestoreBuilder.directory(project.getLocation().toFile());
			assertEquals(0, dotnetRestoreBuilder.start().waitFor());
			project.open(new NullProgressMonitor());
			project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			return project;
		} else {
			return null;
		}
	}

	@After
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
}
