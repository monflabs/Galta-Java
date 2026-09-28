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

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;


/**
 * Base class for import/export
 */
public abstract class ASTImpExp extends ASTNode {
	
	public static final class Item {
		private String name;
		private String alias;
		Item(String name, String alias) {
			this.name = name;
			this.alias = alias;
		}
		public String getName() {
			return name;
		}
		public String getAlias() {
			return alias;
		}
	}

	private String defaultImport;
	private String namespace;
	private List<Item> items = new ArrayList<>();
	private String from;

	public ASTImpExp(Token t) {
		super(t);
	}
	
	public String getDefaultImport() {
		return defaultImport;
	}

	public void setDefaultImport(String defaultImport) {
		this.defaultImport = defaultImport;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public List<Item> getItems() {
		return items;
	}
	
	public void addItem(String name, String alias) {
		items.add(new Item(name, alias));
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	// Import/export attributes (`with { type: "json" }`) - null when no
	// WithClause was present at all (the overwhelmingly common case),
	// distinct from an explicit-but-empty `with {}` (a real, non-null
	// empty map - test262's own import-attribute-empty.js). Only "type"
	// is currently consumed at runtime (JSON module resolution); other
	// keys are parsed/stored but otherwise inert, matching a host that
	// simply doesn't recognize them (spec HostGetSupportedImportAttributes
	// is host-defined - GaltaJS's own set is just {"type"}).
	private java.util.Map<String,String> attributes;

	public java.util.Map<String,String> getAttributes() {
		return attributes;
	}

	public void addAttribute(String key, String value) {
		if(attributes==null) {
			attributes = new java.util.LinkedHashMap<>();
		}
		attributes.put(key, value);
	}

	public void ensureAttributes() {
		if(attributes==null) {
			attributes = java.util.Collections.emptyMap();
		}
	}

	// Shared by every call site that would otherwise call
	// RuntimeUtil.importModule(context, getFrom()) directly (ASTImport's
	// two hoist/evaluate sites, ASTExport's bare-star and named-from
	// sites) - routes to importAttributedModule() instead whenever a
	// "type" attribute is present (see JSGlobalContext.
	// importAttributedModule()'s own doc comment), otherwise behaves
	// exactly like a plain importModule() call. Centralizing this here
	// means a future new call site can't forget the attributed case.
	public org.monflabs.galtajs.JSModule resolveModule(org.monflabs.galtajs.rt.JSRuntimeContext context) {
		String type = attributes==null ? null : attributes.get("type");
		if(type!=null) {
			return org.monflabs.galtajs.rt.RuntimeUtil.importAttributedModule(context, getFrom(), attributes);
		}
		return org.monflabs.galtajs.rt.RuntimeUtil.importModule(context, getFrom());
	}
}