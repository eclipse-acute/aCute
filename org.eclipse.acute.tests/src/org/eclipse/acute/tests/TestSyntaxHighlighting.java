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
package org.eclipse.acute.tests;

import org.eclipse.core.resources.IFile;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Assert;
import org.junit.Test;

public class TestSyntaxHighlighting extends AbstractAcuteTest {

	@Test
	public void testSyntaxHighlighting() throws Exception {
		IFile csharpSourceFile = getProject("csproj").getFile("Program.cs");
		TextEditor editor = (TextEditor) IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), csharpSourceFile, "org.eclipse.ui.genericeditor.GenericEditor");
		StyledText editorTextWidget = (StyledText)editor.getAdapter(Control.class);
		DisplayHelper.waitForCondition(editorTextWidget.getDisplay(), 4000, ()-> editorTextWidget.getStyleRanges().length > 1);
		Assert.assertTrue("There should be multiple styles in editor", editorTextWidget.getStyleRanges().length > 1);
	}

}
