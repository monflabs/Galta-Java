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
package org.monflabs.galtajs.modules;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.JSException;
import org.monflabs.util.StringUtil;

/**
 * Utilities to deal with modules
 */
public final class ModuleUtil {
	
	public static final String MODULE = "module";
	public static final String EXPORTS = "exports";
		
	public static String resolvePath(String parentName, String name) {
		List<String> finalName = new ArrayList<>();
		
		String[] parts = StringUtil.splitString(name, '/');
		
		// If the path if relative, then add the parent name folder (not the last part
		// which is the file name)
		// The parent path is already supposed to be absolute and normalized
		if(parts[0].equals(".") || parts[0].equals("..")) {
			String[] parentParts = StringUtil.splitString(parentName, '/');
			for(int i=0; i<parentParts.length-1; i++) {
				finalName.add(parentParts[i]);
			}
 		}
		
		// Now add the name parts
		for(int i=0; i<parts.length; i++) {
			String p = parts[i];
			if(StringUtil.isEmpty(p) || p.equals(".")) {
				continue;
			}
			if(p.equals("..")) {
				if(finalName.isEmpty()) {
					throw new JSException(null, "Invalid module name: {0}, parent: {1}",name,parentName);
				}
				finalName.remove(finalName.size()-1);
			} else {
				finalName.add(p);
			}
		}
		
		StringBuilder b = new StringBuilder(128);
		for(int i=0; i<finalName.size(); i++) {
			if(b.length()>0) {
				b.append('/');
			}
			b.append(finalName.get(i));
		}
		return b.toString();
	}

	/**
	 * Error for a module that exists but cannot be loaded: JS errors are rethrown as is
	 * (unwrapped from reflection), others are wrapped with the module name.
	 */
	public static RuntimeException loadError(Throwable e, String moduleName) {
		if(e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause()!=null) {
			e = ite.getCause();
		}
		if(e instanceof org.monflabs.galtajs.rt.JSRuntimeException jre) {
			return jre;
		}
		return org.monflabs.galtajs.rt.RuntimeUtil.error(e, "Error while loading module '{0}'", moduleName);
	}
}
