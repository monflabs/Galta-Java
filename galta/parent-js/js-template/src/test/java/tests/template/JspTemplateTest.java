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
package tests.template;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;
import org.monflabs.galtajs.template.TemplateEngine;
import org.monflabs.galtajs.template.engines.JspTemplateEngine;

import tests.ProjectTestCase;
import tests.TestEnvironment;

public class JspTemplateTest extends ProjectTestCase {
	
	JSEnvironment env = TestEnvironment.create();
	
	private TemplateEngine createEngine() {
		return new JspTemplateEngine(env);
	}
	private InterpretedGlobalRuntimeContext createContext() {
		return new InterpretedGlobalRuntimeContext(env,env.createProgramExecutor());
	}
	
	public void testEmptyTemplate() throws Exception {
		TemplateEngine engine = createEngine();
		String txt = engine.execute(createContext(),"");
		support.assertTextResult(txt, "empty.txt");
	}
	
	public void testStaticTemplate() throws Exception {
		TemplateEngine engine = createEngine();
		String txt = engine.execute(createContext(),"Hello World!");
		support.assertTextResult(txt, "static.txt");
	}
	
	public void testExpression() throws Exception {
		TemplateEngine engine = createEngine();
		String txt = engine.execute(createContext(),"one+one=<%=1+1%>");
		support.assertTextResult(txt, "expr1.txt");
	}
	
	public void testExpression2() throws Exception {
		TemplateEngine engine = createEngine();
		String txt = engine.execute(createContext(),"one+one=<%=1+1%>\ntwo+two=<%=2+2%>");
		support.assertTextResult(txt, "expr2.txt");
	}
	
	public void testStatement1() throws Exception {
		TemplateEngine engine = createEngine();
		String txt1 = engine.execute(createContext(),"<% if(true) {%> one+one=<%=1+1%> <%} else {%> two+two=<%=2+2%> <%}%>");
		support.assertTextResult(txt1, "stmt1_1.txt");
		String txt2 = engine.execute(createContext(),"<% if(false) {%> one+one=<%=1+1%> <%} else {%> two+two=<%=2+2%> <%}%>");
		support.assertTextResult(txt2, "stmt1_2.txt");
	}
	
	public void testEscape() throws Exception {
		TemplateEngine engine = createEngine();
		String txt = engine.execute(createContext(),"a', b\", c\\, d\n, e\r, f\u0001, g\u1234");
		support.assertTextResult(txt, "escape.txt");
	}

	public void testStatementLineBreaks() throws Exception {
		TemplateEngine engine = createEngine();
		// The line break right after a statement tag is dropped: one break only,
		// whatever its form ("\n\r" is a LF followed by a lone CR, two breaks)
		assertEquals("a\nb", engine.execute(createContext(),"<% var x=1 %>\na\nb"));
		assertEquals("a", engine.execute(createContext(),"<% var x=1 %>\r\na"));
		assertEquals("\ra", engine.execute(createContext(),"<% var x=1 %>\n\ra"));
	}
}
