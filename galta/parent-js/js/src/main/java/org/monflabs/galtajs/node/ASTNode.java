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

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.MemberAssigner;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;
import org.monflabs.util.generators.GeneratorReturnSignal;



/**
 * Abstract Formula Node.
 */
public abstract class ASTNode implements INode {
	
    public static final ASTNode[] EMPTY_NODES = new ASTNode[0];
	
	//public static final Token NO_TOKEN = new Token();
	
	private ASTNode parent;

    private int beginLine;
    private int beginCol;
    private int endLine;
    private int endCol;

    // Phase 1a: environment.supportSequenceExtensions() hoisted at init time.
    // Read-only after init publishes the AST for execution, so a plain field is
    // safe under the shared-AST/multi-thread invariant (§0 of optimize.md).
    protected boolean sequenceEnabled;

	protected ASTNode(Token token) {
		if(token!=null) {
			this.beginLine = token.beginLine;
			this.beginCol = token.beginColumn;
			this.endLine = token.endLine;
			this.endCol = token.endColumn+1;
		}
	}
	
	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@Override
	public boolean equals(Object o) {
		return this==o;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T endToken(Token token) {
		if(token!=null) {
			this.endLine = token.endLine;
			this.endCol = token.endColumn+1;
		}
		return (T)this;
	}
	
	@Override
	public String toString() {
		String n = getClass().getSimpleName();
		String ns = getNodeString();
		if(StringUtil.isNotEmpty(ns)) {
			return n + ": " + ns;
		}
		return n;
	}
	
	public interface InitContext {
		public InitContext getParent();
		public InitContext getMainContext();
		public JSEnvironment getEnvironment();
		public boolean isStrictMode();
		// Like isStrictMode(), but never folds in JSEnvironment.isStrictMode() (a parse-dialect
		// toggle used for other purposes). Reflects only actual "use strict" directives, so it can
		// be used where ECMAScript strict-mode *semantics* (not just parsing) must be accurate
		// regardless of that environment setting -- e.g. deciding arguments-object mapping/callee shape.
		public boolean isGenuinelyStrict();
		public int generateUniqueId();
		public default String generateUniqueId(String prefix) {
			return prefix+generateUniqueId();
		}

	}
	public static class MainContext implements InitContext {
		private JSEnvironment env;
		private int uniqueId;
		private boolean forceStrictMode;
		public MainContext(JSEnvironment env, boolean forceStrictMode) {
			this.env = env;
			this.forceStrictMode = forceStrictMode;
		}
		@Override
		public InitContext getParent() {
			return null;
		}
		@Override
		public InitContext getMainContext() {
			return this;
		}
		@Override
		public JSEnvironment getEnvironment() {
			return env;
		}
		@Override
		public boolean isStrictMode() {
			return forceStrictMode || env.isStrictMode();
		}
		@Override
		public boolean isGenuinelyStrict() {
			return forceStrictMode;
		}
		@Override
		public int generateUniqueId() {
			return uniqueId++;
		}
	}
	public static class ChildContext implements InitContext {
		private InitContext parent;
		private boolean forceStrictMode;
		private boolean forceGenuinelyStrict;
		public ChildContext(InitContext parent, boolean forceStrictMode, boolean forceGenuinelyStrict) {
			this.parent = parent;
			this.forceStrictMode = forceStrictMode;
			this.forceGenuinelyStrict = forceGenuinelyStrict;
		}
		@Override
		public InitContext getParent() {
			return parent;
		}
		@Override
		public InitContext getMainContext() {
			return parent.getMainContext();
		}
		@Override
		public JSEnvironment getEnvironment() {
			return parent.getEnvironment();
		}
		@Override
		public boolean isStrictMode() {
			return forceStrictMode || getEnvironment().isStrictMode();
		}
		@Override
		public boolean isGenuinelyStrict() {
			return forceGenuinelyStrict;
		}
		@Override
		public int generateUniqueId() {
			return parent.generateUniqueId();
		}
	}
	
	
	protected void init(InitContext initContext) {
		// Phase 1a: hoist environment flag onto the node once, before publication
		// for execution. Read-only from here on (see §0 concurrency invariant).
		this.sequenceEnabled = initContext.getEnvironment().supportSequenceExtensions();

		int sz = getChildCount();
		for(int i=0; i<sz; i++) {
			ASTNode node = getChild(i);
			if(JSEnvironment.CHECK_FOR_DEBUG) {
				if(node!=null && node.parent==null) {
					throw fillInStackTrace(RuntimeUtil.error("No parent assigned to node {0} from its parent {1}", getChild(i).getClass(), getClass() ));
				}
			}
			if(node!=null) {
				node.init(initContext);
			}
		}

		// Prioritize check for children
		if(isSequence() && !sequenceEnabled) {
			throw fillInStackTrace(RuntimeUtil.syntaxError("Operator {0} requires Galta extensions to be enabled", getNodeString() ));
		}
	}

	// "eval"/"arguments" as a declared BINDING name (var/let/const, function/
	// class name, parameter, catch clause) is an early SyntaxError in
	// strict-mode code, per spec - checked against isGenuinelyStrict() (never
	// blended with JSEnvironment's parse-dialect toggle) so this correctly
	// inherits a direct eval's forceStrict-derived strictness without also
	// firing under an env-wide strict-dialect toggle that isn't a genuine
	// "use strict". Only the DECLARATION of such a binding is checked here -
	// assigning to "eval"/"arguments" as an existing identifier is a
	// separate, already-implemented runtime check (ASTIdentifier.evaluateAssign()).
	protected static void checkStrictBindingName(InitContext initContext, String name, ASTNode errorNode) {
		checkStrictBindingName(initContext.isGenuinelyStrict(), name, errorNode);
	}
	// Overload for call sites (e.g. ASTFunctionDecl.createFunction(), which
	// checks a function DECLARATION's own name against its ENCLOSING scope's
	// strictness) that already have a plain boolean rather than an InitContext.
	protected static void checkStrictBindingName(boolean genuinelyStrict, String name, ASTNode errorNode) {
		if(genuinelyStrict && ("eval".equals(name) || "arguments".equals(name) || isStrictFutureReservedWord(name))) {
			throw new JSParseException(null,errorNode,"'{0}' is not allowed as a binding name in strict mode",name);
		}
	}
	// The spec's strict-mode-only FutureReservedWord list (11.6.2.2) - "let"
	// and "yield" are deliberately excluded here since they already have
	// their own separate, context-sensitive handling elsewhere (yield's
	// generator-scope-aware reservation in the parser, let's contextual
	// keyword status).
	public static boolean isStrictFutureReservedWord(String name) {
		return "implements".equals(name) || "interface".equals(name) || "package".equals(name)
			|| "private".equals(name) || "protected".equals(name) || "public".equals(name)
			|| "static".equals(name);
	}

	// Where the parser found this node as the Statement of another statement
	// (a bit set of the POSITION_* flags, 0 for anywhere else) - checked by
	// EarlyErrorsValidator, since a declaration is not a Statement there.
	public static final int POSITION_BODY = 1;			// loop or with body
	public static final int POSITION_IF_CLAUSE = 2;		// if/else clause
	public static final int POSITION_LABELLED = 4;		// labelled statement
	private byte statementPosition;

	public void markStatementPosition(int position) {
		statementPosition |= position;
	}

	public int getStatementPosition() {
		return statementPosition;
	}

	// The parent of node, above any transparent (debug hook) wrapper
	public static ASTNode skipTransparentParent(ASTNode node) {
		ASTNode p = node.getParent();
		while(p instanceof org.monflabs.galtajs.node.debug.ASTDebugHook) {
			p = p.getParent();
		}
		return p;
	}

	public static ASTNode skipTransparent(ASTNode node) {
		while(node instanceof NoopNode p) {
			node = p.getNode();
		}
		return node;
	}

	@Override
	public String getNodeString() {
		return "";
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.STATEMENT;
	}	

	@Override
	public boolean isSequence() {
		return false;
	}	
	

//	public boolean isExpression() {
//		return false;
//	}	
	
	protected boolean areChildrenConstant(JSOptimizerContext context) {
		int slotCount = getChildCount();
		for(int i=0; i<slotCount; i++) {
			ASTNode node = getChild(i);
			if(node!=null && !node.isConstant(context)) {
				return false;
			}
		}
		return true;
	}	

	
	//////////////////////////////////////////////////////////////////////////////////
	// Access to parent/children
	//////////////////////////////////////////////////////////////////////////////////

	@Override
	public ASTNode getParent() {
		return parent;
	}

	@Override
	public TopNode getTopNode() {
		for(ASTNode n=this;;) {
			ASTNode p=n.getParent();
			if(p==null) {
				return (TopNode)n;
			}
			n = p;
		}
	}

	@Override
	public int getChildCount() {
		return 0;
	}
	@Override
	public ASTNode getChild(int index) {
		throw new IllegalArgumentException(MessageFormat.format("Invalid child index {0}",index));
	}
	@Override
	public final void setChild(int index, INode node) {
		_setChild(index, assignParent((ASTNode)node));
	}
	protected void _setChild(int index, ASTNode node) {
		throw new IllegalArgumentException(MessageFormat.format("Invalid child index {0}",index));
	}


	//////////////////////////////////////////////////////////////////////////////////
	// Access to line information
	//////////////////////////////////////////////////////////////////////////////////
	
	public static class HighlightPosition {
		protected int beginLine;
		protected int beginCol;
		protected int endLine;
		protected int endCol;
		protected HighlightPosition() {
		}
		public HighlightPosition(int beginLine, int beginCol, int endLine, int endCol) {
			this.beginLine = beginLine;
			this.beginCol = beginCol;
			this.endLine = endLine;
			this.endCol = endCol;
		}
		public int getBeginLine() {
			return beginLine;
		}
		public int getBeginCol() {
			return beginCol;
		}
		public int getEndLine() {
			return endLine;
		}
		public int getEndCol() {
			return endCol;
		}
	}
	public HighlightPosition getHighlightPosition() {
		HighlightPosition p = new HighlightPosition(getBeginLine(),getBeginCol(),getEndLine(),getEndCol());
		int childCount = getChildCount();
		for(int i=0; i<childCount; i++) {
			ASTNode c = getChild(i);
			if(c!=null) {
				HighlightPosition cp = c.getHighlightPosition();
				int cBeginLine = cp.getBeginLine();
				int cBeginCol = cp.getBeginCol();
				if(cBeginLine<p.beginLine || (cBeginLine==p.beginLine && cBeginCol<p.beginCol)) {
					p.beginLine = cBeginLine;
					p.beginCol = cBeginCol;
				}
				int cEndLine = cp.getEndLine();
				int cEndCol = cp.getEndCol();
				if(cEndLine>p.endLine || (cEndLine==p.endLine && cEndCol>p.endCol)) {
					p.endLine = cEndLine;
					p.endCol = cEndCol;
				}
			}
		}
		return p;
	}
	// some helpers
	protected HighlightPosition getHighlightPositionWithoutChildren() {
		return new HighlightPosition(getBeginLine(),getBeginCol(),getEndLine(),getEndCol());
	}

	public void copyPositionFrom(ASTNode source) {
		HighlightPosition p = source.getHighlightPosition();
		this.beginLine = p.beginLine;
		this.beginCol = p.beginCol;
		this.endLine = p.endLine;
		this.endCol = p.endCol;
	}
	
    @Override
	public int getBeginLine(){
        if( beginLine<=0 ) {
            computePositions();
        }
        return beginLine;
    }

    @Override
	public int getBeginCol(){
        if( beginCol<=0 ) {
            computePositions();
        }
        return beginCol;
    }

    @Override
	public int getEndLine(){
        if( endLine<=0 ) {
            computePositions();
        }
        return endLine;
    }

    @Override
	public int getEndCol(){
        if( endCol<=0 ) {
            computePositions();
        }
        return endCol;
    }

    private void computePositions() {
        // Must be a non terminal node
    	int childCount = getChildCount();
    	boolean first = true;
    	for(int i=0; i<childCount; i++) {
    		ASTNode c = getChild(i);
    		if(c!=null) {
	    		if(first) {
	    			first = false;
		            this.beginLine = c.getBeginLine();
		            this.beginCol = c.getBeginCol();
		            this.endLine = c.getEndLine();
		            this.endCol = c.getEndCol();
	    		} else {
		            int cBeginLine = c.getBeginLine();
		            int cBeginCol = c.getBeginCol();
		            if(cBeginLine<beginLine || (cBeginLine==beginLine && cBeginCol<beginCol) ) {
	    	            this.beginLine = cBeginLine;
	    	            this.beginCol = cBeginCol;
		            }
		            int cEndLine = c.getEndLine();
		            int cEndCol = c.getEndCol();
		            if(cEndLine>endLine || (cEndLine==endLine && cEndCol>endCol) ) {
	    	            this.endLine = cEndLine;
	    	            this.endCol = cEndCol;
		            }
	    		}
    		}
    	}
    }

    @Override
	public void extractSourceCode(StringBuilder b) {
    	String code = findSourceCode();
    	if(code!=null) {
    		JSException.extractSourceCode(b, JSException.EXTRACT_LINES, code, getBeginLine(), getBeginCol());
    	}
    }
	public String findSourceCode() {
    	for(ASTNode n=this; n!=null; n=n.getParent()) {
    		if(n instanceof ASTProgram p) {
    			return p.getSourceCode();
    		}
    		if(n instanceof ASTFunctionDecl f) {
    			String code = f.getSourceCode();
    			if(code!=null) {
    				return code;
    			}
    		}
    	}
    	return null;
    }

    
	//////////////////////////////////////////////////////////////////////////////////
	// Initialization Helpers
	//////////////////////////////////////////////////////////////////////////////////
    
    protected final <T extends ASTNode> T assignParent(T child) {
    	if(child!=null) {
    		((ASTNode)child).parent = this;
    	}
		return child;
    }
    protected final ASTNode[] assignParent(ASTNode[] children) {
    	for(int i=0; i<children.length; i++) {
    		ASTNode child = children[i];
        	if(child!=null) {
        		child.parent = this;
        	}
    	}
    	return children;
    }
    protected final ASTNode[] assignParent(List<ASTNode> list) {
    	if(list!=null && !list.isEmpty()) {
    		int sz = list.size();
    		ASTNode[] children = new ASTNode[sz];
    		for(int i=0; i<sz; i++) {
    			ASTNode n = list.get(i);
    			n.parent = this;
    			children[i] = n;
    		}
    		return children;
    	}
    	return EMPTY_NODES;
	}
    
    @Override
	@SuppressWarnings("unchecked")
	public <T> T findParentNodeByClass(Class<T> clazz) {
    	for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
    		if(clazz.isAssignableFrom(n.getClass())) {
    			return (T)n;
    		}
    	}
    	throw RuntimeUtil.error("Internal error: script is missing a container node of type {0}.", clazz);
    }
	@SuppressWarnings("unchecked")
	protected <T> T findParentNodeByClassUnchecked(Class<T> clazz) {
    	for(ASTNode n=getParent(); n!=null; n=n.getParent()) {
    		if(clazz.isAssignableFrom(n.getClass())) {
    			return (T)n;
    		}
    	}
    	return null;
    }

	
	//////////////////////////////////////////////////////////////////////////////////
	// Access variables
	//////////////////////////////////////////////////////////////////////////////////
    
