/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.core;

import java.io.IOException;
import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.codemanipulation.AddUnimplementedConstructorsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

public class AddUnimplementedConstructorsTest extends CoreTests {

	private static final Class THIS= AddUnimplementedConstructorsTest.class;

	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}

	public static Test suite() {
		if (true) {
			return allTests();
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new AddUnimplementedConstructorsTest("testOneConstructorWithImportStatement"));
			return new ProjectTestSetup(suite);
		}
	}

	private IType fClassA, fClassB, fClassC;

	private IJavaProject fJavaProject;

	private IPackageFragment fPackage;

	private CodeGenerationSettings fSettings;

	public AddUnimplementedConstructorsTest(String name) {
		super(name);
	}

	private void checkDefaultConstructorNoCommentWithSuper(String con) throws IOException {
		StringBuffer buf= new StringBuffer();
		buf.append("public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		String constructor= buf.toString();
		compareSource(constructor, con);
	}

	private void checkDefaultConstructorWithCommentWithSuper(String con) throws IOException {
		StringBuffer buf= new StringBuffer();
		buf.append("/** Constructor Comment\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		String constructor= buf.toString();
		compareSource(constructor, con);
	}

	private void checkMethods(String[] expected, IMethod[] methods) {
		int nMethods= methods.length;
		int nExpected= expected.length;
		assertTrue("" + nExpected + " methods expected, is " + nMethods, nMethods == nExpected);
		for (int i= 0; i < nExpected; i++) {
			String methName= expected[i];
			assertTrue("method " + methName + " expected", nameContained(methName, methods));
		}
	}

	private void compareSource(String expected, String actual) throws IOException {
		assertEqualStringIgnoreDelim(actual, expected);
	}

	private AddUnimplementedConstructorsOperation createOperation(IType type) throws CoreException {
		return createOperation(type, new CodeGenerationSettings());
	}

	private AddUnimplementedConstructorsOperation createOperation(IType type, CodeGenerationSettings settings) throws CoreException {
		RefactoringASTParser parser= new RefactoringASTParser(AST.JLS3);
		CompilationUnit unit= parser.parse(type.getCompilationUnit(), true);
		AbstractTypeDeclaration declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(type, unit);
		assertNotNull("Could not find type declararation node", declaration);
		ITypeBinding binding= declaration.resolveBinding();
		assertNotNull("Binding for type declaration could not be resolved", binding);
		IMethodBinding[] bindings= StubUtility2.getVisibleConstructors(binding);
		String[] keys= new String[bindings.length];
		for (int index= 0; index < bindings.length; index++)
			keys[index]= bindings[index].getKey();
		return new AddUnimplementedConstructorsOperation(type, null, keys, settings, true);
	}

	private void initCodeTemplates() {
		Hashtable options= TestOptions.getFormatterOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "999");
		JavaCore.setOptions(options);

		StringBuffer comment= new StringBuffer();
		comment.append("/** Constructor Comment\n");
		comment.append(" * ${tags}\n");
		comment.append(" */");

		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORCOMMENT_ID, comment.toString(), null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORSTUB_ID, "${body_statement}\n// TODO", null);
		fSettings= JavaPreferencesSettings.getCodeGenerationSettings(null);
		fSettings.createComments= true;
	}

	private boolean nameContained(String methName, IJavaElement[] methods) {
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].getElementName().equals(methName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a new test Java project.
	 */
	protected void setUp() {
		initCodeTemplates();
	}

	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fJavaProject);
		fJavaProject= null;
		fPackage= null;
		fClassA= null;
	}

	/*
	 * basic test: test with default constructor only
	 */
	public void testDefaultConstructorToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public class A {\n}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorWithCommentWithSuper(createdMethods[0].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with 8 constructors to override
	 */
	public void testEightConstructorsToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf, char a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {super();}\n", null, true, null);
		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);

		CodeGenerationSettings settings= new CodeGenerationSettings();
		settings.createComments= false;
		AddUnimplementedConstructorsOperation op= createOperation(testClass, settings);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1", "Test1", "Test1", "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorNoCommentWithSuper(createdMethods[0].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n" + "\n" + "    public Test1() {\n" + "        super();\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a) {\n" + "        super(a);\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString) {\n" + "        super(a, boo, fooString);\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString, StringBuffer buf) {\n" + "        super(a, boo, fooString, buf);\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a) {\n" + "        super(a, boo, fooString, buf, a);\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {\n" + "        super(a, boo, fooString, buf, a, c);\n"
				+ "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {\n" + "        super(a, boo, fooString, buf, a, c, d);\n" + "        // TODO\n" + "    }\n" + "\n" + "    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {\n" + "        super(a, boo, fooString, buf, a, c, d, secondString);\n" + "        // TODO\n" + "    }\n" + "}");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with 5 constructors to override. Class C extends B extends Class
	 * A.
	 */
	public void testFiveConstructorsToOverrideWithTwoLevelsOfInheritance() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);

		ICompilationUnit cuB= fPackage.getCompilationUnit("B.java");
		fClassB= cuB.createType("public class B extends A{\n}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {super();}\n", null, true, null);

		ICompilationUnit cuC= fPackage.getCompilationUnit("C.java");
		fClassC= cuC.createType("public class C extends B{\n}\n", null, true, null);
		fClassC.createMethod("public C(int a, boolean boo, String fooString, StringBuffer buf, char a) {super();}\n", null, true, null);
		fClassC.createMethod("public C(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {super();}\n", null, true, null);
		fClassC.createMethod("public C(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {super();}\n", null, true, null);
		fClassC.createMethod("public C(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {super();}\n", null, true, null);
		fClassC.createMethod("public C(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString, int xxx) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends C {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends C {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a) {\n");
		buf.append("        super(a, boo, fooString, buf, a);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     * @param d\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c, d);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     * @param d\n");
		buf.append("     * @param secondString\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c, d, secondString);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     * @param d\n");
		buf.append("     * @param secondString\n");
		buf.append("     * @param xxx\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString, int xxx) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c, d, secondString, xxx);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with 4 constructors to override. Class B extends Class A.
	 */
	public void testFourConstructorsToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);

		ICompilationUnit cuB= fPackage.getCompilationUnit("B.java");
		fClassB= cuB.createType("public class B extends A{\n}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {super();}\n", null, true, null);
		fClassB.createMethod("public B(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends B {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends B {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a) {\n");
		buf.append("        super(a, boo, fooString, buf, a);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     * @param d\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c, d);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     * @param a\n");
		buf.append("     * @param c\n");
		buf.append("     * @param d\n");
		buf.append("     * @param secondString\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf, char a, double c, float d, String secondString) {\n");
		buf.append("        super(a, boo, fooString, buf, a, c, d, secondString);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");
		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * found 4 with 5 constructors, 1 already overriden
	 */
	public void testFourConstructorsToOverrideWithOneExistingConstructor() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, int bologna) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1(int a, boolean boo, String fooString) {super();}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n" + 
				"\n" + 
				"    public Test1(int a, boolean boo, String fooString) {super();}\n" + 
				"\n" + 
				"    /** Constructor Comment\n" + 
				"     * \n" + 
				"     */\n" + 
				"    public Test1() {\n" + 
				"        super();\n" + 
				"        // TODO\n" + 
				"    }\n" + 
				"\n" + 
				"    /** Constructor Comment\n" + 
				"     * @param a\n" + 
				"     */\n" + 
				"    public Test1(int a) {\n" + 
				"        super(a);\n" + 
				"        // TODO\n" + 
				"    }\n" + 
				"\n" + 
				"    /** Constructor Comment\n" + 
				"     * @param a\n" + 
				"     * @param boo\n" + 
				"     * @param fooString\n" + 
				"     * @param bologna\n" + 
				"     */\n" + 
				"    public Test1(int a, boolean boo, String fooString, int bologna) {\n" + 
				"        super(a, boo, fooString, bologna);\n" + 
				"        // TODO\n" + 
				"    }\n" + 
				"\n" + 
				"    /** Constructor Comment\n" + 
				"     * @param a\n" + 
				"     * @param boo\n" + 
				"     * @param fooString\n" + 
				"     * @param buf\n" + 
				"     */\n" + 
				"    public Test1(int a, boolean boo, String fooString, StringBuffer buf) {\n" + 
				"        super(a, boo, fooString, buf);\n" + 
				"        // TODO\n" + 
				"    }\n" + 
				"}");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with nothing to override
	 */
	public void testNoConstructorsToOverrideAvailable() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public class A {\n}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1(){}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] existingMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1"}, existingMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    public Test1(){}\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test an Interface to make sure no exception is thrown
	 */
	public void testNoConstructorsToOverrideWithInterface() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public interface A {\n}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 implements A {\n}\n", null, true, null);
		testClass.createMethod("public Test1(){}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] existingMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1"}, existingMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 implements A {\n");
		buf.append("\n");
		buf.append("    public Test1(){}\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * nothing found with default constructor
	 */
	public void testNoConstructorsToOverrideWithOneExistingConstructors() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1() {\nsuper();}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(true);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    public Test1() {\n");
		buf.append("    super();}\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * nothing found with 3 constructors
	 */
	public void testNoConstructorsToOverrideWithThreeExistingConstructors() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1() {\nsuper();}\n", null, true, null);
		testClass.createMethod("public Test1(int a) {super();}\n", null, true, null);
		testClass.createMethod("public Test1(int a, boolean boo, String fooString) {super();}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(true);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] existingMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1"}, existingMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    public Test1() {\n");
		buf.append("    super();}\n");
		buf.append("\n");
		buf.append("    public Test1(int a) {super();}\n");
		buf.append("\n");
		buf.append("    public Test1(int a, boolean boo, String fooString) {super();}\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with one constructor
	 */
	public void testOneConstructorToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.createCompilationUnit("A.java", "package ibm.util;\n\n", true, null);
		fClassA= cu.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java"); //$NON-NLS-1$
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null); //$NON-NLS-1$

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorWithCommentWithSuper(createdMethods[0].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");
		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * found one with constructor which isn't default or the same as existing
	 */
	public void testOneConstructorToOverrideNotDefault() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1(int a, boolean boo, String fooString) {super();}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();

		checkMethods(new String[] { "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    public Test1(int a, boolean boo, String fooString) {super();}\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     * @param boo\n");
		buf.append("     * @param fooString\n");
		buf.append("     * @param buf\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf) {\n");
		buf.append("        super(a, boo, fooString, buf);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * found one with constructor needs import statement
	 */
	public void testOneConstructorWithImportStatement() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util.bogus", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A(java.util.Vector v, int a) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		String fullSource= testClass.getCompilationUnit().getSource();

		checkMethods(new String[] { "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		StringBuffer buf= new StringBuffer();
		buf.append("package ibm.util.bogus;\n");
		buf.append("\n");
		buf.append("import java.util.Vector;\n");
		buf.append("\n");
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param v\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public Test1(Vector v, int a) {\n");
		buf.append("        super(v, a);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");
		buf.append("\n");

		compareSource(buf.toString(), fullSource);
	}

	/*
	 * basic test: test with 3 constructors to override
	 */
	public void testThreeConstructorsToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorWithCommentWithSuper(createdMethods[0].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n" + "\n" + "    /** Constructor Comment\n" + "     * \n" + "     */\n" + "    public Test1() {\n" + "        super();\n" + "        // TODO\n" + "    }\n" + "\n" + "    /** Constructor Comment\n" + "     * @param a\n" + "     */\n" + "    public Test1(int a) {\n" + "        super(a);\n" + "        // TODO\n" + "    }\n" + "\n" + "    /** Constructor Comment\n" + "     * @param a\n" + "     * @param boo\n" + "     */\n" + "    public Test1(int a, boolean boo) {\n" + "        super(a, boo);\n" + "        // TODO\n" + "    }\n" + "}");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * basic test: test with 2 constructors to override
	 */
	public void testTwoConstructorsToOverride() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cu= fPackage.getCompilationUnit("A.java");
		fClassA= cu.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);

		AddUnimplementedConstructorsOperation op= createOperation(testClass);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorWithCommentWithSuper(createdMethods[0].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /** Constructor Comment\n");
		buf.append("     * @param a\n");
		buf.append("     */\n");
		buf.append("    public Test1(int a) {\n");
		buf.append("        super(a);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}

	/*
	 * found 2 with 5 constructors, 3 already overriden
	 */
	public void testTwoConstructorsToOverrideWithThreeExistingConstructors() throws Exception {
		fJavaProject= JavaProjectHelper.createJavaProject("DummyProject", "bin");
		assertNotNull(JavaProjectHelper.addRTJar(fJavaProject));

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJavaProject, "src");
		fPackage= root.createPackageFragment("ibm.util", true, null);

		ICompilationUnit cuA= fPackage.getCompilationUnit("A.java");
		fClassA= cuA.createType("public class A {\n}\n", null, true, null);
		fClassA.createMethod("public A() {\nsuper();}\n", null, true, null);
		fClassA.createMethod("public A(int a) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, int bologna) {super();}\n", null, true, null);
		fClassA.createMethod("public A(int a, boolean boo, String fooString, StringBuffer buf) {super();}\n", null, true, null);

		ICompilationUnit cu2= fPackage.getCompilationUnit("Test1.java");
		IType testClass= cu2.createType("public class Test1 extends A {\n}\n", null, true, null);
		testClass.createMethod("public Test1(int a, boolean boo, String fooString, StringBuffer buf) {\nsuper();}\n", null, true, null);
		testClass.createMethod("public Test1(int a) {super();}\n", null, true, null);
		testClass.createMethod("public Test1(int a, boolean boo, String fooString) {super();}\n", null, true, null);

		CodeGenerationSettings settings= new CodeGenerationSettings();
		settings.createComments= false;
		AddUnimplementedConstructorsOperation op= createOperation(testClass, settings);

		op.setOmitSuper(false);
		op.setVisibility(Modifier.PUBLIC);
		op.run(new NullProgressMonitor());
		JavaModelUtil.reconcile(testClass.getCompilationUnit());

		IMethod[] createdMethods= testClass.getMethods();
		checkMethods(new String[] { "Test1", "Test1", "Test1", "Test1", "Test1"}, createdMethods); //$NON-NLS-1$ //$NON-NLS-2$

		checkDefaultConstructorNoCommentWithSuper(createdMethods[3].getSource());

		StringBuffer buf= new StringBuffer();
		buf.append("public class Test1 extends A {\n");
		buf.append("\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, StringBuffer buf) {\n");
		buf.append("    super();}\n");
		buf.append("\n");
		buf.append("    public Test1(int a) {super();}\n");
		buf.append("\n");
		buf.append("    public Test1(int a, boolean boo, String fooString) {super();}\n");
		buf.append("\n");
		buf.append("    public Test1() {\n");
		buf.append("        super();\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public Test1(int a, boolean boo, String fooString, int bologna) {\n");
		buf.append("        super(a, boo, fooString, bologna);\n");
		buf.append("        // TODO\n");
		buf.append("    }\n");
		buf.append("}\n");

		compareSource(buf.toString(), testClass.getSource());
	}
}
