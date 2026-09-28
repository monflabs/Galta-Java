/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.modules;

import java.lang.reflect.Constructor;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;

/**
 * 
 */
public class JSTranspiledModuleResolver extends ScriptModuleResolver {
	
	private class Descriptor extends NativeModuleDescriptor {
		Class<? extends JSTranspiledUnit> clazz;
		private Descriptor(String name, Class<? extends JSTranspiledUnit> clazz) {
			super(name);
			this.clazz = clazz;
		}
		@Override
		public JSModule loadModule(JSGlobalContext globalContext) {
			try {
				String moduleName = getName();
				Constructor<?> ctor = clazz.getConstructor(JSEnvironment.class,String.class);
				JSTranspiledUnit script = (JSTranspiledUnit)ctor.newInstance(globalContext.getEnvironment(),moduleName);
				initModule(globalContext,script);
				return script;
			} catch(Exception e) {
				// The module exists (its class was found): a failure is a load error,
				// not a "module not found"
				throw ModuleUtil.loadError(e, getName());
			}
		}
		// Source-phase import of a precompiled module: its retained source
		// text when the generated class carries one (JSTranspiledUnit.
		// getFullSourceCode()), otherwise the native-module placeholder.
		// Instantiating the unit does not run its body (initModule() does).
		@Override
		public String getModuleSourceText() {
			try {
				Constructor<?> ctor = clazz.getConstructor(JSEnvironment.class,String.class);
				JSTranspiledUnit unit = (JSTranspiledUnit)ctor.newInstance(env,getName());
				String source = unit.getFullSourceCode();
				if(source!=null) {
					return source;
				}
			} catch(Exception e) {
				// Fall through: treated as a native module.
			}
			return super.getModuleSourceText();
		}
	}

	private JSEnvironment env;
	private ClassLoader classLoader;
	private String basePackage;

	public JSTranspiledModuleResolver(JSEnvironment env, ClassLoader classLoader, String basePackage) {
		this.env = env;
		this.classLoader = classLoader;
		this.basePackage = basePackage;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	protected String moduleNameToJavaClassName(String basePackage, String moduleName) {
		return JSTranspiler.moduleNameToJavaClassName(basePackage, moduleName);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Class<? extends JSTranspiledUnit> loadClass(JSEnvironment env, String className) throws ClassNotFoundException {
		return (Class)getClassLoader().loadClass(className);
	}
	
	@Override
	protected JSModuleDescriptor findModule(String name) {
		try {
			String className = moduleNameToJavaClassName(basePackage, name);
			Class<? extends JSTranspiledUnit> clazz = loadClass(env,className);
			return new Descriptor(name, clazz);
		} catch(Exception e) {
			return null;
		}
	}
	
	@Override
	public Stream<JSModuleDescriptor> getModules() {
		return Stream.empty();
	}	
}
