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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestLSPIntegration extends AbstractAcuteTest {

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		LanguageServerPlugin.getDefault().getPreferenceStore().putValue("org.eclipse.acute.Omnisharp.file.logging.enabled", Boolean.toString(true));
	}

	@Test
	public void testLSFoundWithCSProj() throws Exception {
		IProject project = getProject("csproj");
		IFile csharpSourceFile = project.getFile("Program.cs");
		IEditorPart editor = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), csharpSourceFile);
		SourceViewer viewer = (SourceViewer)getTextViewer(editor);
		workaroundOmniSharpIssue1088(viewer.getDocument());
		int offset = viewer.getDocument().get().indexOf("WriteLine") + 6;
		viewer.setSelectedRange(offset, 0);
		AtomicReference<ICompletionProposal> topProposal = new AtomicReference<>();
		viewer.getContentAssistantFacade().addCompletionListener(new ICompletionListener() {
			@Override public void selectionChanged(ICompletionProposal proposal, boolean smartToggle) {
				topProposal.set(proposal);
			}

			@Override public void assistSessionStarted(ContentAssistEvent event) {
				// nothing
			}

			@Override public void assistSessionEnded(ContentAssistEvent event) {
				// nothing
			}
		});
		viewer.doOperation(SourceViewer.CONTENTASSIST_PROPOSALS);
		assertTrue(DisplayHelper.waitForCondition(viewer.getTextWidget().getDisplay(), 5000, () -> {
			ICompletionProposal proposal = topProposal.get();
			return proposal != null && proposal.getDisplayString().contains("WriteLine");
		}));
	}

	private void workaroundOmniSharpIssue1088(IDocument document) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		// Wait for document to be connected
		Method getDocumentListenersMethod = AbstractDocument.class.getDeclaredMethod("getDocumentListeners");
		getDocumentListenersMethod.setAccessible(true);
		DisplayHelper.waitForCondition(Display.getDefault(), 5000, () -> {
			try {
				return ((Collection<?>) getDocumentListenersMethod.invoke(document)).stream()
						.anyMatch(o -> o.getClass().getName().contains("lsp4e"));
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
				return false;
			}
		});
		assertNotEquals(Collections.emptyList(), getDocumentListenersMethod.invoke(document),
				"LS Document listener was not setup after 5s");
		// workaround https://github.com/OmniSharp/omnisharp-roslyn/issues/1445
		DisplayHelper.sleep(5000);
		// force fake modification for OmniSharp to wake up
		document.set(document.get().replace("Hello", "Kikoo"));
		DisplayHelper.sleep(500);
	}

	@Test
	public void testLSFindsDiagnosticsCSProj() throws Exception  {
		IProject project = getProject("csprojWithError");
		IFile csharpSourceFile = project.getFile("Program.cs");
		IEditorPart editor = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), csharpSourceFile);
		SourceViewer viewer = (SourceViewer)getTextViewer(editor);
		workaroundOmniSharpIssue1088(viewer.getDocument());
		DisplayHelper.waitForCondition(Display.getDefault(), 5000, () -> {
			try {
				return csharpSourceFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length > 0;
			} catch (Exception e) {
				return false;
			}
		});
		DisplayHelper.sleep(500); // time to fill marker details
		IMarker marker = csharpSourceFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)[0];
		assertTrue(marker.getType().contains("lsp4e"));
		assertEquals(12, marker.getAttribute(IMarker.LINE_NUMBER, -1));
	}
}