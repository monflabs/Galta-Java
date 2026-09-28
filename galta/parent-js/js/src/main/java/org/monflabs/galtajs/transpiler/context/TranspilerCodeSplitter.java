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
package org.monflabs.galtajs.transpiler.context;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.assignop.ASTAssign;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.variable.ASTVariableDecl;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;

/**
 * Transpiler splitter.
 */
public class TranspilerCodeSplitter {
	
	public static final int DEFAULT_BLOCK_SPLIT_MIN = 20;
	public static final int DEFAULT_BLOCK_SPLIT_MAX = 150;
	
	public static final int DEFAULT_MAX_FUNCTIONS_PER_DISPATCHER = 500;

	public static final int DEFAULT_OBJECT_LITERAL_SPLIT_THRESHOLD = 5;
	public static final int DEFAULT_OBJECT_SPLIT_MAX = 150;

	public static final int DEFAULT_ARRAY_LITERAL_SPLIT_THRESHOLD = 5;
	public static final int DEFAULT_ARRAY_SPLIT_MAX = 150;
	

	// Max number of ASTFunctions in a single container that still get the
	// per-function emitted class (each with its own callVoid + direct-arg
	// callVoidN fast path). Above this threshold, functions are grouped
	// under a single dispatcher class that switches on `index` - saving
	// class-count / metaspace at the cost of losing the direct-arg fast
	// path for those functions. See ASTVarContainer.transpilerDeclareStatement.
	public static final int DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER = 50;


	
	public TranspilerCodeSplitter(JSTranspilerOptions options) {
	}
	
	
	//
	// List of statements - blocks
	//
	
	public boolean shouldSplitBlock(ASTNode container, ASTNode[] statements) {
		if(statements.length>20) {
			return true;
		}
		return false;
	}
	public boolean isNodeBlockSplitable(ASTNode container, ASTNode node) {
		if(node instanceof ASTAssign || node instanceof ASTCall || node instanceof ASTFunctionDecl || node instanceof ASTVariableDecl) {
			return true;
		}
		return false;
	}
	
	// Minimum nodes in a code split
	public int blockSplitMin() {
		return DEFAULT_BLOCK_SPLIT_MIN;
	}
	// Maximum nodes in a code split
	public int blockSplitMax() {
		return DEFAULT_BLOCK_SPLIT_MAX;
	}

	
	//
	// Function dispatcher
	//
	
	// Threashold to use a disptahcer
	public int maxFunctionsBeforeDispatcher() {
		return DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER;
	}
	
	// Maximun functions per class
	public int maxFunctionsPerDispatcher() {
		return DEFAULT_MAX_FUNCTIONS_PER_DISPATCHER;
	}
	
	//
	// Object literals
	//
	
	// Minimum for splitting the code
	public int ObjectLiteralSplitTheshold() {
		return DEFAULT_OBJECT_LITERAL_SPLIT_THRESHOLD;
	}
	// Maximum entries in code split
	public int ObjectLiteralSplitMax() {
		return DEFAULT_OBJECT_SPLIT_MAX;
	}

	
	//
	// Array literals
	//
	
	// Minimum for splitting the code
	public int ArrayLiteralSplitTheshold() {
		return DEFAULT_ARRAY_LITERAL_SPLIT_THRESHOLD;
	}
	// Maximum entries in code split
	public int ArrayLiteralSplitMax() {
		return DEFAULT_ARRAY_SPLIT_MAX;
	}
}
