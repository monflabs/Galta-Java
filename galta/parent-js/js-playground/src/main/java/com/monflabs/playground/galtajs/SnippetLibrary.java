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
package com.monflabs.playground.galtajs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.GlobalLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.util.path.FilesUtil;


/**
 * Snippet runtime library.
 */
public class SnippetLibrary extends GlobalLibrary {
	
	public static final String PROP_EXECUTION_CONTEXT = "playground.execution.context";

	public SnippetLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		// Global functions
		standardObjects.setOwnMethod(new GlobalFunction(env,"loadText",FunctionIndex.loadText,1));
		standardObjects.setOwnMethod(new GlobalFunction(env,"loadJson",FunctionIndex.loadJson,1));
	}
	
	static enum FunctionIndex {
		loadText,
		loadJson,
	}

	private final static class GlobalFunction extends BaseMethod {
		private FunctionIndex index;

		GlobalFunction(JSEnvironment env, String functionName, FunctionIndex index, int length) {
			super(env,functionName,length);
			this.index = index;
		}

		@Override
		public Object call(Object _this, Object[] args) {
			switch(index) {
				case loadText: { 
					ExecutionContext ec = (ExecutionContext)JSRuntimeContext.get().getGlobalContext().getProperty(PROP_EXECUTION_CONTEXT);
					String fileName = paramString(args, 0);
					Path file = ec.getSnippetFs().getPath(fileName);
					if(Files.isRegularFile(file)) {
						String text = FilesUtil.readString(file,StandardCharsets.UTF_8);
						return text;
					}
					throw RuntimeUtil.error("File {0} doesn't exist", fileName);
				}
				case loadJson: { 
					ExecutionContext ec = (ExecutionContext)JSRuntimeContext.get().getGlobalContext().getProperty(PROP_EXECUTION_CONTEXT);
					String fileName = paramString(args, 0);
					Path file = ec.getSnippetFs().getPath(fileName);
					if(Files.isRegularFile(file)) {
						String text = FilesUtil.readString(file,StandardCharsets.UTF_8);
						return getEnvironment().getJsonFactory().parse(text);
					}
					throw RuntimeUtil.error("File {0} doesn't exist", fileName);
				}
				default: {
				    throw new IllegalStateException(); // Should never be here 
				}
			}
		}
	}

}