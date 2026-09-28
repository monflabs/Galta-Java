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
package org.monflabs.galtajs.transpiler;

import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.util.ObjectBuilder;

/**
 * GaltaJS transpiler options.
 */
public class JSTranspilerOptions {
	
	public static final int STRING_CONSTANT_THRESHOLD = 128;
	public static final int STRING_CONSTANT_CHUNKS    = 256;
	public static final int STRING_CONSTANT_SAMPLE    = 32;

	public static final int DEFAULT_SOURCE_INCOMMENTS = 64;
	
	

	public static class Builder extends ObjectBuilder<JSTranspilerOptions> {
		private boolean sourceInCode=true;
		private boolean debugInformation;
		private boolean mustDeclareVariables;
		private boolean sourceMap;
		private boolean sourceCode;
		private boolean sourceInComments;
		private int maxSourceInComments = DEFAULT_SOURCE_INCOMMENTS;
		private boolean commonJS;
		private boolean splitCode;
		private boolean specializeLoopCounterMath;
		private boolean debuggable;
		private Builder() {}
		public Builder sourceMap(boolean sourceMap) {
			this.sourceMap = sourceMap;
			return this;
		}
		public Builder sourceCode(boolean sourceCode) {
			this.sourceCode = sourceCode;
			return this;
		}
		public Builder sourceInCode(boolean sourceInCode) {
			this.sourceInCode = sourceInCode;
			return this;
		}
		public Builder sourceInComments(boolean sourceInComments) {
			this.sourceInComments = sourceInComments;
			return this;
		}
		public Builder maxSourceInComments(int maxSourceInComments) {
			this.maxSourceInComments = maxSourceInComments;
			return this;
		}
		public Builder commonJS(boolean commonJS) {
			this.commonJS = commonJS;
			return this;
		}
		public Builder splitCode(boolean splitCode) {
			this.splitCode = splitCode;
			return this;
		}
		public Builder debugInformation(boolean debugInformation) {
			this.debugInformation = debugInformation;
			return this;
		}
		public Builder mustDeclareVariables(boolean mustDeclareVariables) {
			this.mustDeclareVariables = mustDeclareVariables;
			return this;
		}
		// Experimental, off by default: shortcuts a classic
		// `for(let i=INIT; i CMP BOUND; i++/--/+=K/-=K)` loop's own test/
		// update to native int math, guarded by a per-operation
		// `instanceof Integer` check that falls back to exactly today's
		// codegen otherwise - see ASTFor's own "Loop-counter math
		// specialization" doc for the full design.
		public Builder specializeLoopCounterMath(boolean specializeLoopCounterMath) {
			this.specializeLoopCounterMath = specializeLoopCounterMath;
			return this;
		}
		// Experimental, off by default: emits a guarded call to
		// JSTranspiledUnit.debugStatement() at every top-level statement in
		// a block (see ASTBlock.transpileBlockStatements()), so a debugger
		// attached via org.monflabs.galtajs.debug.api.impl.Debuggable can
		// pause/step/breakpoint transpiled code the same way it already can
		// interpreted code (see ASTDebugHook for that side) - false emits
		// byte-identical codegen to today, true costs only a single static
		// volatile-int read per instrumented statement when compiled in but
		// no debugger is attached (see DebugRuntime.ACTIVE_SESSIONS).
		public Builder debuggable(boolean debuggable) {
			this.debuggable = debuggable;
			return this;
		}
		@Override
		protected JSTranspilerOptions _build() {
			return new JSTranspilerOptions(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private boolean sourceInCode;
	private boolean debugInformation;
	private boolean mustDeclareVariables;
	private boolean sourceMap;
	private boolean sourceCode;
	private boolean sourceInComments;
	private int maxSourceInComments;
	private boolean commonJS;
	private boolean specializeLoopCounterMath;
	private boolean debuggable;

	private TranspilerCodeSplitter codeSplitter;

	protected JSTranspilerOptions(Builder builder) {
		this.sourceInCode = builder.sourceInCode;
		this.debugInformation = builder.debugInformation;
		this.mustDeclareVariables = builder.mustDeclareVariables;
		this.sourceMap = builder.sourceMap;
		this.sourceCode = builder.sourceCode;
		this.sourceInComments = builder.sourceInComments;
		this.commonJS = builder.commonJS;
		this.maxSourceInComments = builder.maxSourceInComments;
		this.specializeLoopCounterMath = builder.specializeLoopCounterMath;
		this.debuggable = builder.debuggable;

		if(builder.splitCode) {
			this.codeSplitter = new TranspilerCodeSplitter(this);
		}
	}
	
	public boolean isSourceInCode() {
		return sourceInCode;
	}
	public boolean isDebugInformation() {
		return debugInformation;
	}
	public boolean isMustDeclareVariables() {
		return mustDeclareVariables;
	}
	public boolean isSourceMap() {
		return sourceMap;
	}
	public boolean isSourceCode() {
		return sourceCode;
	}
	public boolean isSourceInComments() {
		return sourceInComments;
	}
	public int getMaxSourceInComments() {
		return maxSourceInComments;
	}
	public boolean isCommonJS() {
		return commonJS;
	}
	public boolean isSpecializeLoopCounterMath() {
		return specializeLoopCounterMath;
	}
	public boolean isDebuggable() {
		return debuggable;
	}
	public TranspilerCodeSplitter getCodeSplitter() {
		return codeSplitter;
	}
}
