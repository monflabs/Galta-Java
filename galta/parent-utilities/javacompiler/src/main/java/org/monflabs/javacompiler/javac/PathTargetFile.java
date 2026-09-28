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
package org.monflabs.javacompiler.javac;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

import org.monflabs.javacompiler.TargetFactory;


/**
 * @author priand
 */
public class PathTargetFile extends SimpleJavaFileObject {

	private TargetFactory targetFactory;
	private String className;
	private String fileName;

	public PathTargetFile(TargetFactory targetFactory, String className) {
		this(targetFactory, className, Kind.CLASS);
	}

	// The kind decides the extension: a generated source (e.g. from an annotation
	// processor) is a ".java" file, not a ".class" file
	public PathTargetFile(TargetFactory targetFactory, String className, Kind kind) {
		this(targetFactory, className.replace('.', '/') + kind.extension, kind, true);
		this.className = className;
	}

	// A file given by its path in the target (e.g. a resource created by an annotation processor)
	PathTargetFile(TargetFactory targetFactory, String fileName, Kind kind, boolean isFileName) {
		super(URI.create("fs:///" + fileName), kind);
		this.targetFactory = targetFactory;
		this.fileName = fileName;
	}

	public String getClassName() {
		return className;
	}

    @Override
    public OutputStream openOutputStream() throws IOException {
    	return targetFactory.openOutputStream(fileName);
    }
}
