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
package playground.impl;

import java.util.List;

import org.monflabs.playground.PlaygroundLayout;

import playground.impl.engine.jshell.JShellExecutionEngine;

public class JavaPlaygroundLayout extends PlaygroundLayout {
	
	@Override
	public WINDOW getWindow(String name) {
		if(name.endsWith(".java")) {
			return WINDOW.MAIN;
		}
		if(name.endsWith(".jshell")) {
			return WINDOW.MAIN;
		}
		return super.getWindow(name);
	}
	
	@Override
	public void sortTabs(WINDOW window, List<String> tabs) {
		sortTabs(window,tabs,JShellExecutionEngine.DEFAULT_JSHELL);
	}
}
