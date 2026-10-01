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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTExport;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.ASTImpExp;
import org.monflabs.galtajs.node.control.ASTImport;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.rt.RuntimeUtil;

/**
 * The link step of a module graph (ECMA-262 16.2.1.5.3 Link and
 * InitializeEnvironment): before the first module of a graph runs, every
 * module it statically requests is parsed, and every import and indirect
 * export is resolved with ResolveExport. A dependency that does not parse,
 * a name no module exports, an ambiguous name (two {@code export *} with
 * different bindings) or a circular re-export is a SyntaxError, and no
 * module of the graph runs.
 * <p>
 * GaltaJS evaluates dependencies while it links them, so this is a separate
 * pass over the modules' syntax trees only: it does not load or cache
 * anything. A module whose source is not available as ECMAScript module
 * text (a native, CommonJS or JSON module, or one no resolver finds) is
 * opaque: any name resolves in it, and a missing module is reported when the
 * graph is evaluated.
 */
public final class StaticModuleLinker {

	private record Binding(String module, String name) {}
	private static final Binding AMBIGUOUS = new Binding(null, "*ambiguous*");

	private final JSEnvironment env;
	// resolved module name -> parsed program, null for an opaque module
	private final Map<String,ASTProgram> programs = new HashMap<>();

	private StaticModuleLinker(JSEnvironment env) {
		this.env = env;
	}

	/**
	 * Links the graph of the module {@code program}, named {@code name}.
	 * Throws a SyntaxError on the first unresolvable import or export.
	 */
	public static void link(JSEnvironment env, String name, ASTProgram program) {
		if(program.getAllModuleRequests().isEmpty()) {
			return;
		}
		StaticModuleLinker linker = new StaticModuleLinker(env);
		linker.programs.put(name, program);
		linker.linkGraph(name);
	}

	private void linkGraph(String rootName) {
		// Load: every module of the graph, before anything is resolved. A
		// request no resolver knows is a (non-SyntaxError) load failure,
		// reported when the graph is evaluated: linking stops here.
		java.util.List<String> order = new java.util.ArrayList<>();
		Set<String> visited = new HashSet<>();
		Deque<String> pending = new ArrayDeque<>();
		pending.add(rootName);
		while(!pending.isEmpty()) {
			String name = pending.poll();
			if(!visited.add(name)) {
				continue;
			}
			order.add(name);
			ASTProgram p = program(name);
			if(p==null) {
				continue;
			}
			for(ASTNode st: statements(p)) {
				String from = requestedModule(st);
				if(from!=null) {
					String target = ModuleUtil.resolvePath(name, from);
					if(!programs.containsKey(target) && RuntimeUtil.findModuleDescriptor(env, target)==null) {
						return;
					}
					pending.add(target);
				} else if(st instanceof ASTImport imp && imp.isSourcePhase() && imp.getFrom()!=null) {
					// A source phase import loads its module too (it is not linked)
					if(RuntimeUtil.findModuleDescriptor(env, ModuleUtil.resolvePath(name, imp.getFrom()))==null) {
						return;
					}
				}
			}
		}
		// Link: resolve the imports and indirect exports of every module
		for(String name: order) {
			ASTProgram p = programs.get(name);
			if(p==null) {
				continue;
			}
			for(ASTNode st: statements(p)) {
				// A JSON module has a single export, "default"
				if(st instanceof ASTImpExp impExp && impExp.getFrom()!=null && isJson(impExp)) {
					for(ASTImpExp.Item item: impExp.getItems()) {
						if(!"default".equals(item.getName())) {
							throw new org.monflabs.galtajs.JSParseException(null, null, "SyntaxError: The JSON module {0} does not provide an export named '{1}' (imported by {2})", ModuleUtil.resolvePath(name, impExp.getFrom()), item.getName(), name);
						}
					}
					continue;
				}
				String from = requestedModule(st);
				if(from==null) {
					continue;
				}
				String target = ModuleUtil.resolvePath(name, from);
				if(st instanceof ASTImport imp) {
					if(imp.getDefaultImport()!=null) {
						require(name, target, "default");
					}
					for(ASTImpExp.Item item: imp.getItems()) {
						require(name, target, item.getName());
					}
				} else {
					for(ASTImpExp.Item item: ((ASTExport)st).getItems()) {
						require(name, target, item.getName());
					}
				}
			}
		}
	}

	// The module an import or export declaration requests (loaded and
	// linked with the graph), null for anything else
	private static String requestedModule(ASTNode st) {
		if(st instanceof ASTImport imp && imp.getFrom()!=null && !imp.isSourcePhase() && !isAttributed(imp)) {
			return imp.getFrom();
		}
		if(st instanceof ASTExport exp && exp.getFrom()!=null && !isAttributed(exp)) {
			return exp.getFrom();
		}
		return null;
	}

	private void require(String importer, String target, String exportName) {
		Binding b = resolve(target, exportName, new HashSet<>());
		if(b==null) {
			throw new org.monflabs.galtajs.JSParseException(null, null, "SyntaxError: The module {0} does not provide an export named '{1}' (imported by {2})", target, exportName, importer);
		}
		if(b==AMBIGUOUS) {
			throw new org.monflabs.galtajs.JSParseException(null, null, "SyntaxError: The export '{1}' of module {0} is ambiguous (imported by {2})", target, exportName, importer);
		}
	}

