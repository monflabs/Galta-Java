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
package org.monflabs.galtajs.rt.interpreter;

import java.util.function.Supplier;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;

/**
 * Runtime context used by the interpreter.
 */
public interface JSInterpretedRuntimeContext extends JSRuntimeContext {
	
	// TODO: Temporary here -> should move to base context
	public static enum VAR_TYPE {
		CONST,
		LET,
		VAR,
		AUTO,
		FUNCTION,
		PREDECLARED,	// Predeclared, like fct parameters
		SYSTEM,			// System variable, not a JSVar
		// A named function EXPRESSION's own self-reference binding
		// (`var f = function myself() { ... myself ... }`) - per spec this
		// binding is immutable, but unlike CONST that's enforced at ASSIGNMENT
		// time, not as a static SyntaxError, and the violation is strict-mode-
		// conditional: a silent no-op in sloppy code, a TypeError in strict
		// code (see ASTIdentifier.evaluateAssign()). Otherwise behaves exactly
		// like PREDECLARED (a same-named parameter can still shadow/reuse the
		// slot; see ASTVarContainer._addVarDeclaration()'s canBeOverriden()
		// check) - except it's deliberately EXCLUDED from isSlotEligible()
		// below, forcing both reads and writes through the slower hash-
		// accessor path so the write-time immutability check has exactly one
		// call site to guard, instead of also needing to patch the
		// slot-array fast path in ASTIdentifier.
		FUNCTION_SELF,
		// `using`/`await using` (explicit resource management) - a lexically
		// scoped, TDZ'd binding exactly like CONST for assignment/hoisting/
		// slot-eligibility purposes, but additionally registers its
		// initializer's value (unless null/undefined) as a disposable
		// resource on the declaring scope, disposed - in reverse declaration
		// order, chaining via SuppressedError on multiple failures - when
		// that scope exits (see AbstractRuntimeContext.registerDisposable
		// Resource()/DisposeResourcesUtil). Whether a given USING binding is
		// the async ("await using") form is a per-declaration fact on
		// ASTVariableDeclUsing, not part of this type - the two forms are
		// otherwise identical for scoping purposes.
		USING
		;
		public boolean canAssign() {
			return this==VAR_TYPE.LET || this==VAR_TYPE.VAR || this==VAR_TYPE.FUNCTION || this==PREDECLARED || this==VAR_TYPE.AUTO || this==FUNCTION_SELF;
		}
		public boolean canOverride() {
			return this==VAR_TYPE.VAR || this==VAR_TYPE.FUNCTION || this==PREDECLARED || this==SYSTEM || this==FUNCTION_SELF;
		}
		public boolean canBeOverriden() {
			return this==VAR_TYPE.VAR || this==VAR_TYPE.FUNCTION || this==PREDECLARED || this==VAR_TYPE.AUTO || this==SYSTEM || this==FUNCTION_SELF;
		}
		public boolean isDeclaredGlobally() {
			return this!=VAR_TYPE.LET && this!=VAR_TYPE.CONST && this!=VAR_TYPE.PREDECLARED && this!=FUNCTION_SELF && this!=USING;
		}
		public boolean isHoisted() {
			return this==VAR_TYPE.VAR || this==VAR_TYPE.FUNCTION || this==VAR_TYPE.AUTO;
		}
		// Phase 2c: true when a binding of this type is bound into a function
		// frame's declaration-order slot array at frame-construction time (see
		// BuiltinFunctionInterpreter.bindParametersAndVars and
		// InterpretedFunctionRuntimeContext's constructor). LET/CONST are not
		// eligible: their bindings are created lazily by the initializer
		// statement and land in the hash table only (they'd read UNDEFINED
		// from a pre-filled slot before the initializer runs, breaking TDZ
		// and post-init reads alike). AUTO is not slot-bound either - it's
		// only created by sloppy implicit-global assignment, which at function
		// scope routes to globalThis, not to the local slot array.
		// FUNCTION_SELF is also excluded - see its own comment above.
		public boolean isSlotEligible() {
			return this==VAR_TYPE.VAR || this==VAR_TYPE.FUNCTION || this==PREDECLARED || this==SYSTEM;
		}
	}
	
	public ASTNode getCallerNode();
	public ASTNode setCallerNode(ASTNode node);
	
	public <T> T executeWithFilterContext(Object filterContext, ASTNode node, JSResult result);
	public <T> T executeWithFilterContext(Object filterContext, Supplier<Object> callback);
}
