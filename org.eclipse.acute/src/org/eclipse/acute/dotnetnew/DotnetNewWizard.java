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
 *  Lucas Bullen   (Red Hat Inc.) - Logic implementation
 *******************************************************************************/
package org.eclipse.acute.dotnetnew;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.acute.AcutePlugin;
import org.eclipse.acute.Messages;
import org.eclipse.acute.builder.IncrementalDotnetBuilder;
import org.eclipse.acute.dotnetnew.DotnetNewAccessor.Template;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

public class DotnetNewWizard extends Wizard implements INewWizard {
	private DotnetNewWizardPage wizardPage;

	public DotnetNewWizard() {
		super();
		setNeedsProgressMonitor(true);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		wizardPage = new DotnetNewWizardPage();
		setWindowTitle(Messages.DotnetNewWizard_newProject);

		Iterator<Object> selectionIterator = selection.iterator();
		Set<IWorkingSet> workingSets = new HashSet<>();
		IResource selectedResource = null;

		while (selectionIterator.hasNext()) {
			Object element = selectionIterator.next();
			IResource asResource = toResource(element);

			if (asResource != null && selectedResource == null) {
				selectedResource = asResource;
			} else {
				IWorkingSet asWorkingSet = Adapters.adapt(element, IWorkingSet.class);
				if (asWorkingSet != null) {
					workingSets.add(asWorkingSet);
				}
			}
		}

		if (workingSets.isEmpty() && selectedResource != null) {
			workingSets.addAll(getWorkingSets(selectedResource));
		}
		wizardPage.setWorkingSets(workingSets);

		if (selectedResource != null) {
			wizardPage.setDirectory(toFile(selectedResource));
		} else {
			wizardPage.setDirectory(newFolderLocation());
		}
	}

	@Override
	public void addPages() {
		addPage(wizardPage);
	}

	@Override
	public boolean performFinish() {
		Template template = wizardPage.getTemplate();
		File location = wizardPage.getDirectory();
		String projectName = wizardPage.getProjectName();

		if (!location.exists()) {
			location.mkdirs();
		}

		try {
			getContainer().run(true, true, monitor -> {
				monitor.beginTask(Messages.DotnetNewWizard_createProject, 0);
				List<String> commandLine = new ArrayList<>();
				commandLine.add(AcutePlugin.getDotnetCommand());
				commandLine.add("new"); //$NON-NLS-1$
				if (template != null) {
					commandLine.addAll(template.getCLIOptions());
				}
				ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
				processBuilder.directory(location);

				try {
					Process process = processBuilder.start();
					boolean isProcessDone = false;
					while (!isProcessDone) {
						if (monitor.isCanceled()) {
							process.destroyForcibly();
						}
						isProcessDone = process.waitFor(100, TimeUnit.MILLISECONDS);
					}

					if (process.exitValue() == 0) {
						createProject(projectName, location, monitor);
					} else {
						Display.getDefault().asyncExec(() -> {
							MessageDialog.openError(getShell(), Messages.DotnetNewWizard_createTemplateError_title,
									NLS.bind(Messages.DotnetNewWizard_createTemplateErrorExitValue_message, process.exitValue()));
						});
					}
					monitor.done();
				} catch (IOException e) {
					monitor.done();
					Display.getDefault().asyncExec(() -> {
						MessageDialog.openError(getShell(), Messages.DotnetNewWizard_createTemplateError_title,
								NLS.bind(Messages.DotnetNewWizard_createTemplateError_message, e));
					});
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			MessageDialog.openError(getShell(), Messages.DotnetNewWizard_createTemplateError_title,
					NLS.bind(Messages.DotnetNewWizard_createTemplateError_message, e));
		} catch (IllegalStateException e) {
			// handled by getDotnetCommand()
		}
		return true;
	}

	private void createProject(String name, File directory, IProgressMonitor monitor) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(name);

		try {
			final IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IProjectDescription projectDescription = workspace.newProjectDescription(project.getName());

			String projectLocation = directory.getAbsolutePath();
			IPath projectPath = new Path(projectLocation);

			projectDescription.setLocation(projectPath);

			ICommand[] commands = projectDescription.getBuildSpec();
			ICommand command = projectDescription.newCommand();
			command.setBuilderName(IncrementalDotnetBuilder.BUILDER_ID);
			ICommand[] nc = new ICommand[commands.length + 1];
			System.arraycopy(commands, 0, nc, 1, commands.length);
			nc[0] = command;
			projectDescription.setBuildSpec(nc);

			project.create(projectDescription, monitor);
			project.open(monitor);

		} catch (CoreException e) {
			MessageDialog.openError(getShell(), Messages.DotnetNewWizard_projectDescriptionLoadingError, e.toString());
		}

		IWorkingSetManager wsm = PlatformUI.getWorkbench().getWorkingSetManager();
		IFile csPrgramFile = project.getFile("Program.cs"); //$NON-NLS-1$
		IFile fsPrgramFile = project.getFile("Program.fs"); //$NON-NLS-1$

		Display.getDefault().asyncExec(() -> {

			wsm.addToWorkingSets(project, wizardPage.getWorkingSets());

			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				try {
					if (csPrgramFile.exists()) {
						IDE.openEditor(page, csPrgramFile);
					} else if (fsPrgramFile.exists()) {
						IDE.openEditor(page, fsPrgramFile);
					}
				} catch (CoreException e) {
					MessageDialog.openError(getShell(), Messages.DotnetNewWizard_openProjectError, e.toString());
				}
			}
		});
	}

	private File newFolderLocation() {
		IPath workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		int appendedNumber = 0;
		File newFile = workspacePath.append("NewDotnetProject").toFile(); //$NON-NLS-1$
		while (newFile.isDirectory()) {
			appendedNumber++;
			newFile = workspacePath.append("NewDotnetProject" + appendedNumber).toFile(); //$NON-NLS-1$
		}
		return newFile;
	}

	private Set<IWorkingSet> getWorkingSets(IResource resource) {
		IWorkingSet[] allWorkingSets = PlatformUI.getWorkbench().getWorkingSetManager().getAllWorkingSets();
		Set<IWorkingSet> fileWorkingSets = new HashSet<>();

		for (IWorkingSet iWorkingSet : allWorkingSets) {
			IAdaptable[] elements = iWorkingSet.getElements();
			if (Arrays.asList(elements).contains(resource.getProject())) {
				fileWorkingSets.add(iWorkingSet);
			}
		}

		return fileWorkingSets;
	}

	private IResource toResource(Object o) {
		if (o instanceof IResource) {
			return (IResource) o;
		} else if(o instanceof IAdaptable) {
			return ((IAdaptable) o).getAdapter(IResource.class);
		}else {
			return null;
		}
	}

	private File toFile(IResource r) {
		IPath location = r.getLocation();
		if (location.toFile().isFile()) {
			return location.toFile().getParentFile().getAbsoluteFile();
		}
		return location == null ? null : location.toFile();
	}

}
