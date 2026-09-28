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

import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 * Context for evaluating constants.
 * 
 * Cleaner: should be its own context and not inherit from the program one.
 */
public class OptimizerEvaluationRuntimeContext extends InterpretedGlobalRuntimeContext {
	
	private JSOptimizerContext optimizerContext;
	
	public OptimizerEvaluationRuntimeContext(JSOptimizerContext optimizerContext) {
		super(optimizerContext.getEnvironment(),optimizerContext.getEnvironment().createProgramExecutor());
		this.optimizerContext = optimizerContext;
	}

 	@Override
 	public VarAccessor resolveOwnIdentifierEntry(String varName) {
 		return new VarAccessor() {
			@Override
			public String getKey() {
				return varName;
			}
			@Override
			public Object getValue() {
				return optimizerContext.resolveSymbolValue(varName);
			}
 		};
 	}
}
