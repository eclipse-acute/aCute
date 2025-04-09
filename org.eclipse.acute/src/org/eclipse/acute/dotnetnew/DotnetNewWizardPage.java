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
 *  Lucas Bullen   (Red Hat Inc.) - Logic implementation
 *******************************************************************************/
package org.eclipse.acute.dotnetnew;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import org.eclipse.acute.AcutePlugin;
import org.eclipse.acute.AcutePreferenceInitializer;
import org.eclipse.acute.AcutePreferencePage;
import org.eclipse.acute.Messages;
import org.eclipse.acute.dotnetnew.DotnetNewAccessor.Template;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.dialogs.WorkingSetGroup;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class DotnetNewWizardPage extends WizardPage {

	private Set<IWorkingSet> workingSets;
	private File directory;
	private String projectName;
	private boolean isDirectoryAndProjectLinked = true;

	private Text locationText;
	private Text projectNameText;
	private ListViewer templateViewer;
	private WorkingSetGroup workingSetsGroup;
	private Image linkImage;
	private ControlDecoration locationControlDecoration;
	private ControlDecoration projectNameControlDecoration;
	private ControlDecoration templateControlDecoration;
	private IPropertyChangeListener updateTemplatesListener;

	protected DotnetNewWizardPage() {
		super(DotnetNewWizardPage.class.getName());
		setTitle(Messages.DotnetNewWizardPage_createProject_title);
		setDescription(Messages.DotnetNewWizardPage_createProject_message);

		Bundle bundle = FrameworkUtil.getBundle(this.getClass());
		URL url = bundle.getEntry("images/dotnet.png"); //$NON-NLS-1$
		ImageDescriptor imageDescriptor = ImageDescriptor.createFromURL(url);
		setImageDescriptor(imageDescriptor);
	}

	public void setDirectory(File directory) {
		this.directory = directory;
	}

	public File getDirectory() {
		if (isDirectoryAndProjectLinked) {
			return directory;
		} else {
			return new File(directory.toString() + "/" + projectName); //$NON-NLS-1$
		}
	}

	public String getProjectName() {
		return projectName;
	}

	public @Nullable Template getTemplate() {
		IStructuredSelection selection = (IStructuredSelection) templateViewer.getSelection();
		if (templateViewer.getList().isEnabled() && selection.isEmpty()) {
			return null;
		}
		return (Template) selection.getFirstElement();
	}

	public IWorkingSet[] getWorkingSets() {
		return workingSetsGroup.getSelectedWorkingSets();
	}

	public void setWorkingSets(Set<IWorkingSet> workingSets) {
		this.workingSets = workingSets;
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		setControl(container);
		container.setLayout(new GridLayout(4, false));

		Label locationLabel = new Label(container, SWT.NONE);
		locationLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		locationLabel.setText(Messages.DotnetNewWizardPage_location);

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
		browseButton.setText(Messages.DotnetNewWizardPage_browse);
		browseButton.addSelectionListener(widgetSelectedAdapter(e -> {
			DirectoryDialog dialog = new DirectoryDialog(browseButton.getShell());
			String path = dialog.open();
			if (path != null) {
				updateDirectory(path);
			}
			setPageComplete(isPageComplete());
		}));
		Composite linesAboveLink = new Composite(container, SWT.NONE);
		GridData linesAboveLinkLayoutData = new GridData(SWT.FILL, SWT.FILL);
		linesAboveLinkLayoutData.heightHint = linesAboveLinkLayoutData.widthHint = 30;
		linesAboveLink.setLayoutData(linesAboveLinkLayoutData);
		linesAboveLink.addPaintListener(e -> {
			e.gc.setForeground(e.widget.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			e.gc.drawLine(0, e.height/2, e.width/2, e.height/2);
			e.gc.drawLine(e.width/2, e.height/2, e.width/2, e.height);
		});

		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);
		new Label(container, SWT.NONE);

		Button linkButton = new Button(container, SWT.TOGGLE);
		linkButton.setToolTipText(Messages.DotnetNewWizardPage_linkNames);
		linkButton.setSelection(true);
		try (InputStream iconStream = getClass().getResourceAsStream("/icons/link_obj.png")) { //$NON-NLS-1$
			linkImage = new Image(linkButton.getDisplay(), iconStream);
			linkButton.setImage(linkImage);
		} catch (IOException e1) {
			AcutePlugin.logError(e1);
		}
		linkButton.addSelectionListener(widgetSelectedAdapter(s -> {
			isDirectoryAndProjectLinked = linkButton.getSelection();
			projectNameText.setEnabled(!linkButton.getSelection());
			updateProjectName();
		}));

		Label projectNameLabel = new Label(container, SWT.NONE);
		projectNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		projectNameLabel.setText(Messages.DotnetNewWizardPage_projectName);

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
			e.gc.setForeground(e.widget.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
			e.gc.drawLine(0, e.height/2, e.width/2, e.height/2);
			e.gc.drawLine(e.width/2, e.height/2, e.width/2, 0);
		});
		new Label(container, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 4, 1));

		Label projectTemplateLabel = new Label(container, SWT.NONE);
		projectTemplateLabel.setText(Messages.DotnetNewWizardPage_projectTemplate);

		List list = new List(container, SWT.V_SCROLL | SWT.BORDER);
		GridData listBoxData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		list.setLayoutData(listBoxData);
		templateViewer = new ListViewer(list);
		templateViewer.setContentProvider(new ArrayContentProvider());
		templateViewer.setComparator(new ViewerComparator()); // default uses getLabel()/toString()
		templateViewer.addSelectionChangedListener(e -> setPageComplete(isPageComplete()));
		templateControlDecoration = new ControlDecoration(templateViewer.getControl(), SWT.TOP | SWT.LEFT);
		templateControlDecoration.setImage(errorImage);
		updateTemplateList();

		//Update Template List with preferences change
		IPreferenceStore store = AcutePlugin.getDefault().getPreferenceStore();
		updateTemplatesListener = event -> {
			if (event.getProperty().equals(AcutePreferenceInitializer.EXPLICIT_DOTNET_PATH)) {
				updateTemplateList();
			}
		};
		store.addPropertyChangeListener(updateTemplatesListener);

		new Label(container, SWT.NONE);

		new Label(container, SWT.NONE);
		Link preferencesLink = new Link(container, SWT.NONE);
		preferencesLink.setText(Messages.DotnetNewWizardPage_dotnetPreferencesLink);
		preferencesLink.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false,2,1));
		preferencesLink.addSelectionListener(widgetSelectedAdapter(s -> Display.getDefault().asyncExec(() -> {
			PreferenceDialog preferenceDialog = PreferencesUtil.createPreferenceDialogOn(getShell(),
					AcutePreferencePage.PAGE_ID,
					new String[] { AcutePreferencePage.PAGE_ID }, null);
			preferenceDialog.setBlockOnOpen(false);
			preferenceDialog.open();
		})));
		new Label(container, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 4, 1));

		Composite workingSetComposite = new Composite(container, SWT.NONE);
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1);
		workingSetComposite.setLayoutData(layoutData);
		workingSetComposite.setLayout(new GridLayout(1, false));
		String[] workingSetIds = new String[] { "org.eclipse.ui.resourceWorkingSetPage" }; //$NON-NLS-1$
		IStructuredSelection wsSel = null;
		if (this.workingSets != null) {
			wsSel = new StructuredSelection(this.workingSets.toArray());
		}
		this.workingSetsGroup = new WorkingSetGroup(workingSetComposite, wsSel, workingSetIds);

		if (directory != null) {
			updateDirectory(directory.getAbsolutePath());
		}
	}

	private void updateTemplateList() {
		if(templateViewer.getList().isDisposed()) {
			return;
		}
		setTemplateViewToText(Messages.DotnetNewWizardPage_loadingTemplates);
		try {
			AcutePlugin.getDotnetCommand();
		} catch (IllegalStateException e) {
			setTemplateViewToText(Messages.DotnetNewWizardPage_dotnetError);
			return;
		}
		Job.create(Messages.DotnetNewWizardPage_retriveTemplates, (ICoreRunnable) monitor -> {
			final java.util.List<Template> templates = DotnetNewAccessor.getTemplates();
			Display.getDefault().asyncExec(() -> {
				templateViewer.getList().removeAll();
				if (!templates.isEmpty()) {
					((GridData) templateViewer.getList().getLayoutData()).heightHint = 100;
					Shell shell = templateViewer.getControl().getShell();
					shell.setSize(shell.getSize().x, shell.getSize().y + 100);
					templateViewer.setInput(templates);
					if (templateViewer.getSelection().isEmpty()) {
						templateViewer.getList().setSelection(0);
					}
					templateViewer.getList().setEnabled(true);
					setPageComplete(isPageComplete());
				} else {
					templateViewer.add(Messages.DotnetNewWizardPage_noTemplatesError);
				}
				templateViewer.getControl().getParent().layout();
			});
		}).schedule();
	}

	private void setTemplateViewToText(String text) {
		Display.getDefault().asyncExec(() -> {
			if(templateViewer.getList().getItemCount() > 1) {
				((GridData) templateViewer.getList().getLayoutData()).heightHint = SWT.DEFAULT;
				Shell shell = templateViewer.getControl().getShell();
				shell.setSize(shell.getSize().x, shell.getSize().y - 100);
			}
			templateViewer.getList().removeAll();
			templateViewer.add(text);
			templateViewer.getControl().getParent().layout();
			templateViewer.getList().setEnabled(false);
		});
	}

	private void updateProjectName() {
		if (!isDirectoryAndProjectLinked) {
			projectName = projectNameText.getText();
		} else if (projectName == null || (directory != null && !projectName.equals(directory.getName()))) {
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
		String locationError = ""; //$NON-NLS-1$
		String projectNameError = ""; //$NON-NLS-1$
		String templateError = ""; //$NON-NLS-1$
		if (directory == null || directory.getPath().isEmpty()) {
			locationError = Messages.DotnetNewWizardPage_directroyError_empty;
		} else if (projectName == null || projectName.isEmpty()) {
			projectNameError = Messages.DotnetNewWizardPage_projectError_empty;
		} else if (directory.isFile()) {
			locationError = Messages.DotnetNewWizardPage_locationError_existingFile;
		} else if (directory.getParentFile() == null
				|| (!directory.exists() && !directory.getParentFile().canWrite())) {
			locationError = Messages.DotnetNewWizardPage_locationError_unableToCreate;
		} else if (directory.exists() && !directory.canWrite()) {
			locationError = Messages.DotnetNewWizardPage_locationError_unableToWrite;
		} else if (getTemplate() == null) {
			templateError = Messages.DotnetNewWizardPage_templateError_empty;
		} else {
			File dotProject = new File(directory, IProjectDescription.DESCRIPTION_FILE_NAME);
			if (dotProject.exists()) {
				IProjectDescription desc = null;
				try {
					desc = ResourcesPlugin.getWorkspace()
							.loadProjectDescription(Path.fromOSString(dotProject.getAbsolutePath()));
				} catch (CoreException e) {
					projectNameError = Messages.DotnetNewWizardPage_projectError_invalidDotProjectFile;
				}
				if (!desc.getName().equals(projectName)) {
					projectNameError = Messages.DotnetNewWizardPage_projectError_invalidNameMatch + desc.getName();
				}
			} else {
				IProject project = null;
				try {
					project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
					if (project.exists() && (project.getLocation() == null
							|| !directory.getAbsoluteFile().equals(project.getLocation().toFile().getAbsoluteFile()))) {
						projectNameError = Messages.DotnetNewWizardPage_projectError_existingName;
					}
				} catch (IllegalArgumentException ex) {
					projectNameError = Messages.DotnetNewWizardPage_projectError_invalidName;
				}
			}
		}

		String error = locationError + projectNameError + templateError;

		if (error.isEmpty()) {
			setErrorMessage(null);
			projectNameControlDecoration.hide();
			locationControlDecoration.hide();
			templateControlDecoration.hide();
		} else {
			if (!locationError.isEmpty()) {
				locationControlDecoration.showHoverText(locationError);
				locationControlDecoration.show();
				projectNameControlDecoration.hide();
				templateControlDecoration.hide();
			} else if(!projectNameError.isEmpty()) {
				projectNameControlDecoration.showHoverText(projectNameError);
				projectNameControlDecoration.show();
				locationControlDecoration.hide();
				templateControlDecoration.hide();
			} else {
				templateControlDecoration.showHoverText(projectNameError);
				templateControlDecoration.show();
				locationControlDecoration.hide();
				projectNameControlDecoration.hide();
			}
			setErrorMessage(error);
		}
		return error.isEmpty();
	}

	@Override
	public void dispose() {
		super.dispose();
		this.linkImage.dispose();
		if (updateTemplatesListener != null){
			IPreferenceStore store = AcutePlugin.getDefault().getPreferenceStore();
			store.removePropertyChangeListener(updateTemplatesListener);
		}
	}
}