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
package org.monflabs.playground;

public final class PlaygroundConfiguration {

	private static PlaygroundConfiguration instance = new PlaygroundConfiguration();
	public static PlaygroundConfiguration get() {
		return instance;
	}
	
	private String frameTitle;
	private SnippetFactory snippetFactory;
	private ExecutionEngineFactory executionEngineFactory;
	private PlaygroundLayout layout;
	private boolean editable;
	
	public PlaygroundConfiguration() {
	}
	
	public String getFrameTitle() {
		return frameTitle;
	}
	public void setFrameTitle(String frameTitle) {
		this.frameTitle = frameTitle;
	}

	public SnippetFactory getSnippetFactory() {
		return snippetFactory;
	}
	public void setSnippetFactory(SnippetFactory snippetFactory) {
		this.snippetFactory = snippetFactory;
	}
	
	public PlaygroundLayout getLayout() {
		return layout;
	}
	public void setLayout(PlaygroundLayout layout) {
		this.layout = layout;
	}
	
	
	public boolean isEditable() {
		return editable;
	}
	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	public ExecutionEngineFactory getExecutionEngineFactory() {
		return executionEngineFactory;
	}
	public void setExecutionEngineFactory(ExecutionEngineFactory executionEngineFactory) {
		this.executionEngineFactory = executionEngineFactory;
	}

	public ExecutionEngine createExecutionEngine(ExecutionContext ctx) {
		return executionEngineFactory.createExecutionEngine(ctx);
	}
}
