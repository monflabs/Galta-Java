/*
 * Copyright (c) 2023-2026 Philippe Riand
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
package tests.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.Snippet;

public class TestExecutionContext extends ExecutionContext {
	
	private ByteArrayOutputStream bs = new ByteArrayOutputStream(2048);
	private PrintStream ps;
	
	public TestExecutionContext(Snippet snippet) {
		super(snippet);
		ps = new PrintStream(bs, true);
	}

	@Override
	public PrintStream getConsoleOut() {
		return ps;
	}
	
	@Override
	public PrintStream getConsoleErr() {
		return ps;
	}
	
	@Override
	public String getConsoleText() {
		return bs.toString();
	}
}