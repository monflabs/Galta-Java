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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTRootStatementList;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.assignop.ASTAssign;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.clazz.ASTBaseClass;
import org.monflabs.galtajs.node.clazz.ASTClassMethod;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.literal.ASTObjectLiteral;
import org.monflabs.galtajs.node.literal.DefaultRef;
import org.monflabs.galtajs.node.literal.VariableFactory;
import org.monflabs.galtajs.node.unaryop.ASTAwait;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledFunctionRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.rt.transpiler.JSVarRef;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerEvalShadowContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorFunctionContext;
import org.monflabs.galtajs.transpiler.context.TranspilerParameterScopeContext;
import org.monflabs.galtajs.transpiler.util.TranspilerUtil;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;




/**
 * Function declaration.
 */
public abstract class ASTFunction extends ASTRootStatementList {
	
    public static final class Entry extends ASTNode {
        String varName;
        ASTNode initNode;

        public Entry(Token t, String name, ASTNode init){
        	super(t);
            varName=name;
            initNode=assignParent(init);
        }
        public String getName(){
            return varName;
        }
        public ASTNode getInitNode(){
            return initNode;
        }
		@Override
		public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
			return Signal.NONE;
		}

	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}
    }


	private String functionName;
	private int indexInRoot;
	private ASTArrayLiteral parameters;
	private int paramLength;
	private boolean strictMode;
	private boolean genuinelyStrictMode;

	private boolean useArguments;
	private boolean useThisFunction;

	// Precomputed (once, at init()) declaration-order slot index for each simple
	// parameter position - null when isSimpleParameterList() is false or when
	// declareVariables didn't produce a VariableDef for that position (defensive).
	// Enables BuiltinFunctionInterpreter.bindParametersAndVars' fast path to avoid
	// a per-call hash lookup + BiConsumer allocation on the hot function-call path.
	private int[] simpleParamSlots;
	private int funcNameSlot = -1;
	// Count of this function's own preamble variable slots - "arguments"
	// (always, index 0), the self-reference binding (index 1, only for a
	// named non-method function - see init()'s addVarDeclaration calls
	// above), and every parameter-bound name (declareVariables() below,
	// which can add MORE than getParamLength() slots for a destructuring
	// parameter, or contain names past getParamLength()'s "count up to the
	// first default/rest" ES6 .length cutoff). Snapshotted once, right after
	// parameter binding finishes and before any body statement is
	// processed - see transpileParameterBindingPrologue()'s own use, which
	// needs to know exactly how many LEADING variable-array slots are
	// handled by its own codegen (arguments/self-name assignment, parameter
	// destructuring below) rather than needing the generic
	// TDZ/UNDEFINED-fill treatment transpilerDeclareStatement() gives every
	// OTHER (body-declared) slot. A hardcoded "2+getParamLength()" formula
	// here previously assumed exactly 2 preamble slots always exist and that
	// parameters occupy exactly getParamLength() of them - both wrong (an
	// anonymous function has only 1, not 2; a destructuring/multi-binding
	// parameter can occupy more slots than getParamLength() counts) -
	// confirmed via test262's annexB/language/function-code Annex-B
	// block-scoped-function-hoisting suite, whose every test uses an
	// anonymous IIFE (only 1 real preamble slot) with the Annex-B-hoisted
	// name landing in the now-wrongly-skipped slot, left as raw Java `null`
	// instead of the UNDEFINED sentinel.
	private int preambleVariableCount;

	public ASTFunction(Token t, String functionName, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		super(t,nodes);
		this.functionName = functionName;
		this.parameters = assignParent(parameters);
	}
	
	public String getFunctionName() {
		return functionName;
	}
	public void setFunctionName(String name) {
		this.functionName = name;
	}
	
	public int getIndexInRoot() {
		return indexInRoot;
	}
	
	public ASTArrayLiteral getParameters() {
		return parameters;
	}
	
	public int getModifiers() {
		return    (isArrow()?BuiltinFunction.ARROW:0)
				| (isAsync()?BuiltinFunction.ASYNC:0)
				| (isGenerator()?BuiltinFunction.GENERATOR:0)
			    | (isStrictMode()?BuiltinFunction.STRICT_MODE:0)
			    | (isGenuinelyStrictMode()?BuiltinFunction.GENUINE_STRICT_MODE:0)
			    | (isMethod()?BuiltinFunction.METHOD:0);
	}

	public boolean isArrow() {
		return false;
	}

	// True for a MethodDefinition (object-literal shorthand method/getter/setter, or
	// a class method/getter/setter) - see ASTFunctionMethod, which overrides this.
	// NOT true for a class's own "constructor" method, which BuiltinClassConstructor
	// builds separately rather than wrapping a plain BuiltinFunction.
	public boolean isMethod() {
		return false;
	}
	
	public int getParamLength() {
		return paramLength;
	}

	public boolean isSimpleParameterList() {
		return parameters==null || parameters.isSimpleParameterList();
	}

	public String[] getSimpleParameterNames() {
		return parameters!=null ? parameters.getSimpleParameterNames() : new String[0];
	}

	public int[] getSimpleParamSlots() {
		return simpleParamSlots;
	}

	public int getFuncNameSlot() {
		return funcNameSlot;
	}
	
	public boolean isGenerator() {
		return false;
	}
	
	public boolean isAsync() {
		return false;
	}

	// Function.prototype.toString(): slice the exact original source text
	// (including comments/whitespace) using this node's begin/end position -
	// spec requires the literal source, not a re-serialization of the AST.
	// Returns null if no source text is available (e.g. this function's
	// containing program wasn't tracked with source text).
	public String extractOriginalSource() {
		String code = findSourceCode();
		if(code==null) {
			return null;
		}
		int[] range = extractOriginalSourceRange(code);
		if(range==null) {
			return null;
		}
		return code.substring(range[0], range[1]);
	}

	// The [start,end) character offsets, within `code`, of this function's exact
	// original source text - the offset-only half of extractOriginalSource()
	// (which just substrings the result). Split out so transpiled mode can bake
	// these two integers as literals into the generated function class instead
	// of the full substring string: the whole source is already carried once by
	// the JSTranspiledUnit (SourceCode.CODE / setSourceCode()), so a per-function
	// copy of a slice of it was pure duplication. Offsets are relative to the
	// same source string findSourceCode() returns here at transpile time, which
	// for a normally-compiled unit is the program source the unit holds at
	// runtime - so BuiltinFunctionTranspiler.getOriginalSource() can substring
	// the unit source with them directly. Returns null (caller falls back) when
	// `code` is null or the computed range is out of bounds/empty.
	public int[] extractOriginalSourceRange(String code) {
		if(code==null) {
			return null;
		}
		int start = lineColToOffset(code, getBeginLine(), getBeginCol());
		int end = lineColToOffset(code, getEndLine(), getEndCol());
		if(start<0 || end>code.length() || start>=end) {
			return null;
		}
		if(isAsync()) {
			// "async" precedes the begin token (the "function" keyword, or
			// an arrow function's parameter list/single identifier) with
			// only whitespace/comments between - the begin token itself
			// doesn't include it, so widen the start to cover it.
			int i = skipBackwardTrivia(code,start);
			if(i>=5 && code.regionMatches(i-5,"async",0,5) && (i==5 || !Character.isJavaIdentifierPart(code.charAt(i-6)))) {
				start = i-5;
			}
		}
		// A function's parameter list and body positions aren't fully
		// reliable from AST child aggregation (some node types, e.g.
		// ASTArrayLiteral for the parameter list, don't expose their
		// elements as generic children at all), and a block body's closing
		// "}" - plus any trailing comments/whitespace before it - isn't the
		// position of any child AST node in the first place. So: relocate
		// the parameter list's own "(" by scanning forward from `start`
		// (skipping the "function"/name/generator-star/async prefix),
		// bracket-match to its ")", then look for a body's opening "{"
		// right after and bracket-match that too. Expression-body arrows
		// (no braces) don't have this problem - their end IS a real
		// child's (the expression's) end position, already correct.
		int arrowIdx = isArrow() ? code.indexOf("=>",start) : -1;
		int searchLimit = arrowIdx>=0 ? arrowIdx : (int)Math.min(end+256,code.length());
		// A method/field's own computed name (`[expr](){}`) begins right at
		// `start` (the prefix-widening already covers up through the "["
		// itself) - `findChar()` below can't just scan forward for the
		// first "(", since `expr` may itself contain one (e.g. a nested
		// method definition: `{ [ { a(){} }.a ](){ } }`) that isn't the
		// real parameter list's opening paren at all. Bracket-match past
		// the whole computed-name group first, so the search for the real
		// "(" only starts after its closing "]". See test262 built-ins/
		// Function/prototype/toString/method-computed-property-name.js.
		int parenSearchStart = start;
		if(parenSearchStart<searchLimit && code.charAt(parenSearchStart)=='[') {
			int nameClose = findMatchingBracket(code,parenSearchStart);
			if(nameClose>parenSearchStart) {
				parenSearchStart = nameClose+1;
			}
		}
		int parenOpen = findChar(code,parenSearchStart,searchLimit,'(');
		int afterParams;
		if(parenOpen>=0) {
			int parenClose = findMatchingBracket(code,parenOpen);
			afterParams = parenClose>parenOpen ? parenClose+1 : -1;
		} else {
			// An arrow function's single unparenthesized identifier param
			// (e.g. `a => 0`) - the parameter list ends right at "=>".
			afterParams = arrowIdx;
		}
		if(afterParams>=0) {
			int bodyOpen = findBodyOpenBrace(code, afterParams, end);
			if(bodyOpen>=0) {
				int bodyClose = findMatchingBracket(code, bodyOpen);
				if(bodyClose>bodyOpen) {
					end = bodyClose+1;
				}
			} else if(isArrow()) {
				// Expression-body arrow (no braces): the naive aggregated
				// `end` above is UNRELIABLE here, not just insufficient -
				// ASTBinaryOp (and other operator-token-seeded node kinds)
				// seed their own end position from the OPERATOR token
				// itself rather than aggregating from their right operand,
				// so e.g. `a => a + b`'s computed end lands right after
				// the "+" and silently truncates "b". Rescan the body
				// expression's true extent from source text instead.
				int exprEnd = findExpressionEnd(code, afterParams, end);
				if(exprEnd>afterParams) {
					end = exprEnd;
				}
			}
		}
		return new int[]{start, end};
	}

	// Scans forward from `from` (just past an arrow's "=>") to find where
	// its expression body ends: any top-level "(", "[", "{" is skipped as a
	// balanced group (via findMatchingBracket), and scanning stops at the
	// first top-level ";", ",", unmatched closing bracket, or end of input -
	// all positions where an expression given as an arrow's body must end
	// (statement terminator, argument/element separator, or the enclosing
	// group's own close). Strings/template literals/comments are skipped as
	// opaque units so characters inside them don't affect the scan.
	private static int findExpressionEnd(String code, int from, int limit) {
		int len = code.length();
		int i = from;
		int lastNonSpace = from;
		while(i<len) {
			char c = code.charAt(i);
			if(Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='/') {
				while(i<len && code.charAt(i)!='\n' && code.charAt(i)!='\r') {
					i++;
				}
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='*') {
				int c2 = code.indexOf("*/",i+2);
				i = c2<0 ? len : c2+2;
				continue;
			}
			if(c==';' || c==',' || c==')' || c==']' || c=='}') {
				return lastNonSpace;
			}
			if(c=='\'' || c=='"') {
				i = skipQuoted(code,i,c);
				lastNonSpace = i;
				continue;
			}
			if(c=='`') {
				i = skipTemplateLiteral(code,i);
				lastNonSpace = i;
				continue;
			}
			if(c=='(' || c=='[' || c=='{') {
				int close = findMatchingBracket(code,i);
				i = close<0 ? len : close+1;
				lastNonSpace = i;
				continue;
			}
			i++;
			lastNonSpace = i;
		}
		return lastNonSpace;
	}

	// Scans forward from `from` for the first occurrence of `target`,
	// skipping only whitespace/comments/identifier characters (the
	// "function"/name/generator-star/async/single-identifier-param prefix
	// that can precede a parameter list's "("). Returns -1 if not found
	// before `limit`.
	private static int findChar(String code, int from, int limit, char target) {
		int i = from;
		int len = Math.min(limit,code.length());
		while(i<len) {
			char c = code.charAt(i);
			if(c==target) {
				return i;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='/') {
				while(i<len && code.charAt(i)!='\n' && code.charAt(i)!='\r') {
					i++;
				}
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='*') {
				int close = code.indexOf("*/",i+2);
				i = close<0 ? len : close+2;
				continue;
			}
			i++;
		}
		return -1;
	}

	// Scans forward from `from` (just past the parameter list's closing
	// ")"), skipping only whitespace/comments/"=>" - the only things that
	// can legally appear there before a block body - looking for the body's
	// opening "{". Returns -1 if none is found (an expression-body arrow,
	// e.g. `a => 0`, has no such brace).
	private static int findBodyOpenBrace(String code, int from, int limit) {
		int i = from;
		int len = Math.min(limit+256,code.length()); // small slack past the naive end
		while(i<len) {
			char c = code.charAt(i);
			if(Character.isWhitespace(c) || c=='=' || c=='>') {
				i++;
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='/') {
				while(i<len && code.charAt(i)!='\n' && code.charAt(i)!='\r') {
					i++;
				}
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='*') {
				int close = code.indexOf("*/",i+2);
				i = close<0 ? len : close+2;
				continue;
			}
			return c=='{' ? i : -1;
		}
		return -1;
	}

	// Bracket-matches an opening "(", "{" or "[" at `openIndex` to find the
	// index of its true matching close, skipping over comments/strings/
	// template literals so any bracket characters inside them don't affect
	// the count. Nested brackets of any kind (including a template
	// literal's "${...}" interpolations) recurse back into this same
	// method, so arbitrary nesting is handled naturally without manual
	// depth/stack bookkeeping.
	private static int findMatchingBracket(String code, int openIndex) {
		char open = code.charAt(openIndex);
		char close = open=='(' ? ')' : open=='[' ? ']' : '}';
		int len = code.length();
		int i = openIndex+1;
		while(i<len) {
			char c = code.charAt(i);
			if(c=='/' && i+1<len && code.charAt(i+1)=='/') {
				i += 2;
				while(i<len && code.charAt(i)!='\n' && code.charAt(i)!='\r') {
					i++;
				}
				continue;
			}
			if(c=='/' && i+1<len && code.charAt(i+1)=='*') {
				int c2 = code.indexOf("*/",i+2);
				i = c2<0 ? len : c2+2;
				continue;
			}
			if(c=='\'' || c=='"') {
				i = skipQuoted(code,i,c);
				continue;
			}
			if(c=='`') {
				i = skipTemplateLiteral(code,i);
				continue;
			}
			if(c=='(' || c=='{' || c=='[') {
				int c2 = findMatchingBracket(code,i);
				i = c2<0 ? len : c2+1;
				continue;
			}
			if(c==close) {
				return i;
			}
			i++;
		}
		return -1;
	}

	private static int skipQuoted(String code, int i, char quote) {
		int len = code.length();
		i++;
		while(i<len) {
			char c = code.charAt(i);
			if(c=='\\') {
				i += 2;
				continue;
			}
			i++;
			if(c==quote) {
				break;
			}
		}
		return i;
	}

	// Skips an entire template literal starting at its opening "`", handling
	// nested "${...}" interpolations (which may themselves contain further
	// template literals) by recursing into findMatchingBracket for each one.
	private static int skipTemplateLiteral(String code, int i) {
		int len = code.length();
		i++; // opening `
		while(i<len) {
			char c = code.charAt(i);
			if(c=='\\') {
				i += 2;
				continue;
			}
			if(c=='`') {
				return i+1;
			}
			if(c=='$' && i+1<len && code.charAt(i+1)=='{') {
				int close = findMatchingBracket(code,i+1);
				i = close<0 ? len : close+1;
				continue;
			}
			i++;
		}
		return len;
	}

	// Skips whitespace and block comments going BACKWARD from `i`, so a
	// preceding modifier keyword (e.g. "async") can be found even when
	// separated from the begin token by a comment. Line comments aren't
	// handled going backward (ambiguous without scanning from line start),
	// but they're rare right before a modifier keyword in practice.
	private static int skipBackwardTrivia(String code, int i) {
		while(true) {
			int before = i;
			while(i>0 && Character.isWhitespace(code.charAt(i-1))) {
				i--;
			}
			if(i>=2 && code.charAt(i-1)=='/' && code.charAt(i-2)=='*') {
				int open = code.lastIndexOf("/*",i-3);
				if(open>=0) {
					i = open;
				}
			}
			if(i==before) {
				return i;
			}
		}
	}

	private static int lineColToOffset(String source, int line, int col) {
		int curLine = 1;
		int i = 0;
		int len = source.length();
		while(curLine<line && i<len) {
			char c = source.charAt(i);
			i++;
			if(c=='\r') {
				if(i<len && source.charAt(i)=='\n') {
					i++;
				}
				curLine++;
			} else if(c=='\n') {
				curLine++;
			}
		}
		return i + (col-1);
	}
	
	public boolean isStrictMode() {
		return strictMode;
	}

	@Override
	public boolean isGenuinelyStrictMode() {
		return genuinelyStrictMode;
	}

	public boolean isStatement() {
		return false;
	}

	public boolean isBlockNested() {
		return false;
	}

	   
	public boolean isUseArguments() {
		return useArguments;
	}

	public void setUseArguments(boolean useArguments) {
		this.useArguments = useArguments;
	}

	public boolean isUseThisFunction() {
		return useThisFunction;
	}

	public void setUseThisFunction(boolean useThisFunction) {
		this.useThisFunction = useThisFunction;
	}

	// Phase C: the emitted callVoid body can safely run against a shared,
	// pre-allocated fctContext (see BuiltinFunctionTranspiler.doExecute) - so the
	// caller doesn't pay a `new TranspiledFunctionRuntimeContext(...)` + ScopedValue
	// push on every invocation - only when the body neither observes nor mutates
	// per-call state on fctContext:
	//   - no `this` / `super` / `new.target` / `arguments` / with / direct eval
	//   - no yield / yield* / await
	//   - no class declarations (their body-wrapping context and `super` binding
	//     are per-call state)
	//   - not arrow/generator/async/method, not a class constructor
	// Nested regular (non-arrow) functions ARE allowed: their body has its own
	// this/arguments/newTarget bindings, so anything inside those bindings is
	// shielded from the outer frame. The nested function DOES still receive the
	// outer's fctContext as its lexical parentCtx via `new F_inner(_ctx, ...)` -
	// but the emitted body only reads scope-chain-invariant state through that
	// chain (globals, strict-mode flag, parent function object), never anything
	// varying per call to the outer, so sharing the outer's elided fctContext is
	// safe. Nested arrow functions are transparent for this/args/newTarget and
	// therefore do NOT shield - a `this` inside an arrow inside us is still ours.
	//
	// Everything else - free identifier lookups against globals, calls to other
	// functions, arithmetic - is safe because it goes through the invariant
	// scope chain rooted at the function's lexical parent, which the shared
	// fctContext still points at.
	public boolean isTranspiledContextElidable() {
		if(isArrow() || isGenerator() || isAsync() || isMethod()) {
			return false;
		}
		if(useArguments) {
			return false;
		}
		if(parameters!=null && containsHazard(parameters,false)) {
			return false;
		}
		ASTNode[] statements = getStatements();
		if(statements!=null) {
			for(ASTNode s : statements) {
				if(containsHazard(s,false)) {
					return false;
				}
			}
		}
		return true;
	}
	// `insideNestedNonArrow` = we're descending inside a nested non-arrow
	// function's subtree, whose own this/super/newTarget/arguments/yield/await
	// bind to itself, not to us - so those constructs stop being hazards for
	// the outer function. Arrows do NOT set this flag: their this/arguments
	// transparently observe the enclosing non-arrow's per-call state.
	private static boolean containsHazard(ASTNode node, boolean insideNestedNonArrow) {
		if(node==null) {
			return false;
		}
		if(node instanceof org.monflabs.galtajs.node.ASTThis
				|| node instanceof org.monflabs.galtajs.node.ASTSuperMember
				|| node instanceof org.monflabs.galtajs.node.ASTSuperCtor
				|| node instanceof org.monflabs.galtajs.node.ASTNewMember
				|| node instanceof ASTYield
				|| node instanceof ASTYieldStar
				|| node instanceof org.monflabs.galtajs.node.unaryop.ASTAwait) {
			return !insideNestedNonArrow;
		}
		// A class declaration wraps its body in its own runtime context and has
		// its own `super` binding - keep it as an unconditional hazard for now.
		if(node instanceof org.monflabs.galtajs.node.clazz.ASTBaseClass) {
			return true;
		}
		// `delete`/assignment on a member expression both consult the AMBIENT
		// RuntimeUtil.isStrictMode() (JSContext.getUnchecked().isStrictMode())
		// to decide whether a failed [[Delete]]/[[Set]] throws - unlike this/
		// super/arguments/newTarget, that isn't threaded through an explicit
		// fctContext reference, so the elidable fast path (which skips
		// fctContext.with(...), see BuiltinFunctionTranspiler.doExecute())
		// leaves the CALLER's ambient strict-mode in place instead of this
		// function's own (lexically fixed, but not yet pushed) one. Confirmed
		// via test262 built-ins/Proxy/{set,deleteProperty}/trap-is-*-target-
		// is-proxy.js: `assert.throws(TypeError, function(){"use strict";
		// delete obj.nonConfigurable; / obj.nonWritable = 1;})` silently
		// succeeded instead of throwing, because the callback (itself
		// elidable) never became the ambient context. An unconditional
		// hazard, not gated on insideNestedNonArrow, since the throw-on-
		// failure is about THIS function's own strict-mode-ness regardless
		// of nesting. Plain identifier assignment doesn't go through this
		// [[Set]]/DESC_CHECK path, so it's left alone.
		if(node instanceof org.monflabs.galtajs.node.unaryop.ASTDelete) {
			return true;
		}
		if(node instanceof org.monflabs.galtajs.node.assignop.ASTAbstractAssign assign
				&& assign.getLeftNode() instanceof org.monflabs.galtajs.node.MemberNode) {
			return true;
		}
		boolean childInsideNestedNonArrow = insideNestedNonArrow;
		if(node instanceof ASTFunction fn) {
			// Non-arrow: shields this/args/newTarget/yield/await for its subtree.
			// Arrow: transparent - its this/args/newTarget = enclosing non-arrow's,
			// so descend with the SAME flag rather than setting it.
			if(!fn.isArrow()) {
				childInsideNestedNonArrow = true;
			}
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			if(containsHazard(node.getChild(i),childInsideNestedNonArrow)) {
				return true;
			}
		}
		return false;
	}

	// Early error (GeneratorDeclaration/GeneratorExpression/GeneratorMethod,
	// spec 15.5.1 "It is a Syntax Error if FormalParameters Contains
	// YieldExpression is true"): a generator's OWN FormalParameters are
	// parsed WITH the Yield grammar flag active (see JSParser.jj's
	// yieldReserved, toggled by FunctionDeclaration() based on the "*"
	// token) - so `function*(x = yield){}` syntactically produces a real
	// ASTYield node in the parameter list rather than rejecting "yield" as
	// an identifier outright, but must still be rejected here. This is the
	// same async-generator rule too (isGenerator() is true for those as
	// well). Mirrors containsHazard()'s nested-function-boundary-aware
	// traversal (skip a nested non-arrow function's own subtree - its own
	// yield binds to itself, not to this function; descend into arrows,
	// which are transparent), but narrowed to only Yield/YieldStar (unlike
	// containsHazard, `this`/`super`/`await` are irrelevant to this
	// specific check).
	private static boolean containsYieldExpression(ASTNode node, boolean insideNestedNonArrow) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTYield || node instanceof ASTYieldStar) {
			return !insideNestedNonArrow;
		}
		boolean childInsideNestedNonArrow = insideNestedNonArrow;
		if(node instanceof ASTFunction fn && !fn.isArrow()) {
			childInsideNestedNonArrow = true;
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			if(containsYieldExpression(node.getChild(i),childInsideNestedNonArrow)) {
				return true;
			}
		}
		return false;
	}

	// Early error (AsyncFunctionDeclaration/AsyncFunctionExpression/
	// AsyncGeneratorDeclaration/AsyncGeneratorExpression/AsyncMethod/
	// AsyncArrowFunction, spec "It is a Syntax Error if FormalParameters
	// Contains AwaitExpression is true") - exact mirror of
	// containsYieldExpression() above (same nested-function-boundary-aware
	// traversal, narrowed to only Await), for the equivalent `await`
	// restriction on an async function's OWN formal parameters. Async
	// generators need BOTH this check and containsYieldExpression() above
	// (isGenerator() is true for them too) - test262 built-ins/
	// AsyncGeneratorFunction/instance-await-expr-in-param.js:
	// `AsyncGeneratorFunction('x = await 42', '')` must throw SyntaxError.
	private static boolean containsAwaitExpression(ASTNode node, boolean insideNestedNonArrow) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTAwait) {
			return !insideNestedNonArrow;
		}
		boolean childInsideNestedNonArrow = insideNestedNonArrow;
		// Unlike containsYieldExpression() (arrows can never be generators,
		// so a plain-arrow boundary check is enough there), an ARROW
		// function CAN independently be async (`async () => await x`) and
		// then establishes its OWN AsyncFunctionBody - its own `await`
		// belongs to itself, not to this outer function's parameter list -
		// so only a genuinely PLAIN (non-async) arrow stays transparent
		// here.
		if(node instanceof ASTFunction fn && (!fn.isArrow() || fn.isAsync())) {
			childInsideNestedNonArrow = true;
		}
		int n = node.getChildCount();
		for(int i=0; i<n; i++) {
			if(containsAwaitExpression(node.getChild(i),childInsideNestedNonArrow)) {
				return true;
			}
		}
		return false;
	}

	// Only call site: JSEnvironment.compileFunction() - i.e. `new
	// Function(...)`/`new AsyncFunction(...)`/`new GeneratorFunction(...)`/
	// `new AsyncGeneratorFunction(...)` (CreateDynamicFunction). Per spec
	// (CreateDynamicFunction steps 29-31) this always enforces
	// AllPrivateIdentifiersValid with an empty starting list, UNCONDITIONALLY
	// (a dynamically-created function body/parameters have no enclosing
	// class - it's always compiled as if freestanding) - never the
	// permissive "skip" default, unlike a direct eval from a transpiled
	// caller (see PrivateNameValidator's class doc).
	public void __init(JSEnvironment env) {
		this.init(new MainContext(env,false));
		org.monflabs.galtajs.node.PrivateNameValidator.check(this, java.util.Collections.emptySet());
		org.monflabs.galtajs.node.EarlyErrorsValidator.check(this);
	}
	
	// Early errors of the formal parameters: a rest parameter comes last,
	// with no trailing comma and no initializer, and destructured
	// parameters are binding patterns.
	private void checkParameterPatterns(boolean strict) {
		ASTArrayLiteral params = getParameters();
		if(params==null) {
			return;
		}
		if(params.hasCommaAfterSpread()) {
			throw new JSParseException(null, this, "Rest parameter must be last formal parameter");
		}
		if(isForceStrictMode() && !params.isSimpleParameterList()) {
			throw new JSParseException(null, this, "Illegal 'use strict' directive in function with non-simple parameter list");
		}
		for(int i=0; i<params.getChildCount(); i++) {
			ASTNode p = params.getChild(i);
			ASTNode target = null;
			if(p instanceof ASTArrayLiteral.InitializerSpread sp) {
				if(sp.getNode() instanceof ASTAssign) {
					throw new JSParseException(null, this, "Rest parameter may not have a default initializer");
				}
				target = sp.getNode();
			} else if(p instanceof ASTArrayLiteral.InitializerExpression exp) {
				target = exp.getNode() instanceof ASTAssign as ? as.getLeftNode() : exp.getNode();
			}
			if(target instanceof ASTContainerLiteral lit) {
				lit.checkPattern(true, strict);
			}
		}
	}

    @Override
	protected void init(InitContext parentContext) {
    	this.strictMode = isForceStrictMode() || parentContext.isStrictMode();
    	this.genuinelyStrictMode = isForceStrictMode() || parentContext.isGenuinelyStrict();
    	InitContext initContext = new ChildContext(parentContext,strictMode,genuinelyStrictMode);
    	checkParameterPatterns(genuinelyStrictMode);

        // An arrow function has no `arguments` binding of its own at all -
        // per spec it always resolves through the nearest enclosing non-
        // arrow function's binding (see ASTIdentifier.
        // markEnclosingNonArrowUsesArguments()). Declaring a dummy SYSTEM
        // slot here for an arrow anyway used to make BOTH the transpiler's
        // static identifier resolution (ASTIdentifier.getIdentifierReadAccessor,
        // which stops at the first ASTVarContainer owning the name) and the
        // "mark resolving VariableDef as used" walk below stop at the
        // arrow's own never-populated slot instead of continuing outward to
        // the real one - confirmed via test262
        // returns-async-arrow-returns-arguments-from-parent-function.js (and
        // its plain-synchronous-arrow equivalent): `arguments` read inside an
        // arrow silently resolved to undefined instead of the enclosing
        // function's real Arguments object.
        if(!isArrow()) {
        	addVarDeclaration(Arguments.ARGUMENTS,VAR_TYPE.SYSTEM,JSType.UNKNOWN);
        }

		// For recursive calls within the function. A DECLARATION's own name
    	// binding is ordinarily mutable (PREDECLARED); a named EXPRESSION's
    	// own name binding is spec-immutable (FUNCTION_SELF) - see
    	// ASTIdentifier.evaluateAssign() for how that's enforced. A method/
    	// getter/setter's "name" is a PropertyName (a property key), never a
    	// BindingIdentifier at all - it must NOT get a self-reference
    	// binding here either, only the (separately-gated) strict-binding-
    	// name check below already knew this. Without this guard, `{ get
    	// ownKeys() { return () => ownKeys; } }` incorrectly shadowed an
    	// outer-scope `ownKeys` variable with a self-reference to the
    	// getter itself, silently breaking any getter/setter/method whose
    	// property name collides with a variable its body legitimately
    	// wants to close over (confirmed via a direct repro and
    	// Object/keys/proxy-keys.js/property-traps-order-with-proxied-array.js,
    	// both of which use a proxy handler's `get ownKeys()`/`get
    	// getOwnPropertyDescriptor()` returning a closure over an
    	// identically-named outer variable/property).
    	if(StringUtil.isNotEmpty(getFunctionName()) && !isMethod()) {
	    	if(isStatement() && !isBlockNested()) {
	    		// A DECLARATION's own name is NOT a separate self-reference
	    		// binding - it's already an ordinary, mutable, hoisted binding
	    		// in the ENCLOSING scope (see ASTFunctionDecl.createFunction()),
	    		// and a reference to it from inside the function body must
	    		// resolve to THAT SAME outer binding via normal scope-chain
	    		// lookup - unlike a named function EXPRESSION (below), which
	    		// per spec DOES get its own dedicated, immutable, function-
	    		// body-scoped self-reference environment (15.2.5
	    		// InstantiateOrdinaryFunctionExpression). Previously this branch
	    		// ALSO called addVarDeclaration(..., PREDECLARED, ...) here,
	    		// creating a SEPARATE function-local copy that merely started
	    		// out equal to the outer binding - a self-reassignment
	    		// (`function fn(){ fn=2; }`) mutated only that local copy,
	    		// never the outer one, silently breaking any caller that
	    		// expects the reassignment to be observable afterward (found
	    		// via test262's own module default-export liveness tests,
	    		// `export default function fn(){ fn=2; ...}` /
	    		// eval-gtbndng-indirect-update-dflt.js family - `imported.
	    		// default` must see the reassignment - but the same bug
	    		// applies equally to a plain, non-exported top-level
	    		// `function fn(){ fn=2; }`, confirmed via direct repro).
	    	} else if(isStatement()) {
	    		// Block-nested declaration (Annex B candidate or genuinely
	    		// block-scoped in strict mode): unlike the non-nested case
	    		// above, this declaration's EXTERNALLY-visible binding is a
	    		// separate, runtime-only lexical binding in its own block
	    		// (BlockDeclarationInstantiation), never represented as a
	    		// static VariableDef the scope-resolution optimizer's walk can
	    		// see - so a plain outer-scope lookup for a self-reference from
	    		// inside the body would incorrectly resolve to the OUTER
	    		// (root-hoisted, Annex B) binding instead, when one exists
	    		// (confirmed via annexB/language/global-code/block-decl-
	    		// global-block-scoping.js: reassigning the function's own name
	    		// from inside its body must be observable only to a
	    		// SUBSEQUENT SAME-INVOCATION read, never to the outer binding).
	    		// Keeps the older function-local PREDECLARED self-copy instead -
	    		// not a fully correct implementation of the block's own live
	    		// binding, but preserves already-passing behavior pending a
	    		// real fix (needs the block's own lexical binding, not this
	    		// declaration's static VariableDef, to be resolvable at all).
	    		addVarDeclaration(getFunctionName(), VAR_TYPE.PREDECLARED, null);
	    	} else {
		    	VariableDef selfRef = addVarDeclaration(getFunctionName(), VAR_TYPE.FUNCTION_SELF, null);
		    	selfRef.setFunctionSelfStrict(isGenuinelyStrictMode());
		    	// A function DECLARATION's own name is checked against its
		    	// ENCLOSING scope's strictness instead - see
		    	// ASTFunctionDecl.createFunction() - since that's the scope the
		    	// BindingIdentifier production is actually contained in; only a
		    	// named function EXPRESSION's own name is restricted by its own
		    	// body's strictness.
		    	checkStrictBindingName(initContext, getFunctionName(), this);
	    	}
    	}

    	// Dynamic functions don't need an index as they are anyway interpreted
    	IContextBlockContainer functionContainer = findParentNodeByClassUnchecked(IContextBlockContainer.class);
        if(functionContainer!=null) {
        	this.indexInRoot = functionContainer.addFunctionDeclaration(this);
        }

    	// A duplicate parameter name is only tolerated (last-one-wins, via
    	// fixParametersForFunction() below) for an ordinary sloppy-mode
    	// function with a simple (no rest/default/destructured) parameter
    	// list - the FormalParameters grammar production. Every other kind
    	// (arrow, method/getter/setter, generator, async, or any non-simple
    	// parameter list) is grammatically UniqueFormalParameters and must
    	// reject a duplicate BoundName as an early SyntaxError, regardless of
    	// strict-mode-ness.
    	if(genuinelyStrictMode || !parameters.isSimpleParameterList() || isArrow() || isGenerator() || isAsync() || isMethod()) {
    		Set<String> seenParamNames = new HashSet<>();
    		parameters.forEachVarName((name) -> {
    			if(!seenParamNames.add(name)) {
    				throw new JSParseException(null,this,"Duplicate parameter name not allowed in this context: '{0}'",name);
    			}
    		});
    	}

    	if(isGenerator() && containsYieldExpression(parameters,false)) {
    		throw new JSParseException(null,this,"Yield expression not allowed in formal parameters of generator function");
    	}
    	if(isAsync() && containsAwaitExpression(parameters,false)) {
    		throw new JSParseException(null,this,"Await expression not allowed in formal parameters of async function");
    	}

    	// Fix the function parameters
    	// Per the spec, a function can have the same parameter twice and only the last one counts...
    	// This is only for the main parameters f(a,b,a) works while f(a,b,{a}) doesn't
    	parameters.fixParametersForFunction();
    	parameters.declareVariables(this, VAR_TYPE.PREDECLARED);
    	parameters.forEachVarName((name) -> checkStrictBindingName(initContext, name, this));

    	// See preambleVariableCount's own field comment - every preamble slot
    	// (arguments/self-reference/parameter-bound names) has now been added
    	// above; no body statement has been processed yet (that happens only
    	// once super.init() below walks this function's own statement list).
    	preambleVariableCount = hasDeclaredVariables() ? getVariables().size() : 0;

    	// Precompute the declaration-order slot indices for the simple-param
    	// fast path in BuiltinFunctionInterpreter.bindParametersAndVars.
    	if(isSimpleParameterList()) {
    		String[] names = getSimpleParameterNames();
    		int[] slots = new int[names.length];
    		for(int i=0; i<names.length; i++) {
    			VariableDef vd = names[i]!=null ? getOwnVariable(names[i]) : null;
    			slots[i] = vd!=null ? vd.getJavaVariableIndex() : -1;
    			// A simple-list parameter is unconditionally bound from its
    			// argument in the prologue before any body statement runs, and
    			// (simple list => no default/destructuring expressions) nothing
    			// can observe it mid-binding - so it's never in the TDZ at a
    			// body read. Mark it exempt so ASTIdentifier skips the
    			// otherwise-PREDECLARED checkTDZ guard. See VariableDef.tdzExempt
    			// and transpileParameterBindingPrologue's matching skip of the
    			// param TDZ pre-fill for simple lists.
    			if(vd!=null) {
    				vd.setTdzExempt(true);
    			}
    		}
    		simpleParamSlots = slots;
    	}
    	if(StringUtil.isNotEmpty(getFunctionName())) {
    		VariableDef fnv = getOwnVariable(getFunctionName());
    		funcNameSlot = fnv!=null ? fnv.getJavaVariableIndex() : -1;
    	}
    	
		// ES6+: function.length = count of parameters before first default or rest parameter
    	paramLength = 0;
		ASTArrayLiteral params = getParameters();
		for (int i = 0; i < params.getChildCount(); i++) {
			ASTArrayLiteral.Initializer param = (ASTArrayLiteral.Initializer) params.getChild(i);
			// Stop counting at first default (InitializerExpression with ASTAssign) or rest parameter
			if (param instanceof ASTArrayLiteral.InitializerSpread) {
				break;
			}
			if (param instanceof ASTArrayLiteral.InitializerExpression) {
				ASTArrayLiteral.InitializerExpression expr = (ASTArrayLiteral.InitializerExpression) param;
				if (expr.getNode() instanceof ASTAssign) {
					break;
				}
			}
			paramLength++;
		}

    	super.init(initContext);
	}
  
	public void declare(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, Object[] values, JSResult result) {
		if(parameters!=null) {
			parameters.assign(context, variableFactory, values, result);
		}
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index==0) {
			return parameters;
		}
		return super.getChild(index-1);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index==0) {
			this.parameters = (ASTArrayLiteral)node;
			return;
		}
		super._setChild(index-1,node);
	}

	// A nested function whose (ordinary) body-statement instantiation stays
	// in callVoid, while its class emission naively moved into
	// initGeneratorParams under the generator split, would break: the two
	// must live in the same Java method for the class type to be visible.
	// Nested functions living entirely inside the parameter subtree (e.g. a
	// function expression invoked from a parameter default) don't have this
	// problem - both their class AND their instantiation move together into
	// initGeneratorParams. transpileFunctionBody's splitForGenerator handles
	// this by declaring each nested function's class in whichever method its
	// own instantiation lives in (ASTVarContainer.transpilerDeclareFunctionClasses'
	// filter, keyed off isInsideParameters) rather than refusing to split at
	// all whenever a body-scoped function is present.
	//
	// The one case that filtering can't handle: dispatcher mode (functions.
	// size()>maxFunctionsBeforeDispatcher()) emits ALL of a container's
	// nested functions into one shared switch-dispatcher class hierarchy, not
	// split-by-subset-able without a larger restructure - so the generator
	// split is skipped entirely (falls back to the pre-existing, fully-inline
	// callVoid codegen) whenever that many nested functions are declared
	// directly in a generator body. A vanishingly rare shape in practice.
	private boolean canSplitFunctionClassesForGenerator(JSTranspilerGeneratorContext functionContext) {
		if(!hasFunctionDeclarations()) {
			return true;
		}
		TranspilerCodeSplitter splitter = functionContext.getOptions().getCodeSplitter();
		int max = splitter!=null ? splitter.maxFunctionsBeforeDispatcher() : TranspilerCodeSplitter.DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER;
		return getFunctionDeclarations().size()<=max;
	}

	// Exposes transpileFunctionBody's splitForGenerator condition (minus the
	// always-false-for-generators `!direct` term - see that method's own doc)
	// so ASTVarContainer's dispatcher-mode codegen can tell, per sibling
	// function sharing its switch-dispatcher class, whether this function will
	// emit its own indexed initGeneratorParams_f_N helper - see
	// transpileFunctionBody's isOverride branch.
	public boolean needsInitGeneratorParamsSplit(JSTranspilerGeneratorContext functionContext) {
		return isGenerator() && hasDeclaredVariables() && canSplitFunctionClassesForGenerator(functionContext);
	}

	private boolean isInsideParameters(ASTNode node) {
		for(ASTNode n=node; n!=null; n=n.getParent()) {
			if(n==parameters) {
				return true;
			}
			if(n==this) {
				return false;
			}
		}
		return false;
	}

	// True when this function hasParameterExpressions (!isSimpleParameterList())
	// AND its body declares at least one name of its own beyond the preamble
	// (arguments/self-reference/parameter-bound names - see
	// preambleVariableCount's own field doc) - i.e. exactly the case where a
	// closure written IN the parameter list must not see the function's own
	// body-level declarations. See TranspilerParameterScopeContext's own doc
	// for why/how.
	private boolean needsParamBodySplit() {
		return !isSimpleParameterList() && hasDeclaredVariables() && getVariables().size()>preambleVariableCount;
	}

	// True when this function's OWN body (not a nested function's or class's -
	// see containsLiteralDirectEval's own bail-out) syntactically contains a
	// bare, literal `eval(...)` call (never `eval?.(...)`, never an indirect
	// form like `(0,eval)(...)`) AND this function is genuinely non-strict. Only
	// a NON-STRICT direct eval can leak a new `var` binding into the calling
	// function's own VariableEnvironment, shadowing a same-named outer binding
	// for the rest of that invocation (EvalDeclarationInstantiation, ECMA-262
	// 19.2.1.3 step 5) - a STRICT direct eval's var/function declarations are
	// fully contained in a fresh Environment Record of the eval's own, so they
	// can never be visible to an ordinary identifier read in the calling
	// function at all (see PerformEval's strictEval branch). Used ONLY to
	// decide whether transpileFunctionBody wraps this function's own body
	// statements in a TranspilerEvalShadowContext (see that class's own doc) -
	// the overwhelming common case (no literal eval, or a strict function)
	// leaves this false and the body transpiles against functionContext
	// directly, exactly as before this method existed. See KnownGaps.md's "a
	// read AFTER a same-function direct-eval doesn't see the eval's shadowing
	// var" entry.
	private boolean hasNonStrictDirectEvalInOwnBody() {
		if(isGenuinelyStrictMode()) {
			return false;
		}
		ASTNode[] statements = getStatements();
		if(statements!=null) {
			for(ASTNode s: statements) {
				if(containsLiteralDirectEval(s)) {
					return true;
				}
			}
		}
		return false;
	}

	// Same concern as hasNonStrictDirectEvalInOwnBody(), for the OTHER site a
	// literal non-strict direct eval can appear: a default-value/destructuring-
	// default/computed-key expression in this function's own parameter list
	// (never a nested function/class's parameters - containsLiteralDirectEval's
	// own bail-out already excludes those). Spec 9.2.10
	// FunctionDeclarationInstantiation step 20's own NOTE: "A separate
	// Environment Record is needed to ensure that bindings created by direct
	// eval calls in the formal parameter list are outside the environment
	// where parameters are declared" - a `var` declared this way is visible to
	// a closure created LATER in that same parameter list (test262
	// scope-param-elem-var-open.js and its many siblings across every
	// function-like construct - confirmed against real Node.js, not an
	// obsolete ES5-only assertion). Used to decide whether
	// transpileParameterBindingPrologue's paramContext and
	// getNestedFunctionParentContext's nested-closure context both get
	// wrapped in a TranspilerEvalShadowContext (see that class's own doc).
	private boolean hasNonStrictDirectEvalInOwnParams() {
		if(isGenuinelyStrictMode() || parameters==null) {
			return false;
		}
		return containsLiteralDirectEval(parameters);
	}

	// Recursive helper for hasNonStrictDirectEvalInOwnBody() - never descends
	// into a nested function or class (each has its own separate strictness/
	// scope and is analyzed independently, the same time its OWN
	// transpileFunctionBody runs).
	private static boolean containsLiteralDirectEval(ASTNode node) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction || node instanceof ASTBaseClass) {
			return false;
		}
		if(node instanceof ASTCall call && call.getNode() instanceof ASTIdentifier id
				&& "eval".equals(id.getId()) && !call.isNullOp()) {
			return true;
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(containsLiteralDirectEval(node.getChild(i))) {
				return true;
			}
		}
		return false;
	}

	// A nested function declared INSIDE this function's own parameter list
	// (e.g. a closure created by a default-value expression) must resolve
	// free identifiers as if the enclosing function's own body didn't exist
	// yet - route its codegen parent context through the same filtered view
	// transpileParameterBindingPrologue uses for the parameter list's own
	// expressions. A nested function declared in the BODY instead needs no
	// such PARAMETER-SCOPE filtering (it already correctly sees everything
	// via jsContext unchanged) - but see the eval-shadow wrap below, which
	// DOES apply to body-declared nested functions too.
	@Override
	protected JSTranspilerGeneratorContext getNestedFunctionParentContext(JSTranspilerGeneratorContext jsContext, ASTFunction fct) {
		// Parameter-scope filtering must wrap the ORIGINAL, unwrapped
		// `jsContext` (functionContext) - TranspilerParameterScopeContext
		// compares a VariableDef's own array index against THIS function's
		// own preambleVariableCount, which is meaningless for a VariableDef
		// belonging to some OTHER (enclosing class's self-binding) array -
		// so the enclosing-class wrap (see ASTVarContainer.
		// wrapForEnclosingClasses, which only ever intercepts the class's
		// own name specifically, never anything preambleVariableCount cares
		// about) is layered OUTSIDE it instead, checked first.
		JSTranspilerGeneratorContext ctx = needsParamBodySplit() && isInsideParameters(fct)
				? new TranspilerParameterScopeContext(jsContext, preambleVariableCount)
				: jsContext;
		// A closure declared inside this function's own parameter list (e.g.
		// `f(_ = probe = function(){ return x; })`) OR its own body (e.g.
		// `probeBody = function(){ return x; }` AFTER the param list already
		// ran the eval) must see a same-function parameter-list eval's
		// shadowing var when IT reads a free identifier - see
		// hasNonStrictDirectEvalInOwnParams()'s own doc (the shadow binding
		// outlives the parameter-binding prologue for the rest of this
		// invocation, so both sites need the check, unlike
		// TranspilerParameterScopeContext's own params-only concern just
		// above). Composes with the param/body-split wrap above (order
		// doesn't matter to TranspilerEvalShadowContext.getOwnVariable, a
		// pure pass-through).
		if(hasNonStrictDirectEvalInOwnParams()) {
			ctx = new TranspilerEvalShadowContext(ctx);
		}
		return wrapForEnclosingClasses(ctx, fct);
	}


	// Set ONLY by ASTFor's own "deferred head closure snapshot" detection
	// (see ASTFor.getDeferredIncClosure()'s doc) - null for every other
	// closure in the language, which leaves transpileJavaExpression()'s
	// construction-site snapshot behavior completely unchanged. Non-null
	// names a Java local (declared by ASTFor, typed
	// HeadClosureSnapshotHolder) that this closure's own "new
	// ClassName(...)" construction expression should ALSO assign itself
	// to, so ASTFor's generated code - later in the SAME increment clause -
	// can reach back into this specific instance and overwrite its
	// (deliberately non-final in that case, see
	// ASTVarContainer.transpilerDeclareFunctionClasses) snapshot field
	// with a value computed AFTER a later write this closure must still
	// observe.
	private String deferredHeadSnapshotTempVar;

	public void setDeferredHeadSnapshotTempVar(String tempVar) {
		this.deferredHeadSnapshotTempVar = tempVar;
	}

	public String getDeferredHeadSnapshotTempVar() {
		return deferredHeadSnapshotTempVar;
	}

	//
	// We can't use Lambda to define functions, we must create an object
	// Lambda cannot be stacked with the same parameter names
	//

	@Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		ASTVarContainer container = findParentNodeByClass(ASTVarContainer.class);
		String className = container.getFunctionEmittedClassName(jsContext, indexInRoot);
		// See ASTVarContainer.needsHeadClosureSnapshot()'s own doc: this
		// function's class was declared (once, see
		// transpilerDeclareFunctionClasses) with an extra constructor
		// parameter for a snapshot of `container`'s own variable array -
		// pass a defensive COPY of WHATEVER that array currently resolves
		// to right here, at this specific instantiation site (which may be
		// the shared outer array, or a per-iteration redirect - exactly
		// mirroring what a plain, non-closure identifier reference
		// transpiled at this same point would read).
		String snapshotArg = "";
		if(container.needsHeadClosureSnapshot()) {
			snapshotArg = StringFormat.format(",java.util.Arrays.copyOf({0},{1})",
					container.getVariables().getJavaVariable(), container.getVariables().size());
		}
		// jsContext here is the ENCLOSING scope where this function-value literal
		// appears (this call only builds the "new F1_fN(...)" construction
		// expression - the function's own body is generated separately, see
		// transpileFunctionExpression below, with its own fresh context that
		// always resets back to the plain _ctx name). A class body with private
		// members overrides getContextJavaName() so every method/getter/setter/
		// constructor captures the class's private-name-resolving scope instead
		// of the ambient _ctx - see TranspilerGeneratorFunctionContext.
		// Function.prototype.toString() source fidelity in transpiled mode:
		// compute the exact original source text's [start,end) offsets ONCE,
		// here, at transpile time (extractOriginalSourceRange() is pure
		// AST+source-text computation) and bake in just those two ints via the
		// generated subclass's constructor, mirroring how name/length/index are
		// already passed as literals. The offsets index the unit's own source,
		// which the JSTranspiledUnit already carries in full (SourceCode.CODE) -
		// so BuiltinFunctionTranspiler.getOriginalSource() reslices it on demand
		// rather than each function holding its own duplicated substring copy.
		// -1,-1 (no valid range - findSourceCode() null or offsets out of range)
		// makes getOriginalSource() fall back to the `[unavailable]` placeholder.
		int[] srcRange = extractOriginalSourceRange(findSourceCode());
		String construction = StringFormat.format("new {0}({1},{2},{3},{4},{5},{6},{7}{8})",
				className,
				jsContext.getContextJavaName(),
				getModifiers(),
				ASTLiteral.encodeString(getFunctionName()),
				getParamLength(),
				indexInRoot,
				srcRange!=null ? srcRange[0] : -1,
				srcRange!=null ? srcRange[1] : -1,
				snapshotArg);
		if(deferredHeadSnapshotTempVar!=null) {
			// See this field's own doc above - route the freshly constructed
			// instance through the temp holder ASTFor declared, in addition
			// to returning it normally (a parenthesized Java assignment
			// expression evaluates to the assigned value), so ASTFor's own
			// later-in-the-same-clause code can find it again.
			return StringFormat.format("({0} = {1})", deferredHeadSnapshotTempVar, construction);
		}
		return construction;
	}

	// True when `this` IS (not just is nested inside) a class's own
	// "constructor" method body - walks up through non-ASTFunction ancestors
	// only, so a plain function/arrow NESTED inside a constructor's body
	// doesn't get mistaken for the constructor itself.
	public boolean isClassConstructor() {
		return findEnclosingClassMethodIfConstructor()!=null;
	}

	// True when `this` is a DERIVED class's own constructor specifically
	// (isClassConstructor() AND the enclosing class has a ClassHeritage) -
	// only a derived constructor's explicit non-object return value needs
	// GetThisBinding()-style handling (10.2.2 [[Construct]] step 13); a base
	// constructor's non-object return is simply ignored in favor of the
	// already-created `this` (BuiltinClassConstructor.constructObject()'s
	// non-derived branch already does this correctly, unconditionally).
	public boolean isDerivedClassConstructor() {
		ASTClassMethod m = findEnclosingClassMethodIfConstructor();
		if(m==null) {
			return false;
		}
		for(ASTNode n=m.getParent(); n!=null; n=n.getParent()) {
			if(n instanceof ASTBaseClass cls) {
				return cls.getSuperClass()!=null;
			}
		}
		return false;
	}

	private ASTClassMethod findEnclosingClassMethodIfConstructor() {
		for(ASTNode n=getParent(); n!=null && !(n instanceof ASTFunction); n=n.getParent()) {
			if(n instanceof ASTClassMethod m) {
				return (m.getFunctionDecl()==this && m.isConstructor()) ? m : null;
			}
		}
		return null;
	}

	// The class whose `this` this function shares - i.e. this function IS
	// that class's own constructor or a non-static method of it (a static
	// method's `this` is the class/constructor object itself, not an
	// instance - deliberately excluded). Used for ASTThis.getReturnedType()'s
	// "this is an instance of class X" inference - see JSType.ofConstructor().
	public ASTBaseClass getEnclosingInstanceMethodClass() {
		for(ASTNode n=getParent(); n!=null && !(n instanceof ASTFunction); n=n.getParent()) {
			if(n instanceof ASTClassMethod m) {
				if(m.getFunctionDecl()!=this || m.isStatic()) {
					return null;
				}
				for(ASTNode c=m.getParent(); c!=null; c=c.getParent()) {
					if(c instanceof ASTBaseClass cls) {
						return cls;
					}
				}
				return null;
			}
		}
		return null;
	}

	// Staging local + labeled block used to defer a derived constructor's
	// explicit-return-value validation (10.2.2 [[Construct]] step 13) until
	// AFTER the whole method body - including any of its own try/catch/
	// finally - has finished, so the resulting TypeError/ReferenceError is
	// not catchable by the constructor's own generated catch blocks (test262
	// derived-class-return-override-catch*.js) and sees `this` as updated by
	// any super() call the body made along the way (ASTSuperCtor reassigns
	// the `_this` parameter directly). transpileFunctionBody() names these
	// first (see its use below); ASTReturn reads the same names back via the
	// enclosing ASTFunction it already looks up.
	private String derivedCtorReturnVar;
	private String derivedCtorReturnLabel;

	String getDerivedCtorReturnVar(JSTranspilerGeneratorContext jsContext) {
		ensureDerivedCtorReturnNames(jsContext);
		return derivedCtorReturnVar;
	}
	String getDerivedCtorReturnLabel(JSTranspilerGeneratorContext jsContext) {
		ensureDerivedCtorReturnNames(jsContext);
		return derivedCtorReturnLabel;
	}
	private void ensureDerivedCtorReturnNames(JSTranspilerGeneratorContext jsContext) {
		if(derivedCtorReturnVar==null) {
			derivedCtorReturnVar = jsContext.generateUniqueId("derivedCtorReturn_");
			derivedCtorReturnLabel = jsContext.generateUniqueId("derivedCtorReturnLabel_");
		}
	}

	// Returns true if the last statement of `statements` unconditionally
	// exits the enclosing function *as far as Java's flow analysis sees it*.
	// Used to decide whether to emit the implicit `return UNDEFINED;` fall-
	// through -- we must skip it when Java would call it unreachable, and we
	// must NOT skip it when Java can't prove the body always returns.
	//
	// Key subtlety: ASTThrow transpiles to a plain method call
	// (JSRuntimeException.throwJavaException(...)), so Java does NOT treat it
	// as terminating -- we must still emit the trailing return after it.
	// Only forms that produce Java `return`/`switch`/etc. structures with
	// provable no-fallthrough count as exit.
	private static boolean endsWithUnconditionalExit(ASTNode[] statements) {
		if(statements==null || statements.length==0) {
			return false;
		}
		return endsWithUnconditionalExit(statements[statements.length-1]);
	}
	private static boolean endsWithUnconditionalExit(ASTNode node) {
		node = ASTNode.skipTransparent(node);
		if(node instanceof ASTReturn) {
			return true;
		}
		if(node instanceof ASTBlock block) {
			return endsWithUnconditionalExit(block.getStatements());
		}
		if(node instanceof ASTWith withNode) {
			// `with(obj) { ... }` transpiles to a plain, unconditionally-
			// entered Java block wrapping the body - reachability-wise
			// identical to ASTBlock above, so it exits iff its body does
			// (test262 language/statements/with/12.10-0-10.js and 11 more:
			// `with (o) { return x; }` as a function's LAST statement left an
			// unreachable trailing `return UNDEFINED;` after it, failing
			// javac outright).
			return endsWithUnconditionalExit(withNode.getBodyNode());
		}
		if(node instanceof ASTIf ifNode) {
			ASTNode elseNode = ifNode.getElseNode();
			if(elseNode==null) {
				return false;
			}
			return endsWithUnconditionalExit(ifNode.getThenNode()) && endsWithUnconditionalExit(elseNode);
		}
		if(node instanceof ASTSwitch switchNode) {
			// A JS switch transpiles to a Java `switch(indexN)` with numeric
			// cases 0..k-1 + optional default. Java treats the code after the
			// switch as unreachable iff every possible control path through
			// the switch exits abruptly. Since Java lets cases fall through,
			// it's sufficient that:
			//   1. a `default:` exists (else index=-1 falls out the switch),
			//   2. the LAST case's statements end with unconditional exit
			//      (empty preceding cases fall through into it), AND
			//   3. no case body contains a top-level `break;` that would
			//      target this switch (that would let the switch complete
			//      normally).
			ASTNode[] cases = switchNode.getCases();
			if(cases.length==0) {
				return false;
			}
			boolean hasDefault = false;
			for(ASTNode c : cases) {
				ASTCase caseNode = (ASTCase)c;
				if(caseNode.getExprNode()==null) {
					hasDefault = true;
				}
				ASTNode[] stmts = caseNode.getStatements();
				if(stmts!=null) {
					for(ASTNode stmt : stmts) {
						if(bodyCanBreakOut(stmt,switchNode.getLabel())) {
							return false;
						}
					}
				}
			}
			if(!hasDefault) {
				return false;
			}
			ASTCase lastCase = (ASTCase)cases[cases.length-1];
			return endsWithUnconditionalExit(lastCase.getStatements());
		}
		if(node instanceof ASTTry tryNode) {
			// A try/finally where the finally unconditionally exits is
			// unreachable-past regardless of the try body.
			ASTBlock finallyNode = tryNode.getFinallyNode();
			if(finallyNode!=null && endsWithUnconditionalExit(finallyNode.getStatements())) {
				return true;
			}
			// Otherwise: body AND every catch must exit.
			if(!endsWithUnconditionalExit(tryNode.getBodyNode())) {
				return false;
			}
			ASTCatch catchNode = tryNode.getCatchNode();
			if(catchNode!=null && !endsWithUnconditionalExit(catchNode.getBodyNode())) {
				return false;
			}
			return true;
		}
		// An infinite loop whose body has no reachable `break` targeting it
		// completes only via return/throw -- Java flow analysis flags the
		// following statement as unreachable, so we must NOT emit the implicit
		// trailing `return UNDEFINED;`.
		if(node instanceof ASTWhile whileNode) {
			if(isConstantTrue(whileNode.getTestNode()) && !bodyCanBreakOut(whileNode.getBodyNode(),whileNode.getLabel())) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTDoWhile doNode) {
			if(isConstantTrue(doNode.getTestNode()) && !bodyCanBreakOut(doNode.getBodyNode(),doNode.getLabel())) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTFor forNode) {
			ASTNode test = forNode.getTestNode();
			if((test==null || isConstantTrue(test)) && !bodyCanBreakOut(forNode.getBodyNode(),forNode.getLabel())) {
				return true;
			}
			return false;
		}
		return false;
	}

	private static boolean isConstantTrue(ASTNode node) {
		node = ASTNode.skipTransparent(node);
		return node instanceof ASTLiteral lit && Boolean.TRUE.equals(lit.getValue());
	}

	// True if `body` contains at least one `break` statement that would exit
	// the loop whose label is `loopLabel` (null for an unlabeled loop). Nested
	// loops/switches absorb an unlabeled break; a labeled break is absorbed
	// only by an intervening statement carrying that same label. Function/
	// class bodies stop the walk -- break cannot cross a function boundary.
	private static boolean bodyCanBreakOut(ASTNode body, String loopLabel) {
		if(body==null) {
			return false;
		}
		return breakOutWalk(body,loopLabel,0,null);
	}
	private static boolean breakOutWalk(ASTNode node, String loopLabel, int unlabeledDepth, java.util.Set<String> absorbedLabels) {
		if(node==null) {
			return false;
		}
		if(node instanceof ASTFunction) {
			// break cannot escape a function boundary.
			return false;
		}
		if(node instanceof ASTBreak brk) {
			String l = brk.getLabel();
			if(l==null) {
				// Unlabeled break exits the innermost enclosing loop/switch --
				// if we've entered one during the walk, it's absorbed there.
				// Otherwise it exits the loop we're analyzing.
				return unlabeledDepth==0;
			}
			// Labeled break exits the statement carrying `l`. If we crossed
			// that label on the way down, it's absorbed. Otherwise it exits
			// our loop iff `l` matches our loop's own label.
			if(absorbedLabels!=null && absorbedLabels.contains(l)) {
				return false;
			}
			return l.equals(loopLabel);
		}
		// Descending into a nested loop or switch absorbs unlabeled breaks
		// inside it. ASTFor is the classic for(;;) loop; ASTFor_ is the base
		// of ASTForIn/ASTForOf - both flavors act as sinks.
		boolean introducesUnlabeledSink = node instanceof ASTWhile || node instanceof ASTDoWhile
				|| node instanceof ASTFor || node instanceof ASTFor_ || node instanceof ASTSwitch;
		int nextDepth = introducesUnlabeledSink ? unlabeledDepth+1 : unlabeledDepth;
		// A labeled statement absorbs `break L;` for its own label.
		String ownLabel = null;
		if(node instanceof ILabeledNode labeled) {
			ownLabel = labeled.getLabel();
		}
		java.util.Set<String> nextAbsorbed = absorbedLabels;
		if(StringUtil.isNotEmpty(ownLabel)) {
			nextAbsorbed = absorbedLabels!=null ? new java.util.HashSet<>(absorbedLabels) : new java.util.HashSet<>();
			nextAbsorbed.add(ownLabel);
		}
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			if(breakOutWalk(node.getChild(i),loopLabel,nextDepth,nextAbsorbed)) {
				return true;
			}
		}
		return false;
	}

	public String transpileFunctionExpression(JSTranspilerGeneratorContext jsContext, String className, String functionName) {
        JSTranspilerGeneratorContext functionContext = new TranspilerGeneratorFunctionContext(jsContext);
  		TranspilerJavaBuilder b = functionContext.createJavaBuilder();

  		JSTranspilerMap map = jsContext.getTranspilerMap();
		map.pushBlock(b.getCurrentLine());

  		// Declare the function in the parent context
  		String fctName = getFunctionName();

		b.println("// {0}, {1}.f_{2}", StringUtil.isNotEmpty(fctName)?fctName:"<anonymous>", className, indexInRoot);
		b.println("// line: {0}, col: {1}", getBeginLine(), getBeginCol());

		// Direct-arg dispatch: emit an arity-N callVoidN(ctx,_this,Object p_0,...)
		// (fast path - no Object[] allocation, params bound directly from method
		// locals) AND a trivial Object[]-form callVoid stub that delegates to
		// callVoidN (fallback for spread callers, external Callable.call(_this,
		// Object[]) invocations, and arity mismatches). Emitting a delegating
		// stub instead of the full body a second time keeps generated class size
		// down without changing semantics: initArg(_args, i) returns UNDEFINED
		// when i >= _args.length, matching the missing-args path the duplicated
		// body used to take. Enabled when:
		//   - this is the sole function in its emitting class (functionName == "callVoid" -
		//     ASTVarContainer emits a switch-based dispatcher for multi-function classes,
		//     which stays on the Object[] path),
		//   - the parameter list is simple identifiers (no defaults, destructuring,
		//     rest, or `arguments` object) with arity <= Callable.MAX_DIRECT_ARITY,
		//   - the function is context-elidable - piggybacking on that predicate
		//     because it already excludes nested inner functions / classes /
		//     generators / async / method / arrow / `this`/`super`/`new.target`,
		//     ALL of which would either cause duplicate class-declaration emission
		//     if the body ran twice (nested functions/classes emit a class into the
		//     enclosing method body) or would require identical `this` binding across
		//     both variants (which the fast path bypasses).
		// Caller-side, transpiled call sites route directly to
		// Callable.call(_this, p1,...,pN), which BuiltinFunctionTranspiler overrides
		// to hit callVoidN without ever building an Object[].
		int arity = getParamLength();
		int declared = getParameters().getChildCount();
		boolean directArg = "callVoid".equals(functionName)
				&& !useArguments
				&& isSimpleParameterList()
				&& declared == arity
				&& arity <= Callable.MAX_DIRECT_ARITY
				&& isTranspiledContextElidable();

		// When functionName is "callVoid" this is the sole function in the
		// enclosing class - it overrides BuiltinFunctionTranspiler's own
		// callVoid[N] stubs, so emit @Override. When functionName is "f_N",
		// it's a numbered method invoked by the dispatcher (not an override).
		boolean isOverride = "callVoid".equals(functionName);
		if(directArg) {
			transpileFunctionBody(functionContext, b, functionName, fctName, /*direct*/true, isOverride);
			b.println();
			transpileCallVoidStub(b, arity, isOverride);
		} else {
			transpileFunctionBody(functionContext, b, functionName, fctName, /*direct*/false, isOverride);
		}

		map.popBlock(b.getCurrentLine());

		return b.toString();
    }

	// Emits the delegating `callVoid(ctx, _this, Object[] _args)` stub that
	// forwards to callVoidN. Used as the Object[]-shaped fallback next to the
	// direct-arg fast path emitted by transpileFunctionBody. Missing args map
	// to UNDEFINED via initArg's own bounds check, matching the semantics the
	// duplicated body path used to produce.
	private void transpileCallVoidStub(TranspilerJavaBuilder b, int arity, boolean isOverride) {
		if(isOverride) {
			b.println("@Override");
		}
		b.println("public Object callVoid({0} {1}, Object {2}, Object[] {3}) {", JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, JSTranspiler.THIS_VAR, JSTranspiler.FUNCTION_ARGUMENTS);
		b.incIndent();
			StringBuilder call = new StringBuilder();
			call.append("return callVoid").append(arity).append("(");
			call.append(JSTranspiler.MAIN_CONTEXT).append(", ").append(JSTranspiler.THIS_VAR);
			for(int i=0; i<arity; i++) {
				call.append(", initArg(").append(JSTranspiler.FUNCTION_ARGUMENTS).append(", ").append(i).append(")");
			}
			call.append(");");
			b.println(call.toString());
		b.decIndent();
		b.println("}");
	}

	// Emits a single callVoid variant. When direct is true, emits
	// `callVoidN(ctx, _this, Object p_0,...,Object p_{N-1})` and reads each
	// parameter directly from its positional local; when false, emits the
	// original `callVoid(ctx, _this, Object[] _args)` and reads via initArg.
	// The body between the parameter-binding prologue and the final `return`
	// is identical in both variants.
	private void transpileFunctionBody(JSTranspilerGeneratorContext functionContext, TranspilerJavaBuilder b, String methodName, String fctName, boolean direct, boolean isOverride) {
		int arity = getParamLength();

		// A generator function must run FunctionDeclarationInstantiation
		// (parameter binding + var/function hoisting) synchronously at call
		// time, before the generator object is even returned - only the
		// body's statements are genuinely lazy, deferred until first next()
		// (see BuiltinFunctionTranspiler.call()'s isGenerator() branch and
		// initGeneratorParams()). Emit the prologue as a separate, eagerly-
		// invoked method for that case; `direct` is always false for
		// generators already (isTranspiledContextElidable() excludes them),
		// kept as a belt-and-suspenders guard. A generator with no declared
		// variables (no params, no locals) has nothing to bind eagerly, so
		// the base no-op initGeneratorParams is correct as-is - skip the split.
		// See canSplitFunctionClassesForGenerator's doc for the (rare)
		// dispatcher-mode case where the split is still skipped entirely.
		boolean splitForGenerator = isGenerator() && !direct && hasDeclaredVariables() && canSplitFunctionClassesForGenerator(functionContext);

		if(splitForGenerator) {
			// In dispatcher mode (isOverride==false, methodName=="f_N") this
			// class also holds every OTHER sibling function sharing the same
			// switch-dispatcher class (see ASTVarContainer.
			// transpilerDeclareFunctionClasses) - a fixed "initGeneratorParams"
			// name would collide (duplicate method) as soon as a second sibling
			// generator also needs this split, since they're all methods of the
			// SAME class rather than of their own separate classes. Give each
			// one an index-qualified helper name instead; ASTVarContainer emits
			// the actual @Override initGeneratorParams(...) once per class,
			// switching on `index` to reach the right helper - mirroring how
			// its callVoid dispatcher already switches on `index` to reach f_N.
			String initMethodName = isOverride ? "initGeneratorParams" : ("initGeneratorParams_" + methodName);
			if(isOverride) {
				b.println("@Override");
			}
			b.println("protected void {0}({1} {2}, Object {3}, Object[] {4}) {", initMethodName, JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT, JSTranspiler.THIS_VAR, JSTranspiler.FUNCTION_ARGUMENTS);
			b.incIndent();
			// Only the nested function classes actually NEEDED here (those
			// referenced by the parameter-binding prologue itself, i.e. living
			// inside the parameter subtree) are declared in this method - see
			// isInsideParameters/transpilerDeclareFunctionClasses. A
			// body-scoped nested function's class is declared later, in
			// callVoid, right next to the (still lazy, unmoved) body statement
			// that instantiates it.
			transpileParameterBindingPrologue(functionContext, b, fctName, direct, this::isInsideParameters);
			b.println("{0}.setLocals({1});", JSTranspiler.MAIN_CONTEXT, getVariables().getJavaVariable());
			b.decIndent();
			b.println("}");
			b.println();
		}

		if(isOverride) {
			b.println("@Override");
		}
		if(direct) {
			// Note: callVoidN is a distinct method name per arity (0..10) declared in
			// BuiltinFunctionTranspiler, not an overload on `callVoid`.
			StringBuilder sig = new StringBuilder();
			sig.append("public Object callVoid").append(arity).append("(");
			sig.append(JSTranspiledFunctionRuntimeContext.class.getSimpleName()).append(" ").append(JSTranspiler.MAIN_CONTEXT);
			sig.append(", Object ").append(JSTranspiler.THIS_VAR);
			for(int i=0; i<arity; i++) {
				sig.append(", Object p_arg_").append(i);
			}
			sig.append(") {");
			b.println(sig.toString());
		} else {
			b.println("public Object {0}({1} {2}, Object {3}, Object[] {4}) {", methodName, JSTranspiledFunctionRuntimeContext.class.getSimpleName(), JSTranspiler.MAIN_CONTEXT,JSTranspiler.THIS_VAR,JSTranspiler.FUNCTION_ARGUMENTS);
		}

		b.incIndent();

		if(splitForGenerator) {
			// Locals were already allocated and populated eagerly by
			// initGeneratorParams() above - just read them back.
			b.println("final Object[] {0} = {1}.getLocals();", getVariables().getJavaVariable(), JSTranspiler.MAIN_CONTEXT);
			// Declare the REMAINING nested function classes - the body-scoped
			// ones initGeneratorParams' filtered prologue call skipped - right
			// here, in the same position/method they occupied before the
			// split (immediately ahead of the body statements that instantiate
			// them, still an ordinary, lazy body statement - see
			// canSplitFunctionClassesForGenerator's doc).
			transpilerDeclareFunctionClasses(functionContext, b, fct -> !isInsideParameters(fct));
		} else {
			transpileParameterBindingPrologue(functionContext, b, fctName, direct, null);
		}

		ASTNode[] statements = getStatements();
		boolean derivedCtor = isDerivedClassConstructor();
		String returnVar = null, returnLabel = null;
		if(derivedCtor) {
			// See getDerivedCtorReturnVar()'s field comment: stage the
			// explicit-return value and break past the whole body (including
			// its own try/catch/finally) instead of returning from inside
			// it, so ASTReturn's checkDerivedConstructorReturn() call below
			// runs at [[Construct]]-caller timing, not inline.
			returnVar = getDerivedCtorReturnVar(functionContext);
			returnLabel = getDerivedCtorReturnLabel(functionContext);
			b.println("Object {0} = UNDEFINED;", returnVar);
			b.println("{0}: {", returnLabel);
			b.incIndent();
		}

		// Route identifier reads inside the body statements through a narrow,
		// conditionally-allocated view when either this function's own body
		// OR its own parameter list syntactically contains a literal,
		// non-strict direct eval() call - see TranspilerEvalShadowContext's
		// own doc, hasNonStrictDirectEvalInOwnBody()'s and
		// hasNonStrictDirectEvalInOwnParams()'s. A parameter-list eval's
		// shadow binding is visible for the REST of this invocation
		// (spec: the param environment is an ancestor of/equal to the body's
		// own scope), so body code needs the same runtime check a body-level
		// eval would need - test262 scope-param-*-var-close.js's own
		// `probeBody` (declared directly in the body, not the params). The
		// overwhelming common case (neither) leaves bodyContext===
		// functionContext, a complete no-op - identical codegen to before
		// this existed.
		JSTranspilerGeneratorContext bodyContext = hasNonStrictDirectEvalInOwnBody() || hasNonStrictDirectEvalInOwnParams()
				? new TranspilerEvalShadowContext(functionContext)
				: functionContext;

		if(ASTBlock.hasUsingDeclarations(this)) {
			// A using/await-using declared directly in the function body (no
			// intervening nested Block) - ASTBlock's own disposal-boundary
			// wrapping never runs for this position since a function body
			// isn't an ASTBlock instance (see ASTRootStatementList), so it's
			// duplicated here against the same functionContext instead.
			ASTBlock.transpileStatementsWithDisposal(bodyContext, bodyContext, b, this, statements);
		} else {
			ASTBlock.transpileBlockStatements(bodyContext,b,this,statements);
		}

		if(derivedCtor) {
			b.decIndent();
			b.println("}");
			// Reached either by falling off the end of the labeled block (no
			// explicit return encountered - returnVar is still UNDEFINED,
			// same as the base-constructor fall-through case) or via a
			// `break returnLabel;` from ASTReturn after staging the return
			// value - checkDerivedConstructorReturn() itself already handles
			// the UNDEFINED case via GetThisBinding() (10.2.2 step 13.b), so
			// both paths converge correctly on one call. Read `this` fresh
			// from the context (not the local `_this` parameter): a super()
			// call reached through a nested arrow (e.g. `(() => super())()`)
			// updates the constructor's OWN JSFunctionContext directly
			// (RuntimeUtil.superCtor() walks up past arrow contexts to call
			// setThis() there - see its comment), but has no way to reach
			// back into this outer method's local `_this` variable, which is
			// a plain Java parameter private to this call frame.
			b.println("return checkDerivedConstructorReturn({0},{1}.getThis());", returnVar, JSTranspiler.MAIN_CONTEXT);
			b.decIndent();
			b.println("}");
			return;
		}

		// Implicit fall-through return: the callVoid contract is that a
		// function's method body must return the JS return value directly.
		// A missing `return` statement in the source produces `undefined` -
		// EXCEPT for a class constructor (10.2.1.1 OrdinaryCallEvaluateBody:
		// "Return ? constructorEnv.GetThisBinding()"), where it must instead
		// re-check/return `this` - throwing ReferenceError if a DERIVED
		// constructor never called super() (test262 language/statements/
		// class/subclass/builtin-objects/*/super-must-be-called.js and
		// neighbors: an empty `constructor() {}` body must throw, not
		// silently return undefined). Skip emitting this when the body
		// already unconditionally exits, or Java's compiler flags it as an
		// unreachable statement.
		if(!endsWithUnconditionalExit(statements)) {
			if(isClassConstructor()) {
				b.println("return checkThisBinding({0});", JSTranspiler.THIS_VAR);
			} else {
				b.println("return UNDEFINED;");
			}
		}

		b.decIndent();
		b.println("}");
	}

	// Parameter/local-variable declaration, `arguments` object creation, the
	// empty-object-pattern-parameter coercibility pre-pass, parameter
	// destructuring, and the recursive-self-reference assignment - i.e.
	// everything FunctionDeclarationInstantiation covers. For an ordinary
	// function this is emitted inline at the top of callVoid; for a
	// generator (see transpileFunctionBody's splitForGenerator) it's emitted
	// into the separate, eagerly-invoked initGeneratorParams() instead - in
	// which case functionClassFilter restricts the nested function classes
	// declared here to the ones actually needed eagerly (see
	// isInsideParameters/transpilerDeclareFunctionClasses); null (every
	// other caller) declares all of them, as before.
	private void transpileParameterBindingPrologue(JSTranspilerGeneratorContext functionContext, TranspilerJavaBuilder b, String fctName, boolean direct, Predicate<ASTFunction> functionClassFilter) {
		// Pre-declare the variables and inner functions - see
		// preambleVariableCount's own field comment for why this can't be a
		// simple "2+getParamLength()" formula (arguments/self-reference/
		// parameter-bound names are handled by this method's own codegen
		// below instead, not the generic UNDEFINED-fill every other,
		// body-declared slot gets).
		transpilerDeclareStatement(functionContext,b,preambleVariableCount,functionClassFilter);

		// Per spec (9.2.10 FunctionDeclarationInstantiation steps 26-27): when
		// this function hasParameterExpressions (!isSimpleParameterList()) AND
		// its body declares at least one name of its own (preambleVariableCount
		// doesn't cover the whole of getVariables() - see that field's own
		// doc: everything from preambleVariableCount onward is body-declared),
		// a closure created BY an expression in the parameter list below must
		// NOT see those body-level declarations - it needs a separate
		// parameter Environment Record. Route every expression transpiled
		// below (default values, destructuring defaults/computed keys) through
		// a filtered view instead of functionContext directly - see
		// TranspilerParameterScopeContext's own doc. The overwhelming common
		// case (simple parameter list, or no body-level declarations to hide)
		// leaves paramContext===functionContext, a complete no-op.
		JSTranspilerGeneratorContext paramScopeContext = needsParamBodySplit()
				? new TranspilerParameterScopeContext(functionContext, preambleVariableCount)
				: functionContext;
		// Mirrors bodyContext's own TranspilerEvalShadowContext wrap in
		// transpileFunctionBody, for a literal non-strict direct eval in THIS
		// parameter list instead of the body - see
		// hasNonStrictDirectEvalInOwnParams()'s own doc. Every default-value/
		// destructuring expression below (transpiled against paramContext) is
		// covered, plus any closure created by one (via
		// getNestedFunctionParentContext's own matching wrap).
		final JSTranspilerGeneratorContext paramContext = hasNonStrictDirectEvalInOwnParams()
				? new TranspilerEvalShadowContext(paramScopeContext)
				: paramScopeContext;

		// Every actual parameter slot starts in the TDZ (spec 9.2.12
		// FunctionDeclarationInstantiation step 23-25's IteratorBindingInitialization
		// binds params strictly left-to-right into an initially-uninitialized
		// environment) until this method's own binding loop below overwrites
		// it - so a default-value expression referencing itself or a later,
		// not-yet-bound parameter throws ReferenceError (test262
		// dflt-params-ref-self.js/dflt-params-ref-later.js) instead of
		// silently reading a stale/null value. `arguments` (VAR_TYPE.SYSTEM)
		// and the function's own self-reference binding (VAR_TYPE.PREDECLARED
		// for a statement, assigned separately below) are deliberately NOT
		// touched here - only names bound by the actual parameter list are.
		//
		// This pre-fill only matters when a parameter's TDZ state is
		// OBSERVABLE, i.e. some default-value / destructuring-default /
		// computed-key expression can run between two bindings and read a
		// not-yet-bound param. A simple parameter list has no such
		// expressions: params are bound strictly positionally straight from
		// their argument values, with no interleaved user code, so no slot
		// can ever be read before its own binding overwrites it. The TDZ
		// stores would all be dead - skip them entirely for that (common)
		// case. (A duplicate simple-list name like `f(a,a)` shares one slot,
		// last binding wins, still never observed uninitialized.)
		if(!isSimpleParameterList()) {
			parameters.forEachVarName((name) -> {
				VariableDef paramVar = functionContext.getOwnVariable(name);
				if(paramVar!=null) {
					b.println("{0} = TDZ;", paramVar.getJavaVariableValue());
				}
			});
		}

		// The function's own self-reference binding (named function
		// expression, or a statement's PREDECLARED name) must be visible to
		// default-parameter expressions - e.g. `function f(a = f) {}` or, per
		// test262 generator-created-after-decl-inst.js,
		// `var g = function*(a = (g.prototype = null)) {}`. Per spec this
		// binding lives in an environment enclosing the parameter list, so it
		// has to be assigned BEFORE the parameter-binding loop below, not
		// after (a parameter sharing the same name still wins - checked here
		// via the same forEachVarName enumeration the TDZ loop above uses,
		// instead of a during-the-loop side effect, precisely so this can run
		// first).
		boolean[] fctNameConflictHolder = {false};
		if(StringUtil.isNotEmpty(fctName)) {
			parameters.forEachVarName((name) -> {
				if(name.equals(fctName)) {
					fctNameConflictHolder[0] = true;
				}
			});
			if(!fctNameConflictHolder[0]) {
				VariableDef selfVariable = getOwnVariable(fctName);
				if(selfVariable!=null && (selfVariable.getVarType()==VAR_TYPE.PREDECLARED || selfVariable.getVarType()==VAR_TYPE.FUNCTION_SELF)) { // If this is the function itself...
					selfVariable.setTranspilerDeclared(true);
					b.println("{0} = this; // This function", selfVariable.getJavaVariableValue() );
				} else if(selfVariable!=null) {
					// The body has its own `var`/function declaration of this same
					// name (ASTVarContainer.addVarDeclaration's FUNCTION_SELF
					// exemption already merged its VAR_TYPE away from
					// PREDECLARED/FUNCTION_SELF above, at parse time) - it shadows
					// the self-reference binding entirely, so this slot must NOT
					// get "= this" - but it's still counted within
					// preambleVariableCount (its slot index was assigned before
					// the body was even processed), so transpilerDeclareStatement's
					// blanket JSVar.initVars(...) UNDEFINED-fill never touches it
					// either. Without this explicit init it stays raw Java null
					// forever (test262 language/expressions/call/scope-var-open.js:
					// `var f = function f() { var f; return f; }` read back null
					// instead of undefined).
					selfVariable.setTranspilerDeclared(true);
					b.println("{0} = UNDEFINED; // body var/function shadows self-reference", selfVariable.getJavaVariableValue() );
				}
			}
		}

		if(useArguments) {
			VariableDef va = findVariable(Arguments.ARGUMENTS);
			// A genuinely-strict-mode function (or one with a non-simple
			// parameter list, which is always strict for this purpose per
			// spec) must get a "callee" ACCESSOR property poisoned with the
			// shared %ThrowTypeError% intrinsic, not a plain data property -
			// mirrors InterpretedFunctionRuntimeContext's identical
			// `isGenuinelyStrictMode() || !isSimpleParameterList()`
			// computation. Both conditions are static (AST-known), so this
			// bakes in a compile-time literal rather than a runtime check.
			boolean poisonPillCallee = isGenuinelyStrictMode() || !isSimpleParameterList();
			if(!poisonPillCallee) {
				// Statically ELIGIBLE for a MAPPED arguments object (spec
				// 9.4.4 CreateMappedArgumentsObject: non-strict + simple
				// parameter list). "Non-strict" can't be fully resolved here
				// though: isGenuinelyStrictMode()==false only means this
				// function isn't LEXICALLY strict - the owning JSEnvironment
				// can still force isStrictMode()==true dialect-wide (see
				// isGenuinelyStrictMode() vs isStrictMode() elsewhere in this
				// file), and one compiled class can run under different
				// JSEnvironment instances with different settings. So the
				// mapped-vs-unmapped choice itself has to be a runtime
				// branch - the mapped VarAccessor[] literal (built once,
				// below, one JSVarRef per simple parameter's own local-array
				// slot - same pattern as ASTCall.getVariableJavaReferences)
				// is only ever used in the non-strict runtime branch. The
				// strict-toggle-only branch falls back to unmapped, but
				// mirrors InterpretedFunctionRuntimeContext's own
				// `strictMode?UNDEFINED:function` + static-only `poison`
				// exactly: callee is a plain UNDEFINED VALUE, NOT poison-
				// pilled - the %ThrowTypeError% poison pill is reserved for
				// a GENUINELY (lexically) strict function or a non-simple
				// parameter list (both already excluded from this branch by
				// the enclosing `if(!poisonPillCallee)`), never for the
				// env-wide dialect toggle alone (own test suite's
				// ArgumentsTest.js asserts `arguments.callee === undefined`,
				// readable without throwing, for a plain `function f(){}`
				// under this toggle).
				StringBuilder mapped = new StringBuilder("new VarAccessor[]{");
				String[] simpleNames = parameters.getSimpleParameterNames();
				for(int i=0; i<simpleNames.length; i++) {
					if(i>0) {
						mapped.append(",");
					}
					String name = simpleNames[i];
					VariableDef paramVar = name!=null ? functionContext.getOwnVariable(name) : null;
					if(paramVar!=null) {
						mapped.append("JSVarRef.of(").append(JSTranspiler.literal(name)).append(",")
							.append(paramVar.getJavaVariableArray()).append(",")
							.append(paramVar.getJavaVariableIndex()).append(",VAR_TYPE.")
							.append(paramVar.getVarType().name()).append(")");
					} else {
						mapped.append("null");
					}
				}
				mapped.append("}");
				b.println("if(!isStrictMode()) { {0} = createArguments({1},this,{2}); } else { {0} = createArguments({1},UNDEFINED,false); }",
					va.getJavaVariableValue(), JSTranspiler.FUNCTION_ARGUMENTS, mapped.toString());
			} else {
				b.println("{0} = createArguments({1},isStrictMode()?UNDEFINED:this,{2});", va.getJavaVariableValue(), JSTranspiler.FUNCTION_ARGUMENTS, poisonPillCallee);
			}
		}

		// An empty ObjectBindingPattern parameter (`{}`, optionally with its
		// own default) still requires RequireObjectCoercible(value) even
		// though it binds nothing - destructParameters() below never even
		// visits it (no properties to iterate), so it needs its own check.
		{
			ASTArrayLiteral params = getParameters();
			int paramCount = params.getChildCount();
			for(int i=0; i<paramCount; i++) {
				ASTNode child = params.getChild(i);
				if(!(child instanceof ASTArrayLiteral.InitializerExpression ie)) {
					continue;
				}
				ASTNode target = ie.getNode();
				ASTNode ownDefault = null;
				if(target instanceof ASTAssign as) {
					target = as.getLeftNode();
					ownDefault = as.getRightNode();
				}
				if(target instanceof ASTObjectLiteral ol && ol.isEmpty()) {
					if(ownDefault!=null) {
						b.println("requireObjectCoercible(initArg({0},{1},()->{2}));", JSTranspiler.FUNCTION_ARGUMENTS, i, JSTranspiler.asValue(paramContext,ownDefault));
					} else {
						b.println("requireObjectCoercible(initArg({0},{1}));", JSTranspiler.FUNCTION_ARGUMENTS, i);
					}
				}
			}
		}

		VariableFactory varFactory = (container,defaultValue,path,spread) -> {
			String varName = ((ASTIdentifier)container).getId();
	        VariableDef variable = functionContext.getOwnVariable(varName);
	        if(variable!=null) {
	        	variable.setTranspilerDeclared(true);

		        // Elide the initArg emission entirely when the parameter is
		        // never referenced in the body AND the function has no
		        // dynamic name-resolution paths (with/direct-eval/arguments,
		        // all of which set useArguments=true - see ASTIdentifier.init
		        // and ASTWith.init). A destructuring pattern with side-effecting
		        // defaults, or a getter on the arguments Object[], is retained.
		        boolean canElide = !variable.isUsed() && !useArguments
		        		&& defaultValue==null && spread==null
		        		&& path.length==1 && path[0] instanceof Integer;
		        if(canElide) {
		        	return;
		        }

		        // Most common case: just an integer index - don't use a path
		        if(path.length==1 && path[0] instanceof Integer && spread==null) {
		        	if(direct && defaultValue==null) {
		        		// Direct-arg variant: read from the positional method
		        		// parameter instead of `_args[i]`. No initArg call.
		        		b.println("{0} = p_arg_{1};", variable.getJavaVariableValue(), path[0]);
		        		return;
		        	}
			        b.print("{0} = initArg({1}", variable.getJavaVariableValue(), JSTranspiler.FUNCTION_ARGUMENTS);
	        		b.print(",{0}", path[0]);
	        		if(defaultValue!=null) {
	        			// Generate a value for a constant instead of a lambda!
	        			if(skipTransparent(defaultValue.node()) instanceof ASTLiteral) {
	        				b.print(",{0}", JSTranspiler.asValue(paramContext,defaultValue.node()));
	        			} else {
	        				// Lazy evaluation
	        				b.print(",()->{0}", JSTranspiler.asValue(paramContext,defaultValue.node()));
	        			}
	        		}
		        } else {
			        b.print("{0} = initArg({1}", variable.getJavaVariableValue(), JSTranspiler.FUNCTION_ARGUMENTS);
			        // Could we stack functions instead?  path(path(arg,"a"),0)
	        		if(path.length>0) {
				        b.print(",new Object[]{");
			        	for(int j=0; j<path.length; j++) {
			        		if(j>0) {
				        		b.print(",");
			        		}
			        		b.print("{0}", JSTranspiler.asValue(paramContext,path[j]));
			        	}
		        		b.print("}");
	        		} else {
		        		b.print(",null");
	        		}
		        	if(spread!=null) {
		        		b.print(",{0}",TranspilerUtil.encodeSpreadValue(paramContext,spread));
		        	} else {
		        		b.print(",null");
		        	}
		    		if(defaultValue!=null) {
	        		// TODO: Generate a value for a constant instead of a lambda!
		        	if(path.length>1) {
		        		b.print(",{0}", DefaultRef.transpileChain(paramContext,defaultValue));
		        	} else {
		        		b.print(",()->{0}", JSTranspiler.asValue(paramContext,defaultValue.node()));
		        	}
		    		}
		        }
	    		b.println(");");
	        }
		};

		// Per spec, a top-level parameter's own binding is positional
		// (CreateListIteratorRecord - non-observable, no real Symbol.iterator
		// call), so most positions keep going through the walk above. A
		// position that's directly an array pattern (optionally defaulted, or
		// a rest target) is different: ITS OWN nested BindingPattern DOES use
		// real IteratorBindingInitialization (an observable GetIterator/next/
		// return on the single value this position receives) - see
		// ASTArrayLiteral.transpileJavaIteratorDestructure. isSimpleParameterList()
		// already being false whenever this applies means `direct` is always
		// false here too, so there's no positional (p_arg_i) fast path to
		// consider for this branch.
		ASTArrayLiteral params = getParameters();
		int paramCount = params.getChildCount();
		for(int i=0; i<paramCount; i++) {
			ASTNode child = params.getChild(i);
			ASTArrayLiteral arrayTarget = null;
			ASTNode arrayOwnDefault = null;
			boolean isRest = false;
			if(child instanceof ASTArrayLiteral.InitializerExpression ie) {
				ASTNode target = ie.getNode();
				if(target instanceof ASTAssign as) {
					arrayOwnDefault = as.getRightNode();
					target = as.getLeftNode();
				}
				if(target instanceof ASTArrayLiteral al) {
					arrayTarget = al;
				}
			} else if(child instanceof ASTArrayLiteral.InitializerSpread sp) {
				if(sp.getNode() instanceof ASTArrayLiteral al) {
					arrayTarget = al;
					isRest = true;
				}
			}
			if(arrayTarget==null) {
				((ASTArrayLiteral.Initializer)child).destructParameters(varFactory, RuntimeUtil.EMPTY_PARAMS, i, paramContext, b, null);
				continue;
			}
			String tmpVar = functionContext.generateUniqueId("tmp");
			if(isRest) {
				b.println("Object {0} = initArg({1},null,{2});", tmpVar, JSTranspiler.FUNCTION_ARGUMENTS, TranspilerUtil.encodeSpreadValue(paramContext,i));
			} else {
				b.println("Object {0} = initArg({1},{2});", tmpVar, JSTranspiler.FUNCTION_ARGUMENTS, i);
			}
			if(arrayOwnDefault!=null) {
				b.println("if({0}==UNDEFINED) {0} = {1};", tmpVar, JSTranspiler.asValue(paramContext,arrayOwnDefault));
			}
			arrayTarget.transpileJavaIteratorDestructure(paramContext, b, tmpVar, /*isAssignmentContext*/ false, (target,valueExpr) -> {
				String varName = ((ASTIdentifier)target).getId();
				VariableDef variable = functionContext.getOwnVariable(varName);
				if(variable!=null) {
					variable.setTranspilerDeclared(true);
					b.println("{0} = {1};", variable.getJavaVariableValue(), valueExpr);
				}
			});
		}
	}
}

