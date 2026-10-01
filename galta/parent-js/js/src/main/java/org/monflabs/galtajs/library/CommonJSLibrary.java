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
package org.monflabs.galtajs.library;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSEnvironment.Builder;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.util.PathUtil;


/**
 * Simple common JS library
 * 
 * http://wiki.commonjs.org/wiki/Modules/1.1
 */
public class CommonJSLibrary extends GlobalLibrary {
	
	static enum FunctionIndex {
		require,
	}

	public CommonJSLibrary() {
	}
	
	@Override
	public void configureEnvironment(Builder builder) {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		standardObjects.setOwnMethod(new GlobalFunction(env,"require",FunctionIndex.require,1));
	}
	
	private final static class GlobalFunction extends BaseMethod {
		private FunctionIndex index;

		GlobalFunction(JSEnvironment env, String functionName, FunctionIndex index, int length) {
			super(env,functionName,length);
			this.index = index;
		}

		@Override
		protected Object invoke(Object _this, Object[] parameters) {
			switch(index) {
				case require: {
					String name = paramString(parameters, 0);
					// Extension, load as is
					if(PathUtil.FILE_AGNOSTIC.hasFileExtension(name)) {
						JSModule mod = RuntimeUtil.importModule(JSRuntimeContext.get(), name);
						return mod.getDefaultExport();
					}
					// No extension, multiple tries
					JSException jse;
					try {
						JSModule mod = RuntimeUtil.importModule(JSRuntimeContext.get(), name);
						return mod.getDefaultExport();
					} catch(JSException ex) {
						jse = ex;
					}
					try {
						JSModule mod = RuntimeUtil.importModule(JSRuntimeContext.get(), PathUtil.FILE_AGNOSTIC.setExtension(name,"js"));
						return mod.getDefaultExport();
					} catch(JSException ex) {
					}
// We don't do JSON for now
//					try {
//						JSModule mod = RuntimeUtil.importModule(JSRuntimeContext.get(), PathUtil.FILE_AGNOSTIC.setExtension(name,"json"));
//						return mod.getDefaultExport();
//					} catch(JSException ex) {
//					}
					throw jse;
				}
				default: {
				    throw new IllegalStateException(); // Should never be here 
				}
			}
		}
	}
}