/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestDebug extends AbstractAcuteTest {

	private ILaunchManager launchManager;

	@Before
	public void setUpLaunch() throws DebugException {
		this.launchManager = DebugPlugin.getDefault().getLaunchManager();
		removeAllLaunches();
	}

	private void removeAllLaunches() throws DebugException {
		for (ILaunch launch : this.launchManager.getLaunches()) {
			launch.terminate();
		}
	}

	@After
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
		assertTrue("New Debug Target not created", DisplayHelper.waitForCondition(Display.getDefault(), 30000,
				() -> launchManager.getDebugTargets().length > before.size()));
		Set<IDebugTarget> after = new HashSet<>(List.of(launchManager.getDebugTargets()));
		after.removeAll(before);
		assertEquals("Extra DebugTarget not found", 1, after.size());
		IDebugTarget target = after.iterator().next();
		assertTrue("Debug Target shows no threads", DisplayHelper.waitForCondition(Display.getDefault(), 3000, () -> {
			try {
				return target.getThreads().length > 0;
			} catch (DebugException e) {
				e.printStackTrace();
				return false;
			}
		}));
		target.getThreads();
		assertTrue("No thread is suspended", DisplayHelper.waitForCondition(Display.getDefault(), 3000, () -> {
			try {
				return Arrays.stream(target.getThreads()).anyMatch(ISuspendResume::isSuspended);
			} catch (DebugException e) {
				e.printStackTrace();
				return false;
			}
		}));
	}

}
