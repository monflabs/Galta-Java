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
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;


/**
 * Base Class.
 */
public abstract class ASTBaseClass extends ASTNode {

	private String className;
	private ASTNode superClass;
	private ASTNode[] elements;
	private ASTNode[] decorators;

	public ASTBaseClass(Token t, String className, ASTNode superClass, List<ASTNode> elements) {
		this(t,className,superClass,elements,Collections.emptyList());
	}
	public ASTBaseClass(Token t, String className, ASTNode superClass, List<ASTNode> elements, List<ASTNode> decorators) {
		super(t);
		this.className = className;
		this.superClass = assignParent(superClass);
		this.elements = assignParent(elements);
		this.decorators = assignParent(decorators);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1+elements.length+decorators.length;
	}
	@Override
	public ASTNode getChild(int index) {
		if (index == 0) {
			return superClass;
		}
		if(index-1<elements.length) {
			return elements[index-1];
		}
		if(index-1-elements.length<decorators.length) {
			return decorators[index-1-elements.length];
		}
		return super.getChild(index-1-elements.length-decorators.length);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if (index == 0) {
			superClass = node;
			return;
		}
		if(index-1<elements.length) {
			elements[index-1] = node;
			return;
		}
		if(index-1-elements.length<decorators.length) {
			decorators[index-1-elements.length] = node;
			return;
		}
		super._setChild(index-1-elements.length-decorators.length, node);
	}

	public String getClassName() {
		return className;
	}
	public void setClassName(String name) {
		this.className = name;
	}
	public ASTNode getSuperClass() {
		return superClass;
	}
	public ASTNode[] getElements() {
		return elements;
	}
	public ASTNode[] getDecorators() {
		return decorators;
	}

	private JSType objectType;

	/** JSType for "an instance of this class", memoized here (not in a global cache - see JSType.ofConstructor()) so its lifetime matches this node's. */
	public JSType getObjectType() {
		if(objectType==null) {
			objectType = JSType.ofConstructor(this);
		}
		return objectType;
	}
	
	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			result.setValue(context.getThis());
			return Signal.NONE;
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
    	return JSTranspiler.THIS_VAR;
    }
    
    @Override
	public String decompileExpression() {
    	return "this";
    }
}