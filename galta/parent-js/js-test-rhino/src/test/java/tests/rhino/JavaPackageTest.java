package tests.rhino;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.java.JavaClass;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.test.rhino.JavaPackageLibrary.RhinoJavaPackage;
import org.monflabs.galtajs.test.rhino.RhinoTestEnvironment;


public class JavaPackageTest extends RhinoTestCase {

	public void testJavaPackage() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();

    	JSInterpretedUnit sc1 = env.createScript("var v = java; v;","Java Package");
    	RhinoJavaPackage p1 = (RhinoJavaPackage)sc1.execute();
    	assertEquals("java", p1.getPackageName());

    	JSInterpretedUnit sc2 = env.createScript("var v = java.lang; v;","Java Package");
    	RhinoJavaPackage p2 = (RhinoJavaPackage)sc2.execute();
    	assertEquals("java.lang", p2.getPackageName());

    	JSInterpretedUnit sc3 = env.createScript("var v = java.lang.Integer; v;","Java Package");
    	JavaClass p3 = (JavaClass)sc3.execute();
    	assertEquals(Integer.class, p3.getNativeClass());
	}
	
	public void testJavaConstructor() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();

    	JSInterpretedUnit sc1 = env.createScript("var v = new java.lang.Integer(44); v;","Java Package");
    	int p1 = (Integer)sc1.execute();
    	assertEquals(44, p1);

    	JSInterpretedUnit sc2 = env.createScript("var v = new Packages.java.lang.Integer(55); v;","Java Package");
    	int p2 = (Integer)sc2.execute();
    	assertEquals(55, p2);
	}

	public void testStaticJavaClass() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();

    	JSInterpretedUnit sc1 = env.createScript("var v = java.lang.Integer.toString(44); v;","Java Package");
    	String p1 = (String)sc1.execute();
    	assertEquals("44", p1);
	}
	
	public void testReflect() throws Exception {
		JSEnvironment env = RhinoTestEnvironment.create();

        JSInterpretedUnit sc1 = env.createScript("java.lang.reflect.Array.newInstance(java.lang.Integer, 3)","Java Package");
    	Object p1 = sc1.execute();
    	assertTrue(p1 instanceof Integer[]);
    	assertEquals(3, ((Integer[])p1).length);
	}
}
