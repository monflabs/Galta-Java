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

import java.util.LinkedHashSet;
import java.util.Set;

import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.binaryop.ASTIn;
import org.monflabs.galtajs.node.clazz.ASTBaseClass;
import org.monflabs.galtajs.node.clazz.ASTClassMember;
import org.monflabs.galtajs.node.literal.ASTPrivateNameLiteral;

/**
 * Static implementation of the spec's "AllPrivateNamesValid" early error
 * (e.g. ScriptBody/FunctionBody's "It is a Syntax Error if
 * AllPrivateNamesValid of StatementList with an empty List as an argument is
 * false unless the source code is eval code that is being processed by a
 * direct eval"): every #name reference (a private member access `.#name`, or
 * a `#name in expr` brand check) must be lexically enclosed by a class that
 * declares that name - either one of THIS program's own nested classes, or
 * (for a direct eval only) a class enclosing the eval() call site itself,
 * threaded in via `initiallyValidNames` (see StandardLibrary's eval case and
 * JSRuntimeContext.collectEnclosingPrivateNames()).
 *
 * GaltaJS otherwise resolves #name entirely at RUNTIME, per access (see
 * JSRuntimeContext.resolvePrivateName(), used by ASTMember/ASTIn) - which
 * only notices an invalid reference when that specific statement actually
 * executes, too late for the early SyntaxError this rule requires (and,
 * critically, AFTER any earlier statements in the same program have already
 * run). This is a single, one-shot AST walk performed right after parsing/
 * init, like EarlyErrorsValidator's.
 *
 * `initiallyValidNames` is the set of private names visible at a direct
 * eval's call site (empty for every other compile). null skips the check
 * entirely: the engine itself never passes it (a transpiled direct eval gets
 * its caller's names baked in at transpile time, see StandardLibrary's eval
 * case), it is only honored for JSEnvironment.createScript() API callers.
 */
public class PrivateNameValidator {

	public static void check(ASTNode root, Set<String> initiallyValidNames) {
		if(initiallyValidNames==null) {
			return;
		}
		ASTNode offender = find(root, initiallyValidNames);
		if(offender!=null) {
			throw new JSParseException(null, offender, "Private field '{0}' must be declared in an enclosing class", nameOf(offender));
		}
	}

	private static String nameOf(ASTNode node) {
		if(node instanceof ASTMember m) {
			return m.getMemberName();
		}
		if(node instanceof ASTIn in && in.getLeftNode() instanceof ASTPrivateNameLiteral lit) {
			return (String)lit.getValue();
		}
		return "#?";
	}

	private static ASTNode find(ASTNode node, Set<String> validNames) {
		if(node==null) {
			return null;
		}
		if(node instanceof ASTMember m) {
			String mn = m.getMemberName();
			if(mn!=null && !mn.isEmpty() && mn.charAt(0)=='#' && !validNames.contains(mn)) {
				return node;
			}
		} else if(node instanceof ASTIn in && in.getLeftNode() instanceof ASTPrivateNameLiteral lit) {
			if(!validNames.contains((String)lit.getValue())) {
				return node;
			}
		}
		Set<String> childValidNames = validNames;
		if(node instanceof ASTBaseClass cls) {
			Set<String> own = ownPrivateNames(cls);
			if(!own.isEmpty()) {
				childValidNames = new LinkedHashSet<>(validNames);
				childValidNames.addAll(own);
			}
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			ASTNode child = node.getChild(i);
			// The ClassHeritage is evaluated in the enclosing private
			// environment, not in the class's own
			boolean heritage = node instanceof ASTBaseClass cls && child!=null && child==cls.getSuperClass();
			ASTNode result = find(child, heritage ? validNames : childValidNames);
			if(result!=null) {
				return result;
			}
		}
		return null;
	}

	// PrivateBoundNames of ClassBody: every private field/method/accessor
	// this class declares directly, regardless of declaration order (a
	// forward reference to a name declared later in the same class body is
	// still valid per this static check - initialization-order TDZ-style
	// failures are a separate, dynamic concern) or static/instance-ness.
	// Deliberately NOT recursive into a nested class's own elements (a
	// nested ASTBaseClass is handled on its own, separate call when the
	// outer find() walk reaches it as a child).
	private static Set<String> ownPrivateNames(ASTBaseClass cls) {
		Set<String> names = new LinkedHashSet<>();
		for(ASTNode el: cls.getElements()) {
			if(el instanceof ASTClassMember m && m.isPrivate()) {
				names.add("#"+m.getName());
			}
		}
		return names;
	}

	// Static-AST equivalent of JSRuntimeContext.collectEnclosingPrivateNames()
	// (a RUNTIME context-chain walk, used for an INTERPRETED direct-eval
	// caller) - for a TRANSPILED caller the "which #names are visible from
	// this eval call site" fact is fully determined by the AST alone (the
	// set of PrivateBoundNames each class declares is static; only the
	// PrivateName TOKEN identity minted per-evaluation is genuinely
	// runtime), so this can be computed once at transpile time via
	// ASTCall.transpileSpecialFunctions() and baked in as a literal, closing
	// the same "permissive callerXxx default for a transpiled caller" gap
	// class as callerHasNewTarget/callerIsMethod/callerIsDerivedCtor
	// elsewhere. Walks ALL enclosing ASTBaseClass ancestors (not stopping at
	// a function boundary - a private name declared by an outer class stays
	// visible to code deeper inside an ordinary nested function/method,
	// mirroring collectEnclosingPrivateNames' own unconditional getParent()
	// walk).
	public static Set<String> collectEnclosingPrivateNames(ASTNode callSite) {
		Set<String> names = new LinkedHashSet<>();
		for(ASTNode n=callSite.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTBaseClass cls) {
				names.addAll(ownPrivateNames(cls));
			}
		}
		return names;
	}
}
