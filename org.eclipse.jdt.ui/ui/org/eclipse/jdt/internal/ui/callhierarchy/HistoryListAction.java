/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation 
 *          (report 36180: Callers/Callees view)
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IMethod;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class HistoryListAction extends Action {
	
	private class HistoryListDialog extends StatusDialog {
		
		private ListDialogField fHistoryList;
		private IStatus fHistoryStatus;
		private IMethod fResult;
		
		private HistoryListDialog(Shell shell, IMethod[] elements) {
			super(shell);
			setTitle(CallHierarchyMessages.getString("HistoryListDialog.title")); //$NON-NLS-1$
			
			String[] buttonLabels= new String[] { 
				/* 0 */ CallHierarchyMessages.getString("HistoryListDialog.remove.button"), //$NON-NLS-1$
			};
					
			IListAdapter adapter= new IListAdapter() {
				public void customButtonPressed(ListDialogField field, int index) {
					doCustomButtonPressed();
				}
				public void selectionChanged(ListDialogField field) {
					doSelectionChanged();
				}
				
				public void doubleClicked(ListDialogField field) {
					doDoubleClicked();
				}				
			};
		
			JavaElementLabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_QUALIFIED | JavaElementLabelProvider.SHOW_ROOT);
			
			fHistoryList= new ListDialogField(adapter, buttonLabels, labelProvider);
			fHistoryList.setLabelText(CallHierarchyMessages.getString("HistoryListDialog.label")); //$NON-NLS-1$
			fHistoryList.setElements(Arrays.asList(elements));
			
			ISelection sel;
			if (elements.length > 0) {
				sel= new StructuredSelection(elements[0]);
			} else {
				sel= new StructuredSelection();
			}
			
			fHistoryList.selectElements(sel);
		}

			
		/*
		 * @see Dialog#createDialogArea(Composite)
		 */
		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);
			
			Composite composite= (Composite) super.createDialogArea(parent);
			
			Composite inner= new Composite(composite, SWT.NONE);
			inner.setLayoutData(new GridData(GridData.FILL_BOTH));

			LayoutUtil.doDefaultLayout(inner, new DialogField[] { fHistoryList }, true, 0, 0);
			LayoutUtil.setHeightHint(fHistoryList.getListControl(null), convertHeightInCharsToPixels(12));
			LayoutUtil.setHorizontalGrabbing(fHistoryList.getListControl(null));

			applyDialogFont(composite);		
			return composite;
		}

		/**
		 * Method doCustomButtonPressed.
		 */
		private void doCustomButtonPressed() {
			fHistoryList.removeElements(fHistoryList.getSelectedElements());
		}
		
		private void doDoubleClicked() {
			if (fHistoryStatus.isOK()) {
				okPressed();
			}
		}
		
		
		private void doSelectionChanged() {
			StatusInfo status= new StatusInfo();
			List selected= fHistoryList.getSelectedElements();
			if (selected.size() != 1) {
				status.setError(""); //$NON-NLS-1$
				fResult= null;
			} else {
				fResult= (IMethod) selected.get(0);
			}
			fHistoryList.enableButton(0, fHistoryList.getSize() > selected.size());			
			fHistoryStatus= status;
			updateStatus(status);	
		}
				
		public IMethod getResult() {
			return fResult;
		}
		
		public IMethod[] getRemaining() {
			List elems= fHistoryList.getElements();
			return (IMethod[]) elems.toArray(new IMethod[elems.size()]);
		}	
		
		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			WorkbenchHelp.setHelp(newShell, IJavaHelpContextIds.HISTORY_LIST_DIALOG);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.window.Window#create()
		 */
		public void create() {
			setShellStyle(getShellStyle() | SWT.RESIZE);
			super.create();
		}

	}
	
	private CallHierarchyViewPart fView;
	
	public HistoryListAction(CallHierarchyViewPart view) {
		fView= view;
		setText(CallHierarchyMessages.getString("HistoryListAction.label")); //$NON-NLS-1$
		JavaPluginImages.setLocalImageDescriptors(this, "history_list.gif"); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.HISTORY_LIST_ACTION);
	}
		
	/*
	 * @see IAction#run()
	 */
	public void run() {
		IMethod[] historyEntries= fView.getHistoryEntries();
		HistoryListDialog dialog= new HistoryListDialog(JavaPlugin.getActiveWorkbenchShell(), historyEntries);
		if (dialog.open() == Window.OK) {
			fView.setHistoryEntries(dialog.getRemaining());
			fView.setMethod(dialog.getResult());
		}
	}

}

