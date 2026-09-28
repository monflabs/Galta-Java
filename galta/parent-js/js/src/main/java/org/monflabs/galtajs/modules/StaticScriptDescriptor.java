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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.rt.JSGlobalContext;

public class StaticScriptDescriptor implements JSModuleDescriptor {
	
	private String name;
	private ASTProgram program;
	
	public StaticScriptDescriptor(String name, ASTProgram program) {
		this.name = name;
		this.program = program;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	public ASTProgram getProgram() {
		return program;
	}
	
	@Override
	public boolean isScript() {
		return true;
	}
	
	@Override
	public String getScript() {
		return program.getText();
	}

	@Override
	public JSInterpretedUnit loadScript(JSEnvironment env) {
		return new JSInterpretedUnit(env,program,this);
	}

	@Override
	public JSModule loadModule(JSGlobalContext globalContext) {
		throw new IllegalStateException("Unit is a script and cannot be loaded as a module");
	}
}