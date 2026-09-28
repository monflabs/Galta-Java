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
package org.monflabs.galtajs.node.literal;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.MemberNode;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * String template.
 * 
 * Note: this cannot be a ASTLiteral as it cannot be used as string literal in other
 * places, like the var bames is Object literals.
 * 
 */
public class ASTStringTemplate extends ASTNode {
	
	private ASTNode tagFunction;
	private List<Object> parts;
	
	private JSArray strings;
	private List<ASTNode> expressions;
	
	public ASTStringTemplate(Token t1, Token t2, ASTNode tagFunction, List<Object> parts) {
		super(t1);
		endToken(t2);
		this.tagFunction = assignParent(tagFunction);
		this.parts = parts;
		// The children must exist from construction: enclosing nodes walk them
		// before init() (e.g. a for-loop checking its unbraced body for closures)
		this.expressions = new ArrayList<ASTNode>();
		for (Object p : parts) {
			if (p instanceof ASTNode n) {
				expressions.add(assignParent(n));
			}
		}
	}
	
	@Override
	protected void init(InitContext initContext) {
		this.strings = JSArray.create(initContext.getEnvironment());
		int expressionCount = 0;
		
		// Optimize this uniquely is there is a tagFunction associated
		JSArray rawStrings = null;
		if(tagFunction!=null) {
			rawStrings = JSArray.create(initContext.getEnvironment());
			// Both arrays are frozen right after creation (see below), and a frozen
			// object's own-property descriptors report writable/configurable:false;
			// "raw" is additionally non-enumerable, per GetTemplateObject.
			strings.getMembers(true).setOwnProperty("raw", rawStrings, PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		}

	    // Make sure that the string doesn't contains platform specific EOL
	    // Use \n no matter what
		for (Object p : parts) {
			if (p instanceof ASTNode) {
				expressionCount++;
				if(strings.arrayLength()<expressionCount) {
					strings.arrayAdd(""); 
					if(rawStrings!=null) {
						rawStrings.arrayAdd("");
					}
				}
			} else if (p instanceof CharSequence s) {
				String cooked = ASTLiteral.parseTemplateString(s.toString());
				if(cooked==null) {
					// An escape sequence the spec forbids in templates (octal, \8, \9,
					// or a malformed hex/unicode escape). Untagged templates treat this
					// as an early SyntaxError; tagged ones get an undefined cooked value
					// for this chunk while the raw text is unaffected.
					if(tagFunction==null) {
						throw RuntimeUtil.syntaxError("Invalid escape sequence in template literal");
					}
					strings.arrayAdd(RuntimeUtil.UNDEFINED);
				} else {
					strings.arrayAdd(cooked);
				}
				if(rawStrings!=null) {
					rawStrings.arrayAdd(ASTLiteral.normalizeRawTemplateString(s.toString()));
				}
			} else {
				throw new IllegalStateException();
			}
		}
		if(strings.arrayLength()==expressionCount) {
			strings.arrayAdd(""); // We should have a last string as well
			if(rawStrings!=null) {
				rawStrings.arrayAdd("");
			}
		}

		if(rawStrings!=null) {
			// Per GetTemplateObject: both the template object and its raw array are frozen.
			rawStrings.freeze();
			strings.freeze();
		}

		super.init(initContext);
	}

	
	@Override
	public int getChildCount() {
		return (tagFunction!=null?1:0) + expressions.size();
	}
	@Override
	public ASTNode getChild(int index) {
		if(tagFunction!=null) {
			if(index==0) {
				return tagFunction;
			}
			index--;
		}
		return expressions.get(index);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(tagFunction!=null) {
			if(index==0) {
				tagFunction = node;
				return;
			}
			index--;
		}
		expressions.set(index,node);
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	
	
	@Override
	public boolean isConstant(JSOptimizerContext context) {
		if(tagFunction!=null) {
			return false;
		}
		if(expressions!=null) {
			for(int i=0; i<expressions.size(); i++) {
				if(!expressions.get(i).isConstant(context)) {
					return false;
				}
			}
		}
		return true;
	}
	
	public String getRawString() {
		StringBuilder b = new StringBuilder(128);
		JSObject m = tagFunction!=null ? (JSObject)strings.getMembers(false) : null;
		JSArray rawStrings = m!=null ? (JSArray)m.getProperty("raw") : null;
		for(int i=0; i<strings.arrayLength(); i++) {
			Object v = strings.getProperty(i);
			if(v instanceof String text) {
				ASTLiteral.encodeString(b, text, (char)0);
			} else if(rawStrings!=null) {
				// A tagged template chunk with an invalid escape has an undefined
				// cooked value (see init()); fall back to the raw source text, which
				// is already valid template syntax and needs no further encoding.
				b.append((String)rawStrings.getProperty(i));
			}
			if(i<expressions.size()) {
				ASTNode sc = expressions.get(i);
				b.append("${");
				b.append(sc.decompileExpression());
				b.append("}");
			}
		}
		return b.toString();
	}
	
	public ASTNode getTagFunction() {
		return tagFunction;
	}

	@Override
	public String getNodeString() {
		String rawString = getRawString();
		if(rawString.length()>64) {
			rawString = rawString.substring(0,64)+"...";
		}
		return rawString;
	}

	@Override
	public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(tagFunction!=null) {
				// The tag is a Call/MemberExpression: a tag reached through a member
				// access (obj.tag`...`) binds "this" to the base object, exactly like a
				// normal method call; any other tag expression gets undefined "this".
				Object tag;
				Object tagThis;
				if(tagFunction instanceof MemberNode m && m.isSingleIndex()) {
					Object base = m.getNode().evaluateValue(context, result);
					tag = m.getSingleValue(context, base);
					tagThis = base;
				} else {
					tag = tagFunction.evaluateValue(context, result);
					tagThis = RuntimeUtil.UNDEFINED;
				}
				if(tag instanceof Callable c) {
					Object[] parameters = new Object[1+expressions.size()];
					// First param is an array of strings
					// Should we make in read only (freeze?)
					parameters[0] = strings;
					// Other parameters are the expressions
					if(expressions!=null) {
						for(int i=0; i<expressions.size(); i++) {
							ASTNode sc = expressions.get(i);
							// A substitution is a plain expression - it
							// declares nothing, so there's no block scope to
							// isolate (unlike ASTBlock, which only allocates
							// a context when hasDeclaredVariables()). No new
							// InterpretedBlockRuntimeContext needed - every
							// other expression-position node already
							// evaluates directly against the ambient
							// context.
							parameters[i+1] = context.with( () -> {
								return sc.evaluateValue(context,result);
							});
						}
					}
					// And call the function
					return c.call(tagThis, parameters);
				} else {
                	throw RuntimeUtil.typeError("Tag is not a callable");
				}
			}
			
			// Optimization when there is not expression included
			if(expressions.size()==0 && strings.arrayLength()==1) {
				return strings.getProperty(0);
			}
			
			// Dynamic templating
			StringBuilder b = new StringBuilder(128);
			for(int i=0; i<strings.arrayLength(); i++) {
				String text = (String)strings.getProperty(i);
				b.append(text);
				if(i<expressions.size()) {
					ASTNode sc = expressions.get(i);
					// See the tagged-template branch above's identical fix -
					// a substitution declares nothing, so no block context
					// is needed here either.
					context.run( () -> {
						String s = RuntimeUtil.toString(context.getEnvironment(),sc.evaluateValue(context,result));
						b.append(s);
					});
				}
			}
			return b.toString();
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
		if(tagFunction!=null) {
			StringBuilder b = new StringBuilder(64);
			if(tagFunction instanceof MemberNode m && m.isSingleIndex()) {
				// A tag reached through a member access (obj.tag`...`) binds "this" to
				// the base object, exactly like a normal method call.
				b.append("templateTagMethod(");
				b.append(JSTranspiler.MAIN_CONTEXT);
				b.append(",");
				b.append(JSTranspiler.asValue(jsContext,m.getNode()));
				b.append(",");
				if(tagFunction instanceof ASTMember mn) {
					b.append(ASTLiteral.encodeString(mn.getMemberName()));
				} else if(tagFunction instanceof ASTArrayMember am) {
					if(!am.isSingleIndex()) {
						throw new IllegalStateException("Only a simple index is supported for now with a template tag");
					}
					b.append(JSTranspiler.asValue(jsContext,am.getIndexes().get(0)));
				} else {
					throw new IllegalStateException("Internal error: should not be here");
				}
			} else {
				b.append("templateTagFunction(");
				b.append(JSTranspiler.MAIN_CONTEXT);
				b.append(",");
				b.append(JSTranspiler.asCallable(jsContext,tagFunction));
			}
			b.append(",");
			// First param is an array of strings
			// Should this be a constant??
			// templateStrings' own cacheId param is a compile-time-unique id
			// for THIS template-literal AST node (not its source text),
			// letting it return the SAME cached template object on every
			// evaluation, per spec GetTemplateObject - see its own comment.
			b.append("templateStrings(").append(jsContext.generateUniqueId()).append(",new Object[]{");
			for(int i=0; i<strings.arrayLength(); i++) {
				if(i>0) b.append(',');
				// A chunk with an invalid escape sequence has an undefined cooked value
				// (see init()); the raw text is unaffected.
				Object v = strings.getProperty(i);
				if(v instanceof String s) {
					// escapeUnicodeMarkerForJavac() must NOT be applied here -
					// encodeString() already produces safe Java source on its
					// own. A literal backslash becomes the standard doubled-
					// backslash Java escape, which is empirically immune to
					// javac's own unicode-escape preprocessing: a backslash
					// is only eligible to start a unicode escape when
					// preceded by an EVEN number of contiguous backslashes,
					// and the SECOND backslash of a doubled pair is always
					// preceded by an ODD count (one). escapeUnicodeMarkerForJavac()'s
					// own character-by-character scan doesn't account for
					// that parity rule - it "protects" the second backslash
					// of an already-safe doubled pair too, corrupting it
					// (confirmed via test262 tagged-template/
					// invalid-escape-sequences.js: a raw backslash-u-zero
					// sequence came back with a literal, undecoded escape
					// marker still in it, since the resulting backslash was
					// itself odd-preceded and so never decoded by javac
					// either). It's still correct for truly raw, un-escaped
					// text with no preceding backslash at all (e.g.
					// TranspilerJavaBuilder's comment echoing).
					b.append(ASTLiteral.encodeString(s));
				} else {
					b.append("RuntimeUtil.UNDEFINED");
				}
			}
			b.append("}");
			JSObject m = (JSObject)strings.getMembers(false);
			if(m!=null) {
				JSArray rawStrings = (JSArray)m.getProperty("raw");
				if(rawStrings!=null) {
					b.append(",new Object[]{");
					for(int i=0; i<rawStrings.arrayLength(); i++) {
						if(i>0) b.append(',');
						// Same "encodeString() alone is already safe" fix as
						// the cooked-strings loop above.
						b.append(ASTLiteral.encodeString((String)rawStrings.getProperty(i)));
					}
					b.append("}");
				}
			}
			b.append(")");
			
			// Other parameters are the expressions
			if(expressions!=null) {
				for(int i=0; i<expressions.size(); i++) {
					ASTNode sc = expressions.get(i);
					b.append(',');
					b.append(sc.transpileJavaExpression(jsContext));
				}
			}
			b.append(")");
			return b.toString();
		}
		
		// Optimization when there is not expression included
		if(strings.arrayLength()==1 && expressions.size()==0) {
			return ASTLiteral.encodeString((String)strings.getProperty(0));
		}

		StringBuilder b = new StringBuilder(64);
		b.append("stringConcat(");
		b.append(JSTranspiler.MAIN_ENVIRONMENT);
		for(int i=0; i<strings.arrayLength(); i++) {
			String s = (String)strings.getProperty(i);
			if(!s.isEmpty()) { // no need to concat empty strings
				b.append(',');
				b.append(ASTLiteral.encodeString(s));
			}
			if(i<expressions.size()) {
				b.append(',');
				ASTNode sc = expressions.get(i);
				String code = sc.transpileJavaExpression(jsContext);
				b.append(code);
			}
		}
		b.append(")");
		return b.toString();
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
    	if(tagFunction!=null) {
    		b.append(tagFunction.decompileExpression());
    	}
		b.append('`');
		b.append(getRawString());
		b.append('`');
		return b.toString();
	}
}
