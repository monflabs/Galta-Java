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
package org.monflabs.galtajs.node.clazz;

import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.control.ASTFunctionMethod;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.util.JavaBuilder;


/**
 * Class field getter.
 */
public class ASTClassGetter extends ASTClassMember {
	
	private ASTFunctionMethod functionDecl;
	private BuiltinFunction function;
	
	public ASTClassGetter(Token t, Object name, ASTFunctionMethod functionDecl, boolean isStatic, boolean isPrivate) {
		this(t,name,functionDecl,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassGetter(Token t, Object name, ASTFunctionMethod functionDecl, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		super(t,name,isStatic, isPrivate,decorators);
		this.functionDecl = assignParent(functionDecl);
	}

	public ASTFunctionMethod getFunctionDecl() {
		return functionDecl;
	}

	public BuiltinFunction getFunction() {
		return function;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->	{ return functionDecl; }
			default ->	{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.functionDecl = (ASTFunctionMethod)node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			function = (BuiltinFunction)functionDecl.evaluateValue(context, result);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}				
	}

	@Override
	public void initClass(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz) {
		if(isStatic())  {
			Object name = evaluateName(context, clazz);
			applyOwnDecorators(context, name);
			clazz.addClassStaticGetter(name, function);
		} else {
			Object name = evaluateName(context, clazz);
			applyOwnDecorators(context, name);
			clazz.addClassGetter(name, function);
		}
	}
	private void applyOwnDecorators(JSInterpretedRuntimeContext context, Object name) {
		Object result = applyDecorators(context, getDecorators(), function, "getter", name, isStatic(), isPrivate());
		if(result instanceof BuiltinFunction bf) {
			function = bf;
		}
	}

	@Override
	public void initInstance(JSInterpretedRuntimeContext context, BuiltinClassConstructor clazz, Object instance) {
		// See ASTClassMethod.initInstance: a private accessor is registered
		// per-instance too, merging with a same-named private setter if one
		// was already (or will later be) registered on this instance.
		if(!isStatic() && isPrivate()) {
			PrivateName name = (PrivateName)evaluateName(context, clazz);
			clazz.addInstancePrivateAccessor(instance, name, function, null);
		}
	}
	
	@Override
	public void transpileInitClassStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, int memberIndex) {
		if(!isStatic()) {
			if(isPrivate()) {
				b.println("priv_{0} = {1};", memberIndex, functionDecl.transpileJavaExpression(_jsContext));
				b.println("{0}.addClassGetter({1}, priv_{2});", clazzVar, transpileNameExpression(_jsContext, clazzVar), memberIndex);
			} else {
				b.println("{0}.addClassGetter({1},", clazzVar, transpileNameExpression(_jsContext, clazzVar));
				b.incIndent();
				b.println("{0}", functionDecl.transpileJavaExpression(_jsContext));
				b.decIndent();
				b.println(");");
			}
		} else {
			b.println("{0}.addClassStaticGetter({1},", clazzVar, transpileNameExpression(_jsContext, clazzVar));
			b.incIndent();
			b.println("{0}", functionDecl.transpileJavaExpression(_jsContext));
			b.decIndent();
			b.println(");");
		}
    }

	@Override
	public void transpileInitInstanceStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b, String clazzVar, String instanceVar, int memberIndex) {
		// See ASTClassGetter.initInstance (interpreted mode): merges with a
		// same-named private setter if one was already (or will later be)
		// registered on this instance.
		if(!isStatic() && isPrivate()) {
			b.println("{0}.addInstancePrivateAccessor({1}, {2}, priv_{3}, null);", clazzVar, instanceVar, transpileNameExpression(_jsContext, clazzVar), memberIndex);
		}
    }
	
	@Override
	public void decompile(JavaBuilder b) {
		if(isStatic()) {
			b.append("static ");
		}
		b.append("get ");
		ASTNode namedNode = getNameNode();
		if(namedNode!= null) {
			b.append('[');
			b.append(namedNode.decompileExpression());
			b.append(']');
		} else {
			if(isPrivate()) {
				b.append('#');
			}
			b.append(getName());
		}
		b.append(" ");
		b.append(functionDecl.decompileFunction());
		b.append("\n");
	}
}
