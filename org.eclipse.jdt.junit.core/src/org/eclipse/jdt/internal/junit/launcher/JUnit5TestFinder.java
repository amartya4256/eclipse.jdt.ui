/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.launcher;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.JUnitMessages;
import org.eclipse.jdt.internal.junit.util.CoreTestSearchEngine;

public class JUnit5TestFinder implements ITestFinder {

	private static class Annotation {

		private static final Annotation RUN_WITH= new Annotation("org.junit.runner.RunWith"); //$NON-NLS-1$

		private static final Annotation TEST_4= new Annotation("org.junit.Test"); //$NON-NLS-1$

		private static final Annotation TESTABLE= new Annotation(JUnitCorePlugin.JUNIT5_TESTABLE_ANNOTATION_NAME);

		private final String fName;

		private Annotation(String name) {
			fName= name;
		}

		public String getName() {
			return fName;
		}

		public boolean annotatesTypeOrSuperTypes(ITypeBinding type) {
			while (type != null) {
				if (annotates(type.getAnnotations())) {
					return true;
				}
				type= type.getSuperclass();
			}
			return false;
		}

		public boolean annotatesAtLeastOneMethod(ITypeBinding type) {
			ITypeBinding curr= type;
			while (curr != null) {
				if (annotatesDeclaredMethods(curr)) {
					return true;
				}
				curr= curr.getSuperclass();
			}

			curr= type;
			while (curr != null) {
				ITypeBinding[] superInterfaces= curr.getInterfaces();
				for (int i= 0; i < superInterfaces.length; i++) {
					if (annotatesMethodsInInterface(superInterfaces[i])) {
						return true;
					}
				}
				curr= curr.getSuperclass();
			}

			return false;
		}

		private boolean annotatesMethodsInInterface(ITypeBinding type) {
			if (annotatesDeclaredMethods(type)) {
				return true;
			}
			ITypeBinding[] superInterfaces= type.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++) {
				if (annotatesMethodsInInterface(superInterfaces[i])) {
					return true;
				}
			}
			return false;
		}

		private boolean annotatesDeclaredMethods(ITypeBinding type) {
			IMethodBinding[] declaredMethods= type.getDeclaredMethods();
			for (int i= 0; i < declaredMethods.length; i++) {
				IMethodBinding curr= declaredMethods[i];
				if (annotates(curr.getAnnotations())) {
					return true;
				}
			}
			return false;
		}

		private boolean annotates(IAnnotationBinding[] annotations) {
			for (IAnnotationBinding annotation : annotations) {
				if (annotation == null) {
					return false;
				}
				if (matchesAnnotationName(annotation.getAnnotationType())) {
					return true;
				}
				if (matchesAnnotationNameInHierarchy(annotation)) {
					return true;
				}
			}
			return false;
		}

		private boolean matchesAnnotationName(ITypeBinding annotationType) {
			if (annotationType != null) {
				String qualifiedName= annotationType.getQualifiedName();
				if (qualifiedName.equals(fName)) {
					return true;
				}
			}
			return false;
		}

		private boolean matchesAnnotationNameInHierarchy(IAnnotationBinding annotation) {
			if (!TESTABLE.getName().equals(fName)) {
				return false;
			}

			Set<ITypeBinding> hierarchy= new HashSet<>();
			collectAnnotationsInHierarchy(annotation, hierarchy);

			for (ITypeBinding type : hierarchy) {
				if (matchesAnnotationName(type)) {
					return true;
				}
			}
			return false;
		}