	// ResolveExport(exportName, resolveSet): a Binding, null when the name
	// cannot be resolved (not exported, or a circular re-export), AMBIGUOUS
	private Binding resolve(String module, String exportName, Set<String> resolveSet) {
		if(!resolveSet.add(module+'\u0000'+exportName)) {
			return null;
		}
		ASTProgram p = program(module);
		if(p==null) {
			return new Binding(module, exportName);
		}
		String localName = localExports(p).get(exportName);
		if(localName!=null) {
			ASTProgram.IndirectExportEntry imported = p.getImportedLocalNames().get(localName);
			if(imported==null) {
				return new Binding(module, localName);
			}
			return resolveImported(module, imported, resolveSet);
		}
		for(ASTProgram.IndirectExportEntry ie: p.getIndirectExportEntries()) {
			if(ie.exportName().equals(exportName)) {
				return resolveImported(module, ie, resolveSet);
			}
		}
		// A default export is never provided by "export *"
		if("default".equals(exportName)) {
			return null;
		}
		Binding star = null;
		for(ASTProgram.StarExportEntry se: p.getStarExportEntries()) {
			Binding b = resolve(ModuleUtil.resolvePath(module, se.moduleRequest()), exportName, resolveSet);
			if(b==AMBIGUOUS) {
				return AMBIGUOUS;
			}
			if(b!=null) {
				if(star==null) {
					star = b;
				} else if(!star.equals(b)) {
					return AMBIGUOUS;
				}
			}
		}
		return star;
	}

	private Binding resolveImported(String module, ASTProgram.IndirectExportEntry entry, Set<String> resolveSet) {
		String target = ModuleUtil.resolvePath(module, entry.moduleRequest());
		if(ASTProgram.NAMESPACE_IMPORT_NAME.equals(entry.importName()) || ASTProgram.SOURCE_IMPORT_NAME.equals(entry.importName())) {
			return new Binding(target, entry.importName());
		}
		return resolve(target, entry.importName(), resolveSet);
	}

	// Export name -> local binding name, for the exports of the module's own
	// bindings (declarations, "export { x }" and "export default")
	private static Map<String,String> localExports(ASTProgram p) {
		Map<String,String> exports = new LinkedHashMap<>();
		for(ASTNode st: statements(p)) {
			if(!(st instanceof ASTExport exp) || exp.getFrom()!=null) {
				continue;
			}
			if(exp.getDefaultExport()!=null) {
				String name = declarationName(exp.getDefaultExport());
				exports.put("default", name!=null ? name : "*default*");
			} else if(exp.getNamedExport()!=null) {
				ASTNode d = ASTNode.skipTransparent(exp.getNamedExport());
				if(d instanceof ASTVariableDecl decl) {
					for(ASTVariableDecl.Entry e: decl.getEntries()) {
						e.forEachVarName(n -> exports.put(n, n));
					}
				} else {
					String name = declarationName(d);
					if(name!=null) {
						exports.put(name, name);
					}
				}
			} else {
				for(ASTImpExp.Item item: exp.getItems()) {
					exports.put(item.getAlias()!=null ? item.getAlias() : item.getName(), item.getName());
				}
			}
		}
		return exports;
	}

	private static String declarationName(ASTNode node) {
		ASTNode d = ASTNode.skipTransparent(node);
		if(d instanceof ASTFunctionDecl fn && fn.isStatement()) {
			return fn.getFunctionName();
		}
		if(d instanceof ASTClassDecl cls && cls.isStatement()) {
			return cls.getClassName();
		}
		return null;
	}

	private static java.util.List<ASTNode> statements(ASTProgram p) {
		java.util.List<ASTNode> list = new java.util.ArrayList<>();
		int n = p.getChildCount();
		for(int i=0; i<n; i++) {
			list.add(ASTNode.skipTransparent(p.getChild(i)));
		}
		return list;
	}

	private static boolean isJson(ASTImpExp impExp) {
		Map<String,String> attributes = impExp.getAttributes();
		return attributes!=null && "json".equals(attributes.get("type"));
	}

	private static boolean isAttributed(ASTImpExp impExp) {
		Map<String,String> attributes = impExp.getAttributes();
		return attributes!=null && attributes.get("type")!=null;
	}

	// The parsed program of a module, null when it is opaque. A parse error
	// is thrown: a dependency with a syntax error fails the whole graph.
	private ASTProgram program(String name) {
		if(programs.containsKey(name)) {
			return programs.get(name);
		}
		ASTProgram program = null;
		JSModuleDescriptor descriptor = RuntimeUtil.findModuleDescriptor(env, name);
		String source = descriptor!=null ? descriptor.getESModuleSource() : null;
		programs.put(name, null);
		if(source!=null) {
			program = env.createScript(source, name, JSEnvironment.SCRIPT_MODULE).getProgram();
			programs.put(name, program);
		}
		return program;
	}
}
