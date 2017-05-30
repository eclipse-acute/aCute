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
package org.eclipse.acute.dotnetnew;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.acute.AcutePlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.dialogs.WorkingSetGroup;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.registry.WorkingSetDescriptor;
import org.eclipse.ui.internal.registry.WorkingSetRegistry;

public class DotnetNewWizardPage extends WizardPage implements IWizardPage {

	private Set<IWorkingSet> workingSets;
	private Map<String, String> templatesMap;
	private File directory;
	private String projectName;
	private Boolean isDirectoryAndProjectLinked = true;

	private Text locationText;
	private Text projectNameText;
	private ListViewer templateViewer;
	private WorkingSetGroup workingSetsGroup;
	private Image linkImage;
	private Button linkButton;
	private ControlDecoration locationControlDecoration;
	private ControlDecoration projectNameControlDecoration;

	protected DotnetNewWizardPage() {
		super(DotnetNewWizardPage.class.getName());
		setTitle("Create new Dotnet project");
		setDescription("Create a new Dotnet project, using the `dotnet new` command");
	}

	public File getLocation() {
		if (isDirectoryAndProjectLinked) {
			return directory;
		} else {
			return new File(directory.toString() + "/" + projectName);
		}
	}

	public String getProjectName() {
		return projectName;
	}

	public String getTemplate() {
		IStructuredSelection selection = (IStructuredSelection) templateViewer.getSelection();
		if (selection.isEmpty()) {
			return "";
		}
		return templatesMap.get(selection.getFirstElement());
	}

	public IWorkingSet[] getWorkingSets() {
		return workingSetsGroup.getSelectedWorkingSets();
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		container.setLayout(new GridLayout(4, false));

		Label locationLabel = new Label(container, SWT.NONE);
		locationLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		locationLabel.setText("Location");

		Image errorImage = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR)
				.getImage();

		locationText = new Text(container, SWT.BORDER);
		locationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		locationControlDecoration = new ControlDecoration(locationText, SWT.TOP | SWT.LEFT);
		locationControlDecoration.setImage(errorImage);
		locationControlDecoration.setShowOnlyOnFocus(true);
		locationText.addModifyListener(e -> {
			updateDirectory(locationText.getText());
			setPageComplete(isPageComplete());
		});

