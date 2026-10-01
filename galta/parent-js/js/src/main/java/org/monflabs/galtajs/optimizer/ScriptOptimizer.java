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

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.util.Console;
import org.monflabs.util.ObjectBuilder;

/**
 * Optimize a full script.
 */
public class ScriptOptimizer {
	
	
	public static class Builder extends ObjectBuilder<ScriptOptimizer> {
		
		private NodeOptimizer[] optimizers;
		private PrintStream traceStream;
		private boolean traceNodes;

		/**
		 * @param optimizers the optimizers to run, in order; the array is copied
		 */
		public Builder optimizers(NodeOptimizer[] optimizers) {
			this.optimizers = optimizers!=null ? optimizers.clone() : null;
			return this;
		}
		public Builder traceStream(PrintStream traceStream) {
			this.traceStream = traceStream;
			return this;
		}
		public Builder traceNodes(boolean traceNodes) {
			this.traceNodes = traceNodes;
			return this;
		}

		@Override
		protected synchronized ScriptOptimizer _build() {
			return new ScriptOptimizer(this);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	
	private static final ScriptOptimizer EMPTY_NODE_OPTIMIZER = newBuilder()
			.build();
	public static ScriptOptimizer emptyOptimizer() {
		return EMPTY_NODE_OPTIMIZER;
	}

	/**
	 * The optimizers of {@link #defaultOptimizer()}. A public array, so it can
	 * be modified: {@link Builder#optimizers(NodeOptimizer[])} copies it, so
	 * that cannot affect an optimizer already built from it.
	 */
	public static final NodeOptimizer[] DEFAULT_NODE_OPTIMIZER_NODES = new NodeOptimizer[] {
		// Constant folding and dead-code removal fused into one traversal
		// instead of two separate full passes - see
		// ConstantFoldingAndUnreachableCodeOptimizer's own doc for why this
		// is safe and what ordering guarantee it preserves.
		new ConstantFoldingAndUnreachableCodeOptimizer(),
		// Phase 2a: annotate identifiers with (scopeHops, slotIndex, resolvedVarType)
		// so later phases can bypass the name-walking chain lookup. Metadata only;
		// no runtime path change on its own. Must run AFTER constant folding, since
		// folding can rewrite `NaN`/`Infinity` identifiers into literals - those
		// don't need annotation and shouldn't count toward slot indexing.
		new ScopeResolutionOptimizer()
	};

	private static final ScriptOptimizer DEFAULT_NODE_OPTIMIZER = newBuilder()
			.optimizers(DEFAULT_NODE_OPTIMIZER_NODES)
			.build();
	public static ScriptOptimizer defaultOptimizer() {
		return DEFAULT_NODE_OPTIMIZER;
	}
	
	private NodeOptimizer[] optimizers;
	private PrintStream traceStream;
	private boolean traceNodes;
	
	private ScriptOptimizer(Builder b) {
		this.optimizers = b.optimizers;
		this.traceStream = b.traceStream;
		this.traceNodes = b.traceNodes;
	}
	
	public NodeOptimizer[] getNodeOptimizers() {
		return optimizers!=null ? optimizers.clone() : null;
	}

	public boolean isTraceNodes() {
		return traceNodes;
	}

	public PrintStream getTraceStream() {
		return traceStream;
	}
	
	public void optimize(JSEnvironment env, ASTNode node) {
		if(isTraceNodes()) {
			Console.log("*******************************************************************");
			Console.log("*** Before optimization");
			node.dump(traceStream!=null?traceStream:Console.outStream());
			Console.log("");
		}
		if(optimizers!=null) {
			JSOptimizerContext context = new JSOptimizerContext.MainOptimizerContext(env,this);
			for(int i=0; i<optimizers.length; i++) {
				NodeOptimizer opt = optimizers[i];
				opt.optimize(context, node);
			}
		}
		if(isTraceNodes()) {
			Console.log("*** After optimization");
			node.dump(traceStream!=null?traceStream:Console.outStream());
			Console.log("*******************************************************************");
		}
	}
}