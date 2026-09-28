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
package org.monflabs.galtajs.node.control;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;




/**
 * Base class for for loops.
 */
public abstract class ASTFor_ extends ASTVarContainer implements ILabeledNode {
	
	public enum Scope {
		Global,
		Local,
		Scoped
	}

	private String label;
	private Scope scope;

	public ASTFor_(Token t) {
		super(t);
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public void setLabel(String label) {
		this.label = label;
	}
	
	public Scope getScope() {
		return scope;
	}
	
	public void transpileForAssignmentStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, ASTNode node, String assignValue) {
		String s = node.transpileJavaAssignment(jsContext,ASSIGN_TYPE.EQUALS,assignValue,false,false);
		b.print(s);
		b.println(";");
	}

}