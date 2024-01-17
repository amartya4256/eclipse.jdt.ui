package org.eclipse.jdt.internal.corext.refactoring.sef;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;

public class SelfEncapsulateFieldCompositeRefactoring extends Refactoring {

	private final List<SelfEncapsulateFieldRefactoring> fRefactorings;

	public List<SelfEncapsulateFieldRefactoring> getRefactorings() {
		return fRefactorings;
	}

	public SelfEncapsulateFieldCompositeRefactoring(List<SelfEncapsulateFieldRefactoring> refactorings) {
		fRefactorings = refactorings;
	}

	@Override
	public String getName() {
		return RefactoringCoreMessages.SelfEncapsulateField_name;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus fInitialConditions = new RefactoringStatus();
		for(SelfEncapsulateFieldRefactoring selfEncapsulateFieldRefactoring : fRefactorings) {
			fInitialConditions.merge(selfEncapsulateFieldRefactoring.checkInitialConditions(pm));
		}
		return fInitialConditions;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		CompositeChange change = new CompositeChange("Self Encapsulate"); //$NON-NLS-1$
		for(SelfEncapsulateFieldRefactoring selfEncapsulateFieldRefactoring : fRefactorings.stream().filter(SelfEncapsulateFieldRefactoring::isSeletced).toList()) {
			change.merge((DynamicValidationRefactoringChange)selfEncapsulateFieldRefactoring.createChange(pm));
		}
		return change;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus fFinalConditions = new RefactoringStatus();
		for(SelfEncapsulateFieldRefactoring selfEncapsulateFieldRefactoring : fRefactorings.stream().filter(SelfEncapsulateFieldRefactoring::isSeletced).toList()) {
			fFinalConditions.merge(selfEncapsulateFieldRefactoring.checkFinalConditions(pm));
		}
		return fFinalConditions;
	}

}