		Button browseButton = new Button(container, SWT.NONE);
		browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(browseButton.getShell());
				String path = dialog.open();
				if (path != null) {
					updateDirectory(path);
				}
				setPageComplete(isPageComplete());
			}
		});
		Composite linesAboveLink = new Composite(container, SWT.NONE);
		GridData linesAboveLinkLayoutData = new GridData(SWT.FILL, SWT.FILL);
		linesAboveLinkLayoutData.heightHint = linesAboveLinkLayoutData.widthHint = 30;
		linesAboveLink.setLayoutData(linesAboveLinkLayoutData);
		linesAboveLink.addPaintListener(e -> {
			e.gc.setForeground(((Control)e.widget).getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			e.gc.drawLine(0, e.height/2, e.width/2, e.height/2);
			e.gc.drawLine(e.width/2, e.height/2, e.width/2, e.height);
		});

		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);

		linkButton = new Button(container, SWT.TOGGLE);
		linkButton.setToolTipText("Link project name and folder name");
		linkButton.setSelection(true);
		try (InputStream iconStream = getClass().getResourceAsStream("/icons/link_obj.png")) {
			linkImage = new Image(linkButton.getDisplay(), iconStream);
			linkButton.setImage(linkImage);
		} catch (IOException e1) {
			AcutePlugin.logError(e1);
		}
		linkButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent s) {
				isDirectoryAndProjectLinked = linkButton.getSelection();
				projectNameText.setEnabled(!linkButton.getSelection());
				updateProjectName();
			}
		});

		Label projectNameLabel = new Label(container, SWT.NONE);
		projectNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		projectNameLabel.setText("Project name");

		projectNameText = new Text(container, SWT.BORDER);
		projectNameText.setEnabled(false);
		projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		projectNameControlDecoration = new ControlDecoration(projectNameText, SWT.TOP | SWT.LEFT);
		projectNameControlDecoration.setImage(errorImage);
		projectNameControlDecoration.setShowOnlyOnFocus(true);
		projectNameText.addModifyListener(e -> {
			updateProjectName();
			setPageComplete(isPageComplete());
		});
		Composite linesBelowLink = new Composite(container, SWT.NONE);
		GridData linesBelowLinkLayoutData = new GridData(SWT.FILL, SWT.FILL);
		linesBelowLinkLayoutData.heightHint = linesBelowLinkLayoutData.widthHint = 30;
		linesBelowLink.setLayoutData(linesAboveLinkLayoutData);
		linesBelowLink.addPaintListener(e -> {
			e.gc.setForeground(((Control)e.widget).getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			e.gc.drawLine(0, e.height/2, e.width/2, e.height/2);
			e.gc.drawLine(e.width/2, e.height/2, e.width/2, 0);
		});
		new Label(container, SWT.NONE);

		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);

		Label projectTemplateLabel = new Label(container, SWT.NONE);
		projectTemplateLabel.setText("Project Template");

		templatesMap = DotnetNewAccessor.getTemplates();

		List list = new List(container, SWT.V_SCROLL);
		GridData listBoxData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
		list.setLayoutData(listBoxData);

		templateViewer = new ListViewer(list);
		templateViewer.setContentProvider(new ArrayContentProvider());
		if (!templatesMap.isEmpty()) {
			listBoxData.heightHint = 100;
			SortedSet<String> templatesSortedSet = new TreeSet<String>();
			for (String temp : templatesMap.keySet()) {
				templatesSortedSet.add(temp);
			}
			for (String temp : templatesSortedSet) {
				templateViewer.add(temp);
			}
		} else {
			templateViewer.add("No available templates");
			list.setEnabled(false);
		}

		new Label(container, SWT.NONE);

		Composite workingSetComposite = new Composite(container, SWT.NONE);
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1);
		workingSetComposite.setLayoutData(layoutData);
		workingSetComposite.setLayout(new GridLayout(1, false));
		WorkingSetRegistry registry = WorkbenchPlugin.getDefault().getWorkingSetRegistry();
		String[] workingSetIds = Arrays.stream(registry.getNewPageWorkingSetDescriptors())
				.map(WorkingSetDescriptor::getId).toArray(String[]::new);
		IStructuredSelection wsSel = null;
		if (this.workingSets != null) {
			wsSel = new StructuredSelection(this.workingSets.toArray());
		}
		this.workingSetsGroup = new WorkingSetGroup(workingSetComposite, wsSel, workingSetIds);
	}

	private void updateProjectName() {
		if (!isDirectoryAndProjectLinked) {
			projectName = projectNameText.getText();
		} else if (projectName == null || !projectName.equals(directory.getName())) {
			projectName = directory.getName();
			projectNameText.setText(projectName);
		}
	}

	private void updateDirectory(String directoryPath) {
		directory = new File(directoryPath);
		if (!locationText.getText().equals(directoryPath)) {
			locationText.setText(directoryPath);
		} else if (isDirectoryAndProjectLinked) {
			updateProjectName();
		}
	}

	@Override
	public boolean isPageComplete() {
		String locationError = "";
		String projectNameError = "";
		if (directory == null || directory.getPath().isEmpty()) {
			locationError = "Please specify a directory";
		} else if (projectName == null || projectName.isEmpty()) {
			projectNameError = "Please specify project name";
		} else if (directory.isFile()) {
			locationError = "Invalid location: it is an existing file.";
		} else if (directory.getParentFile() == null
				|| (!directory.exists() && !directory.getParentFile().canWrite())) {
			locationError = "Unable to create such directory";
		} else if (directory.exists() && !directory.canWrite()) {
			locationError = "Cannot write in this directory";
		} else {
			File dotProject = new File(directory, IProjectDescription.DESCRIPTION_FILE_NAME);
			if (dotProject.exists()) {
				IProjectDescription desc = null;
				try {
					desc = ResourcesPlugin.getWorkspace()
							.loadProjectDescription(Path.fromOSString(dotProject.getAbsolutePath()));
				} catch (CoreException e) {
					projectNameError = "Invalid .project file in directory";
				}
				if (!desc.getName().equals(projectName)) {
					projectNameError = "Project name must match one in .project file: " + desc.getName();
				}
			} else {
				IProject project = null;
				try {
					project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
					if (project.exists() && (project.getLocation() == null
							|| !directory.getAbsoluteFile().equals(project.getLocation().toFile().getAbsoluteFile()))) {
						projectNameError = "Another project with same name already exists in workspace.";
					}
				} catch (IllegalArgumentException ex) {
					projectNameError = "Invalid project name";
				}
			}
		}

		String error = locationError + projectNameError;

		if (error.isEmpty()) {
			setErrorMessage(null);
			projectNameControlDecoration.hide();
			locationControlDecoration.hide();
		} else {
			if (!locationError.isEmpty()) {
				locationControlDecoration.showHoverText(locationError);
				locationControlDecoration.show();
				projectNameControlDecoration.hide();
			} else {
				projectNameControlDecoration.showHoverText(projectNameError);
				projectNameControlDecoration.show();
				locationControlDecoration.hide();
			}
			setErrorMessage(error);
		}
		return error.isEmpty();
	}

	@Override
	public void dispose() {
		super.dispose();
		this.linkImage.dispose();
	}
}
