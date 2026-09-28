package org.monflabs.galtajs.test.rhino;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.rhino.RhinoLibrary;
import org.monflabs.galtajs.rt.builtins.AccessorFactory;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.util.StringUtil;

/**
 * Java Package library. 
 */
public class JavaPackageLibrary extends RhinoLibrary {
	
	public static final String PACKAGES = "Packages";
	public static final String JAVA_PACKAGE = "java";
	
	public JavaPackageLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnProperty(PACKAGES, new RhinoJavaPackage(env,""));
		standardObjects.setOwnProperty(JAVA_PACKAGE, new RhinoJavaPackage(env,JAVA_PACKAGE));
	}
	
	public class RhinoJavaPackage implements AccessorFactory {
		private JSEnvironment env;
		private String packageName;
		RhinoJavaPackage(JSEnvironment env, String packageName) {
			this.env = env;
			this.packageName = packageName;
		}
		public String getPackageName() {
			return packageName;
		}
		@Override
		public JSAccessor createAccessor(JSEnvironment env) {
			return new RhinoJavaPackageAccessor(env);
		}
		public Object getMember(String member) {
			String name = StringUtil.isNotEmpty(packageName) ? packageName+"."+member : member;
			try {
				Class<?> c = env.getClassLoader().loadClass(name);
				return env.getJavaLibrary().getJavaClass(c);
			} catch(ClassNotFoundException e) {
				return new RhinoJavaPackage(env,name);
			}
		}
	}
	public static class RhinoJavaPackageAccessor extends JSAccessor {
		
		public RhinoJavaPackageAccessor(JSEnvironment env) {
			super(env);
		}
		@Override
		public String getClassName(Object _this) {
			return "JavaPackage";
		}	
		@Override
		public Object getOwnProperty(Object _this, String member, Object defaultValue, Object receiver) {
			RhinoJavaPackage pack = (RhinoJavaPackage)_this;
			return pack.getMember(member);
		}
		@Override
		public PropertyDescriptor getOwnPropertyDescriptor(Object _this, String member) {
			return PropertyDescriptor.DESC_DEFAULT;
		}
	}
}