/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  mistria (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.acute.debug;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.acute.AcutePlugin;
import org.eclipse.acute.Messages;
import org.eclipse.acute.debug.DebuggersRegistry.DebuggerInfo;
import org.eclipse.acute.dotnetrun.DotnetRunDelegate;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

public class DotnetDebugLaunchShortcut implements ILaunchShortcut2 {

	@Override public void launch(ISelection selection, String mode) {
		DebugUITools.launch(getLaunchConfigurations(selection)[0], mode);
	}

	@Override public void launch(IEditorPart editor, String mode) {
		DebugUITools.launch(getLaunchConfigurations(editor)[0], mode);
	}

	@Override public ILaunchConfiguration[] getLaunchConfigurations(ISelection selection) {
		return new ILaunchConfiguration[] { getLaunchConfiguration(getLaunchableResource(selection)) };
	}

	@Override public ILaunchConfiguration[] getLaunchConfigurations(IEditorPart editorpart) {
		return new ILaunchConfiguration[] { getLaunchConfiguration(getLaunchableResource(editorpart)) };
	}

	@Override public IResource getLaunchableResource(ISelection selection) {
		Set<IResource> resources = new HashSet<>();
		if (selection instanceof IStructuredSelection sse) {
			for (Object o : sse.toArray()) {
				IResource resource = Adapters.adapt(o, IResource.class);
				if (resource != null) {
					resources.add(resource);
				}
			}
		}
		if (resources.isEmpty()) {
			return null;
		} else if (resources.size() == 1) {
			return resources.iterator().next();
		} else {
			// TODO ambiguous
			return null;
		}
	}

	@Override public IResource getLaunchableResource(IEditorPart editorpart) {
		if (editorpart.getEditorInput() instanceof IFileEditorInput fei) {
			return fei.getFile();
		}
		return null;
	}

	private ILaunchConfiguration getLaunchConfiguration(IResource resource) {
		final String mode = "debug"; //$NON-NLS-1$
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType configType = launchManager
				.getLaunchConfigurationType(DotnetDebugLaunchDelegate.ID);
		try {
			ILaunchConfiguration[] launchConfigurations = launchManager.getLaunchConfigurations(configType);

			String configName;
			if (resource.getLocation().toFile().isFile()) {
				configName = NLS.bind(Messages.DotnetRunDelegate_configuration, resource.getParent().getName() + "." + resource.getName()); //$NON-NLS-1$
			} else {
				configName = NLS.bind(Messages.DotnetRunDelegate_configuration, resource.getName());
			}

			for (ILaunchConfiguration iLaunchConfiguration : launchConfigurations) {
				if (iLaunchConfiguration.getName().equals(configName)
						&& iLaunchConfiguration.getModes().contains(mode)) {
					return iLaunchConfiguration;
				}
			}
			configName = launchManager.generateLaunchConfigurationName(configName);
			ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, configName);
			if (resource.getLocation().toFile().isFile()) {
				resource = resource.getParent();
			}
			wc.setAttribute(DotnetRunDelegate.PROJECT_FOLDER, resource.getFullPath().toString());
			DebuggerInfo info = DebuggersRegistry.getDefaultDebugger();
			wc.setAttribute(DSPPlugin.ATTR_DSP_CMD, info.debugger.getAbsolutePath());
			wc.setAttribute(DSPPlugin.ATTR_DSP_ARGS, info.args);
			return wc;
		} catch (CoreException e) {
			AcutePlugin.logError(e);
		}
		return null;
	}
}
