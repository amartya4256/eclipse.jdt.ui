/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.sef;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.core.IField;

import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldCompositeRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class SelfEncapsulateFieldWizard extends RefactoringWizard {

	private List<IField> preselected;

	public List<IField> getPreselected() {
		return preselected;
	}

	/* package */ static final String DIALOG_SETTING_SECTION= "SelfEncapsulateFieldWizard"; //$NON-NLS-1$

	public SelfEncapsulateFieldWizard(SelfEncapsulateFieldRefactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.SelfEncapsulateField_sef);
		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
	}

	public SelfEncapsulateFieldWizard(SelfEncapsulateFieldCompositeRefactoring refactoring, List<IField> preselected) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.SelfEncapsulateField_sef);
		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
		this.preselected = preselected;
	}

	@Override
	protected void addUserInputPages() {
		addPage(new SelfEncapsulateFieldInputPage());
	}

	@Override
	public boolean performFinish() {
		SelfEncapsulateFieldInputPage inputPage = (SelfEncapsulateFieldInputPage)this.getStartingPage();
		boolean b = inputPage.getSelectedRefactorings().stream()
				.map(refactoring -> {
					try {
						refactoring.checkInitialConditions(new NullProgressMonitor());
					} catch (CoreException e) {
						ExceptionHandler.handle(e, getShell(), getRefactoring().getName(),
							RefactoringMessages.RefactoringUI_open_unexpected_exception);
						return false;
					}
					return inputPage.performFinish(refactoring);
						})
				.allMatch(Boolean::booleanValue);
		return b;
	}
}