    @Override
	public VariableDef findVariable(String name) {
		for(ASTNode n=this; n!=null; n=n.getParent()) {
			if(n instanceof ASTVarContainer vc) {
				VariableDef v = vc.getOwnVariable(name);
				if(v!=null) {
					return v;
				}
			}
		}
		return null;
	}

    
	//////////////////////////////////////////////////////////////////////////////////
	// Type system
	//////////////////////////////////////////////////////////////////////////////////
    
    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
    }
    
    
	//////////////////////////////////////////////////////////////////////////////////
	// Interpreter
	//////////////////////////////////////////////////////////////////////////////////
	
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		// Wrapping the exception should be done in evaluateValue
		result.setValue(evaluateValue(context, result));
		return Signal.NONE;
	}
	
	// At least one of these must be overridden, else it will enter an infinite recursion
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		// Wrapping the exception should be done in evaluate
		evaluate(context, result);
		return result.deref();
	}
	
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		Object v = evaluateValue(context, result);
		result.setValue(RuntimeUtil.typeof(context.getEnvironment(),v));
		return Signal.NONE;
	}
	
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		throw RuntimeUtil.syntaxError("Left part of assign, {0}, is not assignable", getClass());
	}

	/** How a node is used as an assignment target, for {@link #checkAssignmentTarget}. */
	public enum AssignmentUse {
		/** {@code target = value}: destructuring patterns allowed */
		PLAIN,
		/** {@code for (target in/of ...)}: destructuring patterns allowed */
		FOR_IN_OF,
		/** {@code target op= value}, {@code ++target}, {@code target--} */
		COMPOUND,
		/** {@code target &&= value}, {@code ||=}, {@code ??=} */
		LOGICAL,
		/** An element of a destructuring assignment pattern (nested patterns are checked by the pattern itself) */
		NESTED
	}

	/**
	 * Static Semantics AssignmentTargetType as an early error: throws a
	 * SyntaxError at parse time when {@code target} cannot be assigned
	 * (a literal, an operator expression, {@code this}, an optional chain,
	 * a parenthesized pattern...), instead of failing only once the
	 * assignment runs. A call expression keeps Annex B's web-compat runtime
	 * ReferenceError in sloppy code, except as a logical assignment target.
	 */
	public static void checkAssignmentTarget(ASTNode target, AssignmentUse use, boolean strict) {
		ASTNode n = target;
		boolean parenthesized = false;
		while(n instanceof org.monflabs.galtajs.node.unaryop.ASTParen || n instanceof org.monflabs.galtajs.node.debug.ASTDebugHook) {
			parenthesized |= n instanceof org.monflabs.galtajs.node.unaryop.ASTParen;
			n = ((NoopNode)n).getNode();
		}
		String invalid = null;
		if(n instanceof ASTIdentifier id && strict && ("eval".equals(id.getId()) || "arguments".equals(id.getId()))) {
			invalid = "'"+id.getId()+"' in strict mode";
		} else if(n instanceof org.monflabs.galtajs.node.literal.ASTContainerLiteral) {
			if(parenthesized || (use!=AssignmentUse.PLAIN && use!=AssignmentUse.FOR_IN_OF)) {
				invalid = "a parenthesized or compound-assigned destructuring pattern";
			} else {
				((org.monflabs.galtajs.node.literal.ASTContainerLiteral)n).checkPattern(false, strict);
			}
		} else if(n instanceof ASTSuperCtor || n instanceof org.monflabs.galtajs.node.unaryop.ASTNew) {
			invalid = n instanceof ASTSuperCtor ? "super()" : "new";
		} else if(n instanceof org.monflabs.galtajs.node.call.ASTBaseCall) {
			if(strict || use==AssignmentUse.LOGICAL || use==AssignmentUse.NESTED) {
				invalid = "a function call";
			}
		} else if(n instanceof ASTImportMeta) {
			invalid = "import.meta";
		} else if(n instanceof ASTNewMember) {
			invalid = "new.target";
		} else if(n instanceof ChainingNode c && isOptionalChain(c)) {
			invalid = "an optional chain";
		} else if(n!=null && !overridesEvaluateAssign(n)) {
			invalid = n.getClass().getSimpleName();
		}
		if(invalid!=null) {
			throw new JSParseException(null, target, "Invalid assignment target: {0}", invalid);
		}
	}

	private static boolean isOptionalChain(ChainingNode c) {
		for(ChainingNode n=c; ; ) {
			if(n.isNullOp() && !(n.getNode() instanceof ASTIdentifierFilter)) {
				return true;
			}
			if(!(n.getNode() instanceof ChainingNode next)) {
				return false;
			}
			n = next;
		}
	}

	private static final ClassValue<Boolean> OVERRIDES_EVALUATE_ASSIGN = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("evaluateAssign", JSInterpretedRuntimeContext.class, Object.class, Function.class, JSResult.class, Function.class)
						.getDeclaringClass()!=ASTNode.class;
			} catch(NoSuchMethodException ex) {
				return false;
			}
		}
	};
	private static boolean overridesEvaluateAssign(ASTNode n) {
		return OVERRIDES_EVALUATE_ASSIGN.get(n.getClass());
	}
	
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		// UnaryExpression : delete UnaryExpression - when the operand isn't a
		// Reference (e.g. a function call, a literal), it's still evaluated for its
		// side effects/exceptions, and delete trivially succeeds.
		evaluateValue(context, result);
		return true;
	}

	protected final RuntimeException fillInStackTrace(Throwable ex) {
		if(ex instanceof GeneratorReturnSignal grs) {
			// A Generator.prototype.return(value) completion signal - must propagate
			// unchanged (not wrapped into a JSRuntimeException), since it's not a
			// catchable JS exception, just an internal control-flow signal.
			return grs;
		}
		JSRuntimeException dsException;
		if(ex instanceof JSRuntimeException rt) {
			dsException = rt;
		} else {
			dsException = RuntimeUtil.wrap(ex);
		}
		dsException.fillStackTrace(this);
		return dsException;
	}
	

	
	//////////////////////////////////////////////////////////////////////////////////
	// Optimizer
	//////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		return false;
	}	
	public JSOptimizerContext createOptimizedContext(JSOptimizerContext context) {
		return context;
	}
	public void updateOptimizedContext(JSOptimizerContext context) {
	}	
	
    
	//////////////////////////////////////////////////////////////////////////////////
	// Transpiler
	//////////////////////////////////////////////////////////////////////////////////

    public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
    	STATEMENT_TYPE stype = getStatementType();
    	if(stype==STATEMENT_TYPE.LITERAL) {
    		// Ignore
    		return;
    	}
    	
    	String s = transpileJavaExpression(jsContext);
    	if(StringUtil.isNotEmpty(s)) {
        	if(stype==STATEMENT_TYPE.EXPRESSION) {        		
        		b.print("statement(");
        		b.print(s);
        		b.println(");");
        	} else {
        		b.print(s);
        		b.println(";");
        	}
    	}
    }
    
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	throw new JSTranspilerException(null, this, "Node {0} is not (yet) supported by the transpiler to evaluate", getClass());
    }
    
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	return StringFormat.format("typeof({0})", JSTranspiler.asValue(jsContext,this));
    }
    
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	// UnaryExpression : delete UnaryExpression - when the operand isn't a
    	// Reference (e.g. a function call, a literal), it's still evaluated for its
    	// side effects/exceptions, and delete trivially succeeds.
    	return StringFormat.format("deleteNonReference({0})", JSTranspiler.asValue(jsContext,this));
    }

    public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	throw new JSTranspilerException(null, this, "Node {0} is not (yet) supported by the transpiler", toString());
    }
    
    public static enum ASSIGN_TYPE {
    	EQUALS,
    	
    	EQUALS_ADD,
    	EQUALS_AND,
    	EQUALS_BITAND,
    	EQUALS_BITOR,
    	EQUALS_BITXOR,
    	EQUALS_DIV,
    	EQUALS_LSHIFT,
    	EQUALS_MOD,
    	EQUALS_MUL,
    	EQUALS_NULLCOALESCING,
    	EQUALS_OR,
    	EQUALS_POWER,
    	EQUALS_RSHIFT,
    	EQUALS_RUNSHIFT,
    	EQUALS_SUB,
    	
    	PREINC,
    	POSTINC,
    	PREDEC,
    	POSTDEC
    	;
    	
       	public String assignmentRuntimeFunction() {
        	switch(this) {
	    		case EQUALS -> 					{ return "assign"; }
	    		case EQUALS_ADD -> 				{ return "assignAdd"; }
	    		case EQUALS_AND -> 				{ return "assignAnd"; }
	    		case EQUALS_BITAND -> 			{ return "assignBitAnd"; }
	    		case EQUALS_BITOR -> 			{ return "assignBitOr"; }
	    		case EQUALS_BITXOR -> 			{ return "assignBitXor"; }
	    		case EQUALS_DIV -> 				{ return "assignDiv"; }
	    		case EQUALS_LSHIFT -> 			{ return "assignLShift"; }
	    		case EQUALS_MOD -> 				{ return "assignMod"; }
	    		case EQUALS_MUL -> 				{ return "assignMul"; }
	    		case EQUALS_NULLCOALESCING -> 	{ return "assignNullCoalescing"; }
	    		case EQUALS_OR -> 				{ return "assignOr"; }
	    		case EQUALS_POWER ->			{ return "assignPower"; }
	    		case EQUALS_RSHIFT -> 			{ return "assignRShift"; }
	    		case EQUALS_RUNSHIFT -> 		{ return "assignRunShift"; }
	    		case EQUALS_SUB -> 				{ return "assignSub"; }
	    	
	    		case PREINC -> 					{ return "preInc"; }
	    		case POSTINC -> 				{ return "postInc"; }
	    		case PREDEC -> 					{ return "preDec"; }
	    		case POSTDEC -> 				{ return "postDec"; }
	    	}
        	throw new IllegalStateException();
    	}
       	
       	public MemberAssigner assignmentFunction() {
        	switch(this) {
	    		case EQUALS -> 					{ return RuntimeUtil::assign; }
	    		case EQUALS_ADD -> 				{ return RuntimeUtil::assignAdd; }
	    		case EQUALS_AND -> 				{ return RuntimeUtil::assignAnd; }
	    		case EQUALS_BITAND -> 			{ return RuntimeUtil::assignBitAnd; }
	    		case EQUALS_BITOR -> 			{ return RuntimeUtil::assignBitOr; }
	    		case EQUALS_BITXOR -> 			{ return RuntimeUtil::assignBitXor; }
	    		case EQUALS_DIV -> 				{ return RuntimeUtil::assignDiv; }
	    		case EQUALS_LSHIFT -> 			{ return RuntimeUtil::assignLShift; }
	    		case EQUALS_MOD -> 				{ return RuntimeUtil::assignMod; }
	    		case EQUALS_MUL -> 				{ return RuntimeUtil::assignMul; }
	    		case EQUALS_NULLCOALESCING -> 	{ return RuntimeUtil::assignNullCoalescing; }
	    		case EQUALS_OR -> 				{ return RuntimeUtil::assignOr; }
	    		case EQUALS_POWER ->			{ return RuntimeUtil::assignPower; }
	    		case EQUALS_RSHIFT -> 			{ return RuntimeUtil::assignRShift; }
	    		case EQUALS_RUNSHIFT -> 		{ return RuntimeUtil::assignRunShift; }
	    		case EQUALS_SUB -> 				{ return RuntimeUtil::assignSub; }
	    	
	    		case PREINC -> 					{ return RuntimeUtil::preInc; }
	    		case POSTINC -> 				{ return RuntimeUtil::postInc; }
	    		case PREDEC -> 					{ return RuntimeUtil::preDec; }
	    		case POSTDEC -> 				{ return RuntimeUtil::postDec; }
	    	}
        	throw new IllegalStateException();
    	}
    }


    /////////////////////////////////////////////////////////////////////////////
    // Decompiler
    /////////////////////////////////////////////////////////////////////////////
	
    public void decompileStatement(JavaBuilder b) {
    	String exp = decompileExpression();
    	if(StringUtil.isNotEmpty(exp)) {
    		b.append(decompileExpression());
    		b.append(';');
    	}
	}
    public String decompileExpression() {
    	return StringFormat.format("/* {0} */", getClass().getSimpleName());
		//throw new IllegalStateException(StringFormat.format("Node {0} cannot be decompiled",this.getClass().getSimpleName()));
	}
    
    // Helpers
	protected final void decompileStatements(JavaBuilder b, ASTNode[] statements) {
		if(statements!=null) {
			for(int i=0; i<statements.length; i++) {
				emitDecompileLocation(b,statements[i]);
				statements[i].decompileStatement(b);
				b.nl();
			}
		}
	}
	protected final void decompileBlockStatements(JavaBuilder b, ASTNode node) {
		if(node instanceof ASTBlock bl) {
	    	decompileStatements(b,bl.getStatements());
		} else {
			node.decompileStatement(b);
		}
	}

	public void emitDecompileLocation(JavaBuilder b, ASTNode node) {
		b.comment("line: {0}, col {1}", node.getBeginLine(), node.getBeginCol());
	}
    
    
    /////////////////////////////////////////////////////////////////////////////
    // Debug
    /////////////////////////////////////////////////////////////////////////////

	public String dumpString() {
        StringBuilder builder = new StringBuilder();
        try (OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) {
            	builder.append((char) b);
            }
        }) {
	        PrintStream ps = new PrintStream(outputStream);
	        dump(ps);
	        ps.flush();
	        return builder.toString();
        } catch(Exception ex) {
        	return StringFormat.format("Error: {0}", ex.getLocalizedMessage());
        }
        
    }

    @Override
	public void dump() {
    	dump(Console.outStream());
    }
    @Override
	public void dump(PrintStream ps) {
    	dump(ps, this, new ArrayList<ASTNode>());
    	ps.flush();
    }

	private void dump(PrintStream ps, ASTNode node, ArrayList<ASTNode> nodes){
        // Print the indentation
        for( int lv=1; lv<nodes.size(); lv++ ) {
            ASTNode parent = nodes.get(lv-1);
            if( parent.getChild(parent.getChildCount()-1)==nodes.get(lv) ) {
                ps.print( "    " );
            } else {
                ps.print( "|   " );
            }
        }
		if (node == null) {
			ps.print("+- <null node>\r\n");
			return;
		}

        nodes.add(node);
        try {
            Class<?> c = node.getClass();
            String name=c.getName();
            int pos=name.lastIndexOf('.');
            name=name.substring(pos<0?0:pos+1);

            if(nodes.size()>1 ) {
                ps.print("+- ");
            }
            ps.print(name);
//            ps.print(" #");
//            ps.print(node.beginLine);
//            ps.print(",");
//            ps.print(node.beginCol);
            String info = node.getNodeString();
            if(StringUtil.isNotEmpty(info)) {
                ps.print(": ");
                ps.print(info);
            }
            int cnt=node.getChildCount();
            ps.print("\n");

            for(int i=0;i<cnt;i++){
                ASTNode child=node.getChild(i);
                dump(ps,child,nodes);
            }
        } finally {
            nodes.remove( nodes.size()-1 );
        }
    }
}