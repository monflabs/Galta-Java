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
package org.monflabs.galtajs.modules.node;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.modules.JSNativeModule;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.util.path.FilesUtil;


public class FsModule extends JSNativeModule {
	
	public FsModule(JSEnvironment env, JSModuleDescriptor descriptor, FileSystem fs) {
		super(env, descriptor,  new Fs(env,fs),null);
	}
	
	private static class Fs extends NativeObject {
		
		private FileSystem fs;
		
		public Fs(JSEnvironment env, FileSystem fs) {
			super(env);
			this.fs = fs;
			setOwnMethod(new Method(env,MethodId.readFileSync,2));
			setOwnMethod(new Method(env,MethodId.writeFileSync,3));
		}

		@Override
		public String getClassName() {
			return "Fs";
		}	
		
		private static enum MethodId {
			readFileSync,
			writeFileSync,
		}
		
		private final class Method extends BaseMethod {
			private MethodId methodId;
			
			private Method(JSEnvironment env, MethodId methodId, int index) {
				super(env,methodId.name(),index);
				this.methodId = methodId;
			}
			
			/**
			 * Node accepts either an encoding name or an options object with an
			 * <code>encoding</code> property; a missing encoding means UTF-8.
			 */
			private Charset encoding(Object option) {
				JSEnvironment env = getEnvironment();
				Object enc = option;
				if(option!=null && option!=RuntimeUtil.UNDEFINED && !(option instanceof CharSequence) && RuntimeUtil.isObject(env, option)) {
					enc = RuntimeUtil.getProperty(env, option, "encoding");
				}
				if(enc==null || enc==RuntimeUtil.UNDEFINED) {
					return StandardCharsets.UTF_8;
				}
				String name = RuntimeUtil.toString(env, enc);
				try {
					return Charset.forName(name);
				} catch(IllegalArgumentException e) {
					throw RuntimeUtil.typeError("Unknown encoding '{0}'", name);
				}
			}

		    @Override
			public Object call(final Object obj, final Object[] args) {
		        switch(methodId){
	            	case readFileSync -> {
	            		if(args.length==0) {
	                    	throw RuntimeUtil.typeError("Missing file path");
	            		}
            			Path path = fs.getPath(paramString(args, 0));
            			Charset encoding = StandardCharsets.UTF_8;
	            		if(args.length>=2) {
	            			encoding = encoding(args[1]);
	            		}
	            		return FilesUtil.readString(path,encoding);
	            	}
	            	case writeFileSync -> {
	            		if(args.length==0) {
	                    	throw RuntimeUtil.typeError("Missing file path");
	            		}
            			Path path = fs.getPath(paramString(args, 0));
	            		if(args.length<2) {
	                    	throw RuntimeUtil.typeError("Missing data");
	            		}
            			String data = paramString(args, 1);
            			
            			Charset encoding = StandardCharsets.UTF_8;
	            		if(args.length>=3) {
	            			encoding = encoding(args[2]);
	            		}
	            		FilesUtil.writeString(path,data,encoding);
	            		return null;
	            	}
		            
		            default -> {
		    		    throw new IllegalStateException(); // Should never be here 
		            }
		        }
		    }
		}
	}
}