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
package org.monflabs.galtajs;

import java.text.MessageFormat;

import org.monflabs.galtajs.node.ASTNode;

/**
 * @author Philippe Riand
 */
public class JSParseException extends JSException  {

	private static final long serialVersionUID = 1L;

	private String decoratedMessage;

    public JSParseException(Throwable nextException, ASTNode node, String msg, Object... parameters) {
        super(nextException,msg,parameters);
        fillStackTrace(node);
    }

    @Override
	public String getMessage() {
    	if(decoratedMessage!=null) {
    		return decoratedMessage;
    	}
        return super.getMessage();
    }

    public void fillStackTrace(ASTNode node) {
    	if(decoratedMessage==null && node!=null) {
    		StringBuilder builder = new StringBuilder();
    		node.extractSourceCode(builder);
    		this.decoratedMessage = MessageFormat.format("{0}\nat line {1}, column {2}\n{3}", super.getMessage(), node.getBeginLine(), node.getBeginCol(), builder);
    	}
    }
}
