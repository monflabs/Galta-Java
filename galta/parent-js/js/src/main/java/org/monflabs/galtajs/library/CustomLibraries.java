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

import java.util.ArrayList;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSLibrary;
import org.monflabs.galtajs.library.java.JSJavaLibrary;
import org.monflabs.galtajs.library.java.JavaLibrary;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;


/**
 * Library aggregation.
 */
public class CustomLibraries {

	private ArrayList<JSLibrary> libraries = new ArrayList<JSLibrary>();
	private JavaLibrary javaLibrary;

	public CustomLibraries() {
	}
	
	public JSJavaLibrary getJavaLibrary() {
		return javaLibrary;
	}

	public void configureEnvironment(JSEnvironment.Builder builder) {
		for(int i=0; i<libraries.size(); i++) {
			libraries.get(i).configureEnvironment(builder);
		}
		if(javaLibrary!=null) {
			javaLibrary.configureEnvironment(builder);
		}
	}
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		for(int i=0; i<libraries.size(); i++) {
			libraries.get(i).configureStandardObjects(env, standardObjects);
		}
		if(javaLibrary!=null) {
			javaLibrary.configureStandardObjects(env, standardObjects);
		}
	}

	public void addLibrary(JSLibrary library) {
		if(library instanceof JavaLibrary jl) {
			if(javaLibrary!=null) {
				throw new IllegalStateException("Java Library is already added");
			}
			javaLibrary = jl;
		} else {
			libraries.add(library);
		}
	}
	
	public JSAccessor createAccessor(JSEnvironment env, Class<?> clazz) {
		for(int i=0; i<libraries.size(); i++) {
			JSAccessor o = libraries.get(i).createAccessor(env, clazz);
			if(o!=null) {
				return o;
			}
		}
		if(javaLibrary!=null) {
			return javaLibrary.createAccessor(env, clazz);
		}
		return null;
	}
}