		private void collectAnnotationsInHierarchy(IAnnotationBinding annotation, Set<ITypeBinding> hierarchy) {
			ITypeBinding type= annotation.getAnnotationType();
			if (type != null) {
				for (IAnnotationBinding annotationBinding : type.getAnnotations()) {
					if (annotationBinding != null) {
						ITypeBinding annotationType= annotationBinding.getAnnotationType();
						if (annotationType != null && hierarchy.add(annotationType)) {
							collectAnnotationsInHierarchy(annotationBinding, hierarchy);
						}
					}
				}
			}
		}

	}

	@Override
	public void findTestsInContainer(IJavaElement element, Set<IType> result, IProgressMonitor pm) throws CoreException {
		if (element == null || result == null) {
			throw new IllegalArgumentException();
		}

		if (element instanceof IType) {
			IType type= (IType) element;
			if (internalIsTest(type, pm)) {
				result.add(type);
				return;
			}
		}

		if (pm == null)
			pm= new NullProgressMonitor();

		try {
			pm.beginTask(JUnitMessages.JUnit5TestFinder_searching_description, 4);

			IRegion region= CoreTestSearchEngine.getRegion(element);
			ITypeHierarchy hierarchy= JavaCore.newTypeHierarchy(region, null, new SubProgressMonitor(pm, 1));
			IType[] allClasses= hierarchy.getAllClasses();

			// search for all types with references to RunWith and Test and all subclasses
			for (IType type : allClasses) {
				if (internalIsTest(type, pm) && region.contains(type)) {
					addTypeAndSubtypes(type, result, hierarchy);
				}
			}

			// add all classes implementing JUnit 3.8's Test interface in the region
			IType testInterface= element.getJavaProject().findType(JUnitCorePlugin.TEST_INTERFACE_NAME);
			if (testInterface != null) {
				CoreTestSearchEngine.findTestImplementorClasses(hierarchy, testInterface, region, result);
			}

			//JUnit 4.3 can also run JUnit-3.8-style public static Test suite() methods:
			CoreTestSearchEngine.findSuiteMethods(element, result, new SubProgressMonitor(pm, 1));
		} finally {
			pm.done();
		}
	}

	private void addTypeAndSubtypes(IType type, Set<IType> result, ITypeHierarchy hierarchy) {
		if (result.add(type)) {
			IType[] subclasses= hierarchy.getSubclasses(type);
			for (int i= 0; i < subclasses.length; i++) {
				addTypeAndSubtypes(subclasses[i], result, hierarchy);
			}
		}
	}

	@Override
	public boolean isTest(IType type) throws JavaModelException {
		return internalIsTest(type, null);
	}

	private boolean internalIsTest(IType type, IProgressMonitor monitor) throws JavaModelException {
		if (CoreTestSearchEngine.isAccessibleClass(type)) {
			if (CoreTestSearchEngine.hasSuiteMethod(type)) { // since JUnit 4.3.1
				return true;
			}
			ASTParser parser= ASTParser.newParser(AST.JLS8);
			if (type.getCompilationUnit() != null) {
				parser.setSource(type.getCompilationUnit());
			} else if (!isAvailable(type.getSourceRange())) { // class file with no source
				parser.setProject(type.getJavaProject());
				IBinding[] bindings= parser.createBindings(new IJavaElement[] { type }, monitor);
				if (bindings.length == 1 && bindings[0] instanceof ITypeBinding) {
					ITypeBinding binding= (ITypeBinding) bindings[0];
					return isTest(binding);
				}
				return false;
			} else {
				parser.setSource(type.getClassFile());
			}
			parser.setFocalPosition(0);
			parser.setResolveBindings(true);
			CompilationUnit root= (CompilationUnit) parser.createAST(monitor);
			ASTNode node= root.findDeclaringNode(type.getKey());
			if (node instanceof TypeDeclaration) {
				ITypeBinding binding= ((TypeDeclaration) node).resolveBinding();
				if (binding != null) {
					return isTest(binding);
				}
			}
		}
		return false;

	}

	private static boolean isAvailable(ISourceRange range) {
		return range != null && range.getOffset() != -1;
	}


	private boolean isTest(ITypeBinding binding) {
		if (Modifier.isAbstract(binding.getModifiers()))
			return false;

		if (Annotation.RUN_WITH.annotatesTypeOrSuperTypes(binding)
				|| Annotation.TEST_4.annotatesAtLeastOneMethod(binding)
				|| Annotation.TESTABLE.annotatesAtLeastOneMethod(binding)
				|| Annotation.TESTABLE.annotatesTypeOrSuperTypes(binding)) {
			return true;
		}
		return CoreTestSearchEngine.isTestImplementor(binding);
	}
}
