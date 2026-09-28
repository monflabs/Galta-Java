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
package org.monflabs.galtajs.optimizer;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.literal.ASTLiteral;

/**
 * Optimizer context.
 */
public class JSOptimizerContext {
	
	public static final class ContextVariable {
		VariableDef var;
		ASTNode initNode;
		public ContextVariable(VariableDef var) {
			this.var = var;
		}
		public VariableDef getVar() {
			return var;
		}
		public ASTNode getInitNode() {
			return initNode;
		}
		public void setInitNode(ASTNode initNode) {
			this.initNode = initNode;
		}
	}
	
	public static class MainOptimizerContext extends JSOptimizerContext {
		private ScriptOptimizer optimizer;
		private Map<ASTNode,Map<String,Object>> properties = new IdentityHashMap<ASTNode, Map<String,Object>>();
		public MainOptimizerContext(JSEnvironment env, ScriptOptimizer optimizer) {
			super(env,null,null);
			this.optimizer = optimizer;
		}
		@Override
		public ScriptOptimizer getScriptOptimizer() {
			return optimizer;
		}
		@Override
		protected Map<ASTNode,Map<String,Object>> getNodeProperties() {
			return properties;
		}
	}
	public static class ChildOptimizerContext extends JSOptimizerContext {
		public ChildOptimizerContext(JSOptimizerContext parent, ASTNode node) {
			super(parent.env,parent,node);
		}
	}
	
	private JSEnvironment env;
	private JSOptimizerContext parent;
	private ASTNode node;
	private Map<String, ContextVariable> variables = new LinkedHashMap<>();
	
	protected JSOptimizerContext(JSEnvironment env, JSOptimizerContext parent, ASTNode node) {
		this.env = env;
		this.parent = parent;
		this.node = node;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	public JSOptimizerContext getParent() {
		return parent;
	}
	
	public ASTNode getNode() {
		return node;
	}
	
	public Map<String, ContextVariable> getVariables() {
		return variables;
	}
	
	public ScriptOptimizer getScriptOptimizer() {
		return parent.getScriptOptimizer();
	}

	public PrintStream getTraceStream() {
		return getScriptOptimizer().getTraceStream();
	}
	
	public JSOptimizerContext.ContextVariable resolveSymbolVar(String varName) {
		for(JSOptimizerContext ctx=this; ctx!=null; ctx=ctx.getParent()) {
			JSOptimizerContext.ContextVariable var = ctx.getVariables().get(varName);
			if(var!=null) {
				// The name IS declared in this scope - stop here regardless of
				// whether it's const-foldable, or lexical shadowing breaks: a
				// nearer non-literal-initialized binding (e.g. a plain
				// parameter, whose ContextVariable never gets an initNode)
				// must NOT be skipped in favor of a same-named literal-
				// initialized CONST further up the chain. Previously this
				// fell through to the parent on every non-ASTLiteral match,
				// so a function parameter shadowing an outer `const x = 1`
				// got const-folded to the OUTER binding's value everywhere
				// in the inner function's body - both the read AND any
				// assignment to the parameter appeared to silently target
				// the wrong (outer) binding, since the read was replaced by
				// a compile-time literal instead of a real runtime lookup.
				// Confirmed via test262's language/block-scope/shadowing/
				// parameter-name-shadowing-parameter-name-let-const-and-var.js.
				return var.getInitNode() instanceof ASTLiteral ? var : null;
			}
		}
		return null;
	}

	public Object resolveSymbolValue(String varName) {
		JSOptimizerContext.ContextVariable var = resolveSymbolVar(varName);
		if(var!=null) {
			ASTNode node=var.getInitNode();
			if(node instanceof ASTLiteral lit) {
				return lit.getValue();
			}
		}
		throw new IllegalStateException("Internal error");
	}
	
	public <T> T getNodeProperty(ASTNode node, String key) {
		return getNodeProperty(node, key, null);
	}
	@SuppressWarnings("unchecked")
	public <T> T getNodeProperty(ASTNode node, String key, T defaultValue) {
		 Map<ASTNode,Map<String,Object>> props = getNodeProperties();
		 Map<String,Object> np = props.get(node);
		 if(np!=null) {
			 return (T)np.getOrDefault(key,defaultValue);
		 }
		 return defaultValue;
	}
	public void setNodeProperty(ASTNode node, String key, Object value) {
		 Map<ASTNode,Map<String,Object>> props = getNodeProperties();
		 Map<String,Object> np = props.get(node);
		 if(np==null) {
			 np = new HashMap<String, Object>();
			 props.put(node, np);
		 }
		 np.put(key, value);
	}
	protected Map<ASTNode,Map<String,Object>> getNodeProperties() {
		return parent.getNodeProperties();
	}
}
