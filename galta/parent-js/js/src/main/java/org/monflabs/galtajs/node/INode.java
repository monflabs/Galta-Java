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

import java.io.PrintStream;

import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.types.JSType;



/**
 * Provides the minimal node functions to be used by other interfaces.
 */
public interface INode {
	
	// Used by the transpiler
	enum STATEMENT_TYPE {
		STATEMENT,		// if, for, assignment, ...		-> transpile as is
		LITERAL,		// constant like "use strict"	-> do not transpile
		EXPRESSION		// expression lie				-> transpile XXX in statement(XXX)
	}
	
	public String getNodeString();

	public boolean isConstant(JSOptimizerContext context);
	
	public STATEMENT_TYPE getStatementType();
		
	public boolean isSequence();
	
//	public boolean isExpression() {
//		return false;
//	}	

	
	//////////////////////////////////////////////////////////////////////////////////
	// Access to parent/children
	//////////////////////////////////////////////////////////////////////////////////

	public INode getParent();

	public TopNode getTopNode();

	public int getChildCount();
	
	public INode getChild(int index);
	
	public void setChild(int index, INode node);
	
	public <T> T findParentNodeByClass(Class<T> clazz);



	//////////////////////////////////////////////////////////////////////////////////
	// Access to line information
	//////////////////////////////////////////////////////////////////////////////////

    public int getBeginLine();

    public int getBeginCol();

    public int getEndLine();

    public int getEndCol();

    public void extractSourceCode(StringBuilder builder);

	
	//////////////////////////////////////////////////////////////////////////////////
	// Access variables
	//////////////////////////////////////////////////////////////////////////////////
    
    public VariableDef findVariable(String name);
    
	//////////////////////////////////////////////////////////////////////////////////
	// Type system
	//////////////////////////////////////////////////////////////////////////////////
    
    public JSType getReturnedType();
    
    
    /////////////////////////////////////////////////////////////////////////////
    // Debug
    /////////////////////////////////////////////////////////////////////////////

    public void dump();
    
    public void dump(PrintStream ps);
}