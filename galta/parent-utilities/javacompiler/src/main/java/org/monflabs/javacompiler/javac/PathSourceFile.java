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
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

import org.monflabs.javacompiler.SourceFactory;


/**
 * @author priand
 */
public class PathSourceFile extends SimpleJavaFileObject {

	private SourceFactory sourceFactory;
	private String className;
	private String fileName;

	public PathSourceFile(SourceFactory sourceFactory, String className) {
		super(URI.create("fs:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
		this.sourceFactory = sourceFactory;
		this.className = className;
		this.fileName = className.replace('.', '/') + Kind.SOURCE.extension;
	}

	public String getClassName() {
		return className;
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
		return sourceFactory.readString(fileName);
	}
}
