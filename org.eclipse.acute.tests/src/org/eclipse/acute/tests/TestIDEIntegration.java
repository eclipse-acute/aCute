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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;
import org.junit.jupiter.api.Test;

public class TestIDEIntegration extends AbstractAcuteTest {

	@Test
	public void testEditorAssociation() throws Exception {
		IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IEditorPart editor = IDE.openEditor(activePage, getProject("csproj").getFile("Program.cs"));
		assertInstanceOf(ExtensionBasedTextEditor.class, editor);
	}

}
