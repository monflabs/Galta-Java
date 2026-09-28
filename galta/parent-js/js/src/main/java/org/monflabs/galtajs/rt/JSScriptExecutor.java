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
package org.monflabs.galtajs.rt;

import java.nio.charset.Charset;
import java.nio.file.Path;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.util.path.FilesUtil;


/**
 * JS File interpreter.
 */
public class JSScriptExecutor {
	
	private JSEnvironment env;
	private Path root;
	private Charset cs;
	
	// Keep it between requests
	private JSGlobalContext context;

	public JSScriptExecutor(JSEnvironment env) {
		this(env,null,null);
	}
	public JSScriptExecutor(JSEnvironment env, Path root) {
		this(env,root,null);
	}
	public JSScriptExecutor(JSEnvironment env, Path root, Charset cs) {
		this.env = env;
		this.root = root;
		this.cs = cs;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	public Path getRoot() {
		return root;
	}

	public JSGlobalContext getContext() {
		return context;
	}	
	public void clearContext() {
		context = null;
	}
	
	
	//
	// Execute code
	//
	public Object execute(String code) {
		JSScriptUnit unit = createScriptUnit(code,"main");
		if(context==null) {
			context = createContext();
		}
		return unit.executeWithContext(context);
	}
	
	public Object executeFile(String file) {
		if(root==null) {
			throw new JSException(null,"There is no root Path assigned to this executor");
		}
		Path path = root.resolve(file);
		String code = FilesUtil.readString(path,cs);
		JSScriptUnit unit = createScriptUnit(code,file);
		if(context==null) {
			context = createContext();
		}
		return unit.executeWithContext(context);
	}
	
	//
	// Factories
	//
	protected JSGlobalContext createContext() {
		JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(getEnvironment(), createExecutor());
		//ctx.addModuleResolver(new JSPathModuleResolver(root,cs));
		return ctx;
	}
	protected JSExecutor createExecutor() {
		return env.createProgramExecutor();
	}
	protected JSScriptUnit createScriptUnit(String code, String fileName) {
		JSScriptUnit unit = env.createScript(code, fileName);
		return unit;
	}
}
