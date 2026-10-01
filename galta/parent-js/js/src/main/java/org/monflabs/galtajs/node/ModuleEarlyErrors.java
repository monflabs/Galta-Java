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
package org.monflabs.galtajs.node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTExport;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.ASTImpExp;
import org.monflabs.galtajs.node.control.ASTImport;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;

/**
 * Module early errors (ECMA-262 16.2.1.1) that need the whole module body:
 * <ul>
 * <li>the ExportedNames of the module are unique;</li>
 * <li>each local name of an {@code export { ... }} without {@code from} is
 * declared in the module (a var, lexical or import binding) and is an
 * identifier, not a string;</li>
 * <li>a string ModuleExportName is well-formed Unicode (no unpaired
 * surrogate), and a string imported name needs an {@code as} binding.</li>
 * </ul>
 */
final class ModuleEarlyErrors {

	private ModuleEarlyErrors() {
	}

	static void check(ASTProgram program) {
		ASTNode[] statements = program.getStatements();
		if(statements==null) {
			return;
		}
		Set<String> exported = new HashSet<>();
		Set<String> declared = new HashSet<>();
		List<ASTImpExp.Item> localExports = new ArrayList<>();
		for(ASTNode st: statements) {
			ASTNode s = ASTNode.skipTransparent(st);
			if(s instanceof ASTImport imp) {
				addIfNotNull(declared, imp.getDefaultImport());
				addIfNotNull(declared, imp.getNamespace());
				for(ASTImpExp.Item item: imp.getItems()) {
					checkWellFormed(s, item.getName());
					if(item.isStringName() && item.getAlias()==null) {
						throw new JSParseException(null, s, "A string import name needs an 'as' binding: '{0}'", item.getName());
					}
					declared.add(item.getAlias()!=null ? item.getAlias() : item.getName());
				}
			} else if(s instanceof ASTExport exp) {
				if(exp.getDefaultExport()!=null) {
					export(exported, s, "default");
					declared.add(declaredName(exp.getDefaultExport()));
				} else if(exp.getNamedExport()!=null) {
					for(String name: boundNames(exp.getNamedExport())) {
						export(exported, s, name);
						declared.add(name);
					}
				} else {
					if(exp.getNamespace()!=null) {
						checkWellFormed(s, exp.getNamespace());
						export(exported, s, exp.getNamespace());
					}
					for(ASTImpExp.Item item: exp.getItems()) {
						checkWellFormed(s, item.getName());
						checkWellFormed(s, item.getAlias());
						export(exported, s, item.getAlias()!=null ? item.getAlias() : item.getName());
						if(exp.getFrom()==null) {
							if(item.isStringName()) {
								throw new JSParseException(null, s, "A string cannot be exported without 'from': '{0}'", item.getName());
							}
							localExports.add(item);
						}
					}
				}
			} else {
				for(String name: boundNames(s)) {
					declared.add(name);
				}
			}
			EarlyErrorsValidator.collectVarNames(s, declared);
		}
		for(ASTImpExp.Item item: localExports) {
			if(!declared.contains(item.getName())) {
				throw new JSParseException(null, program, "Export '{0}' is not defined in module", item.getName());
			}
		}
	}

	private static void export(Set<String> exported, ASTNode node, String name) {
		if(!exported.add(name)) {
			throw new JSParseException(null, node, "Duplicate export of '{0}'", name);
		}
	}

	// BoundNames of a declaration at the top level of the module
	private static List<String> boundNames(ASTNode declaration) {
		List<String> names = new ArrayList<>();
		ASTNode d = ASTNode.skipTransparent(declaration);
		if(d instanceof ASTVariableDecl decl) {
			for(ASTVariableDecl.Entry e: decl.getEntries()) {
				e.forEachVarName(names::add);
			}
		} else if(d instanceof ASTFunctionDecl fn && fn.isStatement() && fn.getFunctionName()!=null) {
			names.add(fn.getFunctionName());
		} else if(d instanceof ASTClassDecl cls && cls.isStatement() && cls.getClassName()!=null) {
			names.add(cls.getClassName());
		}
		return names;
	}

	// The local binding of "export default": a named declaration binds its
	// name, anything else the internal "*default*" binding
	private static String declaredName(ASTNode defaultExport) {
		List<String> names = boundNames(defaultExport);
		return names.isEmpty() ? "*default*" : names.get(0);
	}

	private static void addIfNotNull(Set<String> set, String name) {
		if(name!=null) {
			set.add(name);
		}
	}

	// IsStringWellFormedUnicode
	private static void checkWellFormed(ASTNode node, String name) {
		if(name==null) {
			return;
		}
		for(int i=0; i<name.length(); i++) {
			char c = name.charAt(i);
			if(Character.isHighSurrogate(c) && i+1<name.length() && Character.isLowSurrogate(name.charAt(i+1))) {
				i++;
			} else if(Character.isSurrogate(c)) {
				throw new JSParseException(null, node, "An export or import name cannot contain an unpaired surrogate");
			}
		}
	}
}
