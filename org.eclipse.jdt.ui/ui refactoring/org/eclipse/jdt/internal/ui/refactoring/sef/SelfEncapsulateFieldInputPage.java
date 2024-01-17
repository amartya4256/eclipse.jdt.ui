/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldCompositeRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.sef.SelfEncapsulateFieldRefactoring;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

public class SelfEncapsulateFieldInputPage extends UserInputWizardPage {

	private static final String REFACTORING_TYPE= "refactoringType"; //$NON-NLS-1$
	private static final String TEXT_WIDGET= "textWidget"; //$NON-NLS-1$
	private static final String REFACTORING= "refactoring"; //$NON-NLS-1$
	private SelfEncapsulateFieldCompositeRefactoring fRefactorings;
	private IDialogSettings fSettings;
	private List<Control> fEnablements = new ArrayList<>();

	private HashMap<SelfEncapsulateFieldRefactoring, TreeItem> refactoringButtonMap = new HashMap<>();

	private static final String GENERATE_JAVADOC= "GenerateJavadoc";  //$NON-NLS-1$

	private enum RefactoringType {
		GETTER, SETTER;
	}

	public SelfEncapsulateFieldInputPage() {
		super("InputPage"); //$NON-NLS-1$
		setDescription(RefactoringMessages.SelfEncapsulateFieldInputPage_description);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_REFACTOR_CU);
	}

	public boolean performFinish(Refactoring refactoring){
		fRefactoring = refactoring;
		return performFinish();
	}

	public List<SelfEncapsulateFieldRefactoring> getSelectedRefactorings() {
		return fRefactorings.getRefactorings().stream()
				.filter(SelfEncapsulateFieldRefactoring::isSeletced).toList();
	}

	private void enableEditing(TreeItem item, Tree tree, String textData, RefactoringType refactoringType, SelfEncapsulateFieldRefactoring refactoring) {
        Text text = new Text(tree, SWT.BORDER);
        text.setSize(2, text.getSize().y);
        text.setText(textData);
        TreeEditor editor = new TreeEditor(tree);
        editor.minimumWidth = 200;

        item.setData("textEditor", editor); //$NON-NLS-1$
        item.setData(TEXT_WIDGET, text);
        item.setData(REFACTORING_TYPE, refactoringType);
        item.setData(REFACTORING, refactoring);

        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                Text textWidget = (Text) item.getData(TEXT_WIDGET);
                item.setText(textWidget.getText());
                String methodName = item.getChecked() ? textWidget.getText() : ""; //$NON-NLS-1$
                if(refactoringType == RefactoringType.GETTER) {
                	refactoring.setGetterName(methodName);
                } else {
                	refactoring.setSetterName(methodName);
                }
                processValidation();
            }
        });

        text.addTraverseListener(event -> {
            if (event.detail == SWT.TRAVERSE_RETURN) {
                Text textWidget = (Text) item.getData(TEXT_WIDGET);
                item.setText(textWidget.getText());
            }
        });
        editor.setEditor(text, item);
    }

	@Override
	public void createControl(Composite parent) {
		SelfEncapsulateFieldWizard wizard = (SelfEncapsulateFieldWizard) getRefactoringWizard();
		fRefactorings = (SelfEncapsulateFieldCompositeRefactoring) wizard.getRefactoring();

		fEnablements= new ArrayList<>();
		loadSettings();

		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		initializeDialogUnits(result);
		result.setLayout(new GridLayout(3, false));

		Composite nameComposite= new Composite(result, SWT.NONE);
		nameComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		GridLayout gridLayout= new GridLayout(1, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;

		nameComposite.setLayout(gridLayout);
		Tree tree = new Tree(nameComposite, SWT.CHECK);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		tree.addListener(SWT.Selection, new Listener() {

			@Override
			public void handleEvent(Event event) {
				 TreeItem selectedItem = (TreeItem) event.item;
	             if(event.detail == SWT.CHECK) {
	            	 TreeItem parentItem = selectedItem.getParentItem();
	            	 if(parentItem != null) {	// Setter or Getter
	            		 SelfEncapsulateFieldRefactoring refactoring = (SelfEncapsulateFieldRefactoring) selectedItem.getData(REFACTORING);
	            		 String methodName = selectedItem.getChecked() ? ((Text) selectedItem.getData(TEXT_WIDGET)).getText() : ""; //$NON-NLS-1$
	            		 if ((RefactoringType) selectedItem.getData(REFACTORING_TYPE) == RefactoringType.GETTER) {
	            			 refactoring.setGetterName(methodName);
	            		 } else {
	            			 refactoring.setSetterName(methodName);
	            		 }
	            		 boolean isChecked = false;
	            		 for(TreeItem item: parentItem.getItems()) {
	            			 isChecked |= item.getChecked();
	            		 }
	            		 parentItem.setChecked(isChecked);
	            		 refactoring.setSelected(isChecked);
	            	 } else {					// Field
		            	 for (TreeItem item: selectedItem.getItems()) {
		            		 item.setChecked(selectedItem.getChecked());
		            		 ((SelfEncapsulateFieldRefactoring) item.getData(REFACTORING)).setSelected(selectedItem.getChecked());
		            	 }
	            	 }
	             }
	             processValidation();
			}
		});
		fRefactorings.getRefactorings().forEach(refactoring -> {
			boolean isChecked = wizard.getPreselected().contains(refactoring.getField());
        	TreeItem item = new TreeItem(tree, SWT.CHECK);
        	refactoringButtonMap.put(refactoring, item);
			item.setChecked(isChecked);
        	item.setText(refactoring.getField().getElementName());

        	TreeItem generateGetter = new TreeItem(item, SWT.CHECK);
            generateGetter.setChecked(isChecked);
            generateGetter.setText(refactoring.getGetterName());
            enableEditing(generateGetter, tree, refactoring.getGetterName(), RefactoringType.GETTER, refactoring);

            if(needsSetter(refactoring)) {
            	TreeItem generateSetter = new TreeItem(item, SWT.CHECK);
            	generateSetter.setChecked(isChecked);
                generateSetter.setText(refactoring.getSetterName());
                enableEditing(generateSetter, tree, refactoring.getSetterName(), RefactoringType.SETTER, refactoring);
            }
            refactoring.setSelected(isChecked);
		});

		Label separator= new Label(result, SWT.NONE);
		separator.setText("lolol"); //$NON-NLS-1$
		separator.setLayoutData(new GridData(SWT.INHERIT_NONE, SWT.CENTER, true, true, 3, 1));

		createFieldAccessBlock(result);

		Label label= new Label(result, SWT.LEFT);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));
		label.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_insert_after);
		fEnablements.add(label);

		final Combo combo= new Combo(result, SWT.READ_ONLY);
		SWTUtil.setDefaultVisibleItemCount(combo);
		fillWithPossibleInsertPositions(combo);
		combo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				for(SelfEncapsulateFieldRefactoring refactoring: fRefactorings.getRefactorings()) {
					refactoring.setInsertionIndex(combo.getSelectionIndex() - 1);
				}
			}
		});
		combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		fEnablements.add(combo);
		createAccessModifier(result);

		Button checkBox= new Button(result, SWT.CHECK);
		checkBox.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_generateJavadocComment);
		checkBox.setSelection(fRefactorings.getRefactorings().get(0).getGenerateJavadoc());
		checkBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setGenerateJavadoc(((Button)e.widget).getSelection());
			}
		});
		checkBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

		fEnablements.add(checkBox);

		updateEnablements();

		processValidation();

		Dialog.applyDialogFont(result);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.SEF_WIZARD_PAGE);
	}

	private void updateEnablements() {
		boolean enable= !fRefactorings.getRefactorings().get(0).isUsingLocalSetter() || !fRefactorings.getRefactorings().get(0).isUsingLocalGetter();
		for (Control control : fEnablements) {
			control.setEnabled(enable);
		}
	}

	private void loadSettings() {
		fSettings= getDialogSettings().getSection(SelfEncapsulateFieldWizard.DIALOG_SETTING_SECTION);
		if (fSettings == null) {
			fSettings= getDialogSettings().addNewSection(SelfEncapsulateFieldWizard.DIALOG_SETTING_SECTION);
			fSettings.put(GENERATE_JAVADOC, JavaPreferencesSettings.getCodeGenerationSettings(fRefactorings.getRefactorings().get(0).getField().getJavaProject()).createComments);
		}
		fRefactorings.getRefactorings().forEach(refactoring ->
		refactoring.setGenerateJavadoc(fSettings.getBoolean(GENERATE_JAVADOC)));
	}

	private void createAccessModifier(Composite result) {

		Label label= new Label(result, SWT.NONE);
		label.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_access_Modifiers);
		fEnablements.add(label);

		Composite group= new Composite(result, SWT.NONE);
		group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		GridLayout layout= new GridLayout(4, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		group.setLayout(layout);

		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Object[] info= createData();
		String[] labels= (String[])info[0];
		Integer[] data= (Integer[])info[1];
		for (int i= 0; i < labels.length; i++) {
			Button radio= new Button(group, SWT.RADIO);
			radio.setText(labels[i]);
			radio.setData(data[i]);
			int iData= data[i];
			if (iData == Flags.AccPublic)
				radio.setSelection(true);
			radio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					fRefactorings.getRefactorings().forEach(refactoring -> refactoring.setVisibility(((Integer)event.widget.getData())));
				}
			});
			fEnablements.add(radio);
		}
	}

	private void createFieldAccessBlock(Composite result) {
		Label label= new Label(result, SWT.LEFT);
		label.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_field_access);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

		Composite group= new Composite(result, SWT.NONE);
		GridLayout layout= new GridLayout(2, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		group.setLayout(layout);

		group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));

		Button radio= new Button(group, SWT.RADIO);
		radio.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_use_setter_getter);
		radio.setSelection(true);
		radio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fRefactorings.getRefactorings().forEach(refactoring -> refactoring.setEncapsulateDeclaringClass(true));
			}
		});
		radio.setLayoutData(new GridData());

		radio= new Button(group, SWT.RADIO);
		radio.setText(RefactoringMessages.SelfEncapsulateFieldInputPage_keep_references);
		radio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fRefactorings.getRefactorings().forEach(refactoring -> refactoring.setEncapsulateDeclaringClass(true));
			}
		});
		radio.setLayoutData(new GridData());
	}

	private Object[] createData() {
		String pub= RefactoringMessages.SelfEncapsulateFieldInputPage_public;
		String pro= RefactoringMessages.SelfEncapsulateFieldInputPage_protected;
		String def= RefactoringMessages.SelfEncapsulateFieldInputPage_default;
		String priv= RefactoringMessages.SelfEncapsulateFieldInputPage_private;

		String[] labels= new String[] { pub, pro, def, priv };
		Integer[] data= new Integer[] {Flags.AccPublic, Flags.AccProtected, 0, Flags.AccPrivate };
		return new Object[] {labels, data};
	}

	private void fillWithPossibleInsertPositions(Combo combo) {
		int select= 0;
		combo.add(RefactoringMessages.SelfEncapsulateFieldInputPage_first_method);
		try {
			Set<IMethod> methods = new HashSet<>();
			for(SelfEncapsulateFieldRefactoring refactoring: fRefactorings.getRefactorings()) {
				methods.addAll(Arrays.asList(refactoring.getField().getDeclaringType().getMethods()));
			}
			for (IMethod method : methods) {
				combo.add(JavaElementLabels.getElementLabel(method, JavaElementLabels.M_PARAMETER_TYPES));
			}
			if (methods.size() > 0)
				select= methods.size();
		} catch (JavaModelException e) {
			// Fall through
		}
		combo.select(select);
		for(SelfEncapsulateFieldRefactoring refactoring: fRefactorings.getRefactorings()) {
			refactoring.setInsertionIndex(select - 1);
		}
	}

	private void setGenerateJavadoc(boolean value) {
		fSettings.put(GENERATE_JAVADOC, value);
		fRefactorings.getRefactorings().forEach(refactoring -> refactoring.setGenerateJavadoc(value));
	}

	private void processValidation() {
		RefactoringStatus status = new RefactoringStatus();
		fRefactorings.getRefactorings().forEach(refactoring -> status.merge(refactoring.checkMethodNames()));
		String message= null;
		boolean valid= true;
		if (status.hasFatalError()) {
			message= status.getMessageMatchingSeverity(RefactoringStatus.FATAL);
			valid= false;
		}
		if (getSelectedRefactorings().size() == 0) {
			valid = false;
		} else if (getSelectedRefactorings().stream().map(SelfEncapsulateFieldRefactoring::getGetterName).map(String::isEmpty).reduce(false, (acc, value) -> acc || value)) {
			message= RefactoringMessages.SelfEncapsulateFieldInputPage_no_getter_name;
			valid= false;
		} else if (getSelectedRefactorings().stream().map(SelfEncapsulateFieldRefactoring::getSetterName).map(String::isEmpty).reduce(false, (acc, value) -> acc || value)) {
			message= RefactoringMessages.SelfEncapsulateFieldInputPage_no_setter_name;
			valid= false;
		}
		setErrorMessage(message);
		setPageComplete(valid);
	}

	private boolean needsSetter(SelfEncapsulateFieldRefactoring refactoring) {
		try {
			return !JdtFlags.isFinal(refactoring.getField());
		} catch(JavaModelException e) {
			return true;
		}
	}
}
