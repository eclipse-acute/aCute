/*******************************************************************************
 * Copyright (c) 2019, 2025 Red Hat Inc. and others.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.acute.debug.DotnetDebugLaunchShortcut;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ISuspendResume;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestDebug extends AbstractAcuteTest {

	private ILaunchManager launchManager;

	@BeforeEach
	public void setUpLaunch() throws DebugException {
		this.launchManager = DebugPlugin.getDefault().getLaunchManager();
		removeAllLaunches();
	}

	private void removeAllLaunches() throws DebugException {
		for (ILaunch launch : this.launchManager.getLaunches()) {
			launch.terminate();
		}
	}

	@AfterEach
	public void trearDownLaunch() throws DebugException {
		removeAllLaunches();
	}

	@Test
	public void testFindThreadsAndHitsBreakpoint() throws Exception {
		IProject project = getProject("csproj");
		IFile csharpSourceFile = project.getFile("Program.cs");
		ITextEditor editor = (ITextEditor)IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), csharpSourceFile);
		IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		ITextSelection selection = new TextSelection(doc, doc.getLineOffset(10), 0);
		StyledText styledText = (StyledText)editor.getAdapter(Control.class);
		StyleRange initialStyle = styledText.getStyleRangeAtOffset(selection.getOffset());
		IToggleBreakpointsTarget toggleBreakpointsTarget = DebugUITools.getToggleBreakpointsTargetManager().getToggleBreakpointsTarget(editor, selection);
		toggleBreakpointsTarget.toggleLineBreakpoints(editor, selection);
		Set<IDebugTarget> before = new HashSet<>(List.of(launchManager.getDebugTargets()));
		new DotnetDebugLaunchShortcut().launch(editor, ILaunchManager.DEBUG_MODE);
		assertTrue(DisplayHelper.waitForCondition(Display.getDefault(), 30000,
				() -> launchManager.getDebugTargets().length > before.size()), "New Debug Target not created");
		Set<IDebugTarget> after = new HashSet<>(List.of(launchManager.getDebugTargets()));
		after.removeAll(before);
		assertEquals(1, after.size(), "Extra DebugTarget not found");
		IDebugTarget target = after.iterator().next();
		assertTrue(DisplayHelper.waitForCondition(Display.getDefault(), 3000, () -> {
			try {
				return target.getThreads().length > 0;
			} catch (DebugException e) {
				e.printStackTrace();
				return false;
			}
		}), "Debug Target shows no threads");
		target.getThreads();
		assertTrue(DisplayHelper.waitForCondition(Display.getDefault(), 3000, () -> {
			try {
				return Arrays.stream(target.getThreads()).anyMatch(ISuspendResume::isSuspended);
			} catch (DebugException e) {
				e.printStackTrace();
				return false;
			}
		}), "No thread is suspended");
	}

}
