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

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.generators.GeneratorReturnSignal;




/**
 * try statement node.
 */
public class ASTTry extends ASTNode {

	private ASTBlock bodyNode;
	private ASTCatch catchNode;
	private ASTBlock finallyNode;
	
	private String exceptionVarName; // for the transpiler

	public ASTTry(Token t, ASTBlock bodyNode, ASTCatch catchNode, ASTBlock finallyNode) {
		super(t);
		this.bodyNode = assignParent(bodyNode);
		this.catchNode = assignParent(catchNode);
		this.finallyNode = assignParent(finallyNode);
	}

	public ASTBlock getBodyNode() {
		return bodyNode;
	}

	public ASTCatch getCatchNode() {
		return catchNode;
	}

	public ASTBlock getFinallyNode() {
		return finallyNode;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+3;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return bodyNode; }
			case 1 ->	{ return catchNode; }
			case 2 ->	{ return finallyNode; }
			default ->	{ return super.getChild(index-3); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.bodyNode = (ASTBlock)node; }
			case 1 ->	{ this.catchNode  = (ASTCatch)node; }
			case 2 ->	{ this.finallyNode  = (ASTBlock)node; }
			default ->  { super._setChild(index-3,node); }
		}
	}

	public void setFinally(ASTBlock node) {
		this.finallyNode = node;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setUndefined();
			
	        if(catchNode!=null && finallyNode!=null) {
	        	Signal s;
	            try {
	            	InterpretedBlockRuntimeContext tryContext = new InterpretedBlockRuntimeContext(context);
	            	s = tryContext.with( () -> {
		    			return bodyNode.evaluate(tryContext,result);
	            	});
	    			if(s!=Signal.NONE) {
	    				return s;
	    			}
	            } catch(JSRuntimeUncatchableException t) {
	            	throw t;
	            } catch(GeneratorReturnSignal t) {
	            	// Generator.prototype.return(value) completion - not a catchable
	            	// JS exception, must bypass this catch block (finally still runs).
	            	throw t;
	            } catch(Exception t) {
	            	InterpretedBlockRuntimeContext catchContext = new InterpretedBlockRuntimeContext(context);
	            	s = catchContext.with( () -> {
	            		if(catchNode.getBindingNode()!=null) {
	            			catchNode.bindException(catchContext, JSRuntimeException.exceptionObject(t), result);
	            		}
		            	return catchNode.evaluate(catchContext,result);
	            	});
	    			if(s!=Signal.NONE) {
	    				return s;
	    			}
	            } finally {
	        		InterpretedBlockRuntimeContext finallyContext = new InterpretedBlockRuntimeContext(context);
	        		JSResult finallyResult = new JSResult(RuntimeUtil.UNDEFINED); // Don't erase the body one; UpdateEmpty defaults to undefined
	            	Signal s2 = finallyContext.with( () -> {
		        		return finallyNode.evaluate(finallyContext,finallyResult);
	            	});
	    			if(s2!=Signal.NONE) {
	    				result.copyFrom(finallyResult);
	    				return s2;
	    			}
	            }
	        } else if(catchNode!=null) {
	            try {
	            	InterpretedBlockRuntimeContext tryContext = new InterpretedBlockRuntimeContext(context);
	            	Signal s = tryContext.with( () -> {
		    			return bodyNode.evaluate(tryContext,result);
	            	});
	    			if(s!=Signal.NONE) {
	    				return s;
	    			}
	            } catch(JSRuntimeUncatchableException t) {
	            	throw t;
	            } catch(GeneratorReturnSignal t) {
	            	// Generator.prototype.return(value) completion - not a catchable
	            	// JS exception, must bypass this catch block (finally still runs).
	            	throw t;
	            } catch(Exception t) {
	            	InterpretedBlockRuntimeContext catchContext = new InterpretedBlockRuntimeContext(context);
	            	Signal s = catchContext.with( () -> {
	            		if(catchNode.getBindingNode()!=null) {
	            			catchNode.bindException(catchContext, JSRuntimeException.exceptionObject(t), result);
	            		}
	            		return catchNode.evaluate(catchContext,result);
	            	});
	    			if(s!=Signal.NONE) {
	    				return s;
	    			}
	            }
	        } else if(finallyNode!=null) {
	            try {
	            	InterpretedBlockRuntimeContext tryContext = new InterpretedBlockRuntimeContext(context);
	            	Signal s = tryContext.with( () -> {
		    			return bodyNode.evaluate(tryContext,result);
	            	});
	    			if(s!=Signal.NONE) {
	    				return s;
	    			}
	            } finally {
	        		InterpretedBlockRuntimeContext finallyContext = new InterpretedBlockRuntimeContext(context);
	        		JSResult finallyResult = new JSResult(RuntimeUtil.UNDEFINED); // Don't erase the body one; UpdateEmpty defaults to undefined
	            	Signal s2 = finallyContext.with( () -> {
		        		return finallyNode.evaluate(finallyContext,finallyResult);
	            	});
	    			if(s2!=Signal.NONE) {
	    				result.copyFrom(finallyResult);
	    				return s2;
	    			}
	            }
	        } else {
            	InterpretedBlockRuntimeContext tryContext = new InterpretedBlockRuntimeContext(context);
            	Signal s = tryContext.with( () -> {
	    			return bodyNode.evaluate(tryContext,result);
            	});
    			if(s!=Signal.NONE) {
    				return s;
    			}
	        }
	        
			// result contains the last statement evaluation
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}


    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}
    
	public String getExceptionVarName() {
		return exceptionVarName;
	}

    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
		{
			b.println("try {");
			JSTranspilerGeneratorContext tryContext = new TranspilerGeneratorBlockContext(jsContext); 
			b.incIndent();
			bodyNode.transpileJavaStatementNoBrace(tryContext,b);
			b.decIndent();
		}
		if(catchNode!=null) {
			JSTranspilerGeneratorContext catchContext = new TranspilerGeneratorBlockContext(jsContext); 
			exceptionVarName = jsContext.generateUniqueId(JSTranspiler.EXCEPTION_VAR);
			b.println("} catch(JSRuntimeUncatchableException {0}) {", exceptionVarName);
			b.incIndent();
			b.println("// Should not be caught - rethrow");
			b.println("throw {0};",exceptionVarName);
			b.decIndent();
			b.println("} catch(GeneratorReturnSignal {0}) {", exceptionVarName);
			b.incIndent();
			b.println("// Generator.prototype.return(value) completion - not catchable, rethrow");
			b.println("throw {0};",exceptionVarName);
			b.decIndent();
			b.println("} catch(Exception {0}) {", exceptionVarName);
			b.incIndent();
			catchNode.transpileJavaStatement(catchContext, b);
			b.decIndent();
		}
		if(finallyNode!=null) {
			JSTranspilerGeneratorContext finallyContext = new TranspilerGeneratorBlockContext(jsContext); 
			b.println("} finally {");
			b.incIndent();
			finallyNode.transpileJavaStatementNoBrace(finallyContext,b);
			b.decIndent();
		}
		b.println("}");
    }

    
    @Override
	public void decompileStatement(JavaBuilder b) {
    	b.append("try {\n");
    	b.incIndent();
    	decompileBlockStatements(b,bodyNode);
    	b.decIndent();
    	if(catchNode!=null) {
        	b.append("} catch");
        	if(catchNode.getBindingNode()!=null) {
        		b.append("(");
        		b.append(catchNode.getBindingNode().decompileExpression());
        		b.append(") {\n");
        	} else {
        		b.append(" {\n");
        	}
        	b.incIndent();
        	decompileBlockStatements(b,catchNode.getBodyNode());
        	b.decIndent();
    	}
    	if(finallyNode!=null) {
        	b.append("} finally {");
        	b.incIndent();
        	decompileBlockStatements(b,finallyNode);
        	b.decIndent();
    	}
    	b.append("}");
	}
}