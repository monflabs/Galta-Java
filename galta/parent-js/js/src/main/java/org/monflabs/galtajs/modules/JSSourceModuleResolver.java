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
import java.util.function.Consumer;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.javacompiler.TargetFactory;
import org.monflabs.javacompiler.factory.MapSourceFactory;
import org.monflabs.util.StringUtil;


/**
 * Java module resolver based on source code
 * 
 * Can interpret the JS file or compile it on the fly, depending on how it was initialized.
 *  
 */
public abstract class JSSourceModuleResolver extends ScriptModuleResolver {
	
	private static final String JS_PACKAGE_NAME = "js";
	
	protected abstract class BaseDescriptor implements JSModuleDescriptor {
		
		private String name;
		
		protected BaseDescriptor(String name) {
			this.name = name;
		}
	
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public JSModule loadModule(JSGlobalContext globalContext) {
			return loadModule(globalContext, null);
		}
		@Override
		public JSModule loadModule(JSGlobalContext globalContext, Consumer<JSModule> earlyRegister) {
			if(transpiler) {
				// Transpiled loading still constructs-then-immediately-runs
				// with no early-registration hook - a self-/circular-import
				// through this path still hits the same crash this fix
				// otherwise addresses. Not attempted here; see KnownGaps.md.
				return loadTranspiledModule(globalContext);
			} else {
				return loadInterpretedModule(globalContext, earlyRegister);
			}
		}
		protected JSModule loadInterpretedModule(JSGlobalContext globalContext, Consumer<JSModule> earlyRegister) {
			// See if we want to cache the scripts per module name?
			String jsSourceCode = getScript();
			if(jsSourceCode!=null) {
				JSEnvironment env = globalContext.getEnvironment();
				int flags = 0;
				if(isCommonJS()) {
					flags |= JSEnvironment.SCRIPT_COMMONJS;
				} else {
					flags |= JSEnvironment.SCRIPT_MODULE;
				}
				JSInterpretedUnit script = env.createScript(jsSourceCode,getName(),flags);
				if(script.getProgram().isCommonJS()!=isCommonJS()) {
					throw new IllegalStateException("Invalid CommonJS flag");
				}
				// The module object exists now, but its own top-level body
				// hasn't run yet - register it in the caller's cache BEFORE
				// initModule() below, so a self-/circular-import reached
				// from WITHIN that body finds this same instance already
				// cached instead of recursing into loading a second one
				// from scratch (previously a hard crash - see
				// JSModuleDescriptor.loadModule()'s own doc comment).
				if(earlyRegister!=null) {
					earlyRegister.accept(script);
				}
				initModule(globalContext,script);
				return script;
			}
			return null;
		}
		protected JSModule loadTranspiledModule(JSGlobalContext globalContext) {
			try {
				JSEnvironment env = globalContext.getEnvironment();
				FactoryClassLoader classLoader = getFactoryClassLoader();
				String moduleName = getName();
				String className = JSTranspiler.moduleNameToJavaClassName(JS_PACKAGE_NAME, moduleName);
				Class<?> clazz = classLoader.alreadyLoaded(className);
				if(clazz==null) {
					String jsSourceCode = getScript();
					if(jsSourceCode!=null) {
						int flags = 0;
						if(isCommonJS()) {
							flags |= JSEnvironment.SCRIPT_COMMONJS;
						}
						JSInterpretedUnit script = env.createScript(jsSourceCode,moduleName,flags);
						JSTranspiler transpiler = new JSTranspiler(env,options);
						String javaCode = transpiler.compileResult(className,"Object",script.getProgram(),moduleName).getJavaCode();
			            String javaFileName = StringUtil.replaceAll(className,'.','/')+".java";

						try(JavaCompiler javac = JavaCompilerFactory.newBuilder()
								.classLoader(classLoader)
								.sourceFactory(MapSourceFactory.of(javaFileName,javaCode))
								.targetFactory(targetFactory)
								.failOnWarnings(true)
								.build()) {
							javac.compile(className);
						}
					}
					clazz=classLoader.loadClass(className);
				}

				Constructor<?> ctor = clazz.getConstructor(JSEnvironment.class,String.class);
				JSTranspiledUnit script = (JSTranspiledUnit)ctor.newInstance(env,moduleName);
				if(script.isCommonJS()!=isCommonJS()) {
					throw new IllegalStateException("Invalid CommonJS flag");
				}
				initModule(globalContext,script);
				return script;
			} catch(ClassNotFoundException e) {
				// Neither a source nor a compiled class: not found
				return null;
			} catch(Exception e) {
				// Compilation errors, errors thrown by the module body...: not a "not found"
				throw ModuleUtil.loadError(e, getName());
			}
		}
	}

	private boolean transpiler;
	
	private JSTranspilerOptions options;
	private ClassLoader baseClassloader;
	private TargetFactory targetFactory;
	
	private FactoryClassLoader classLoader;
	
	public JSSourceModuleResolver() {
	}
	
	public void initTranspiler(JSTranspilerOptions options, ClassLoader baseClassloader, TargetFactory targetFactory) {
		this.transpiler = true;
		this.options = options;
		this.baseClassloader = baseClassloader;
		this.targetFactory = targetFactory;
	}
	
	protected FactoryClassLoader getFactoryClassLoader() {
		if(classLoader==null) {
			classLoader = new FactoryClassLoader(baseClassloader, targetFactory);
		}
		return classLoader;
	}
}
