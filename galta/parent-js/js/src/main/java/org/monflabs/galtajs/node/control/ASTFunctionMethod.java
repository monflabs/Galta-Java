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

import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.parser.Token;


/**
 * Method declaration.
 */
public class ASTFunctionMethod extends ASTFunctionDecl {

	private static String functionName(Object functionName) {
		if(functionName instanceof String name) {
			return name;
		}
		return "";
	}
	
	public ASTFunctionMethod(Token t, Object functionName, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		super(t,functionName(functionName),parameters,nodes);
	}

	// A class's own "constructor" method never reaches this as a plain BuiltinFunction -
	// ASTClass extracts it to build a distinct BuiltinClassConstructor instead, so
	// unconditionally marking every ASTFunctionMethod non-constructible here is safe.
	@Override
	public boolean isMethod() {
		return true;
	}
}
