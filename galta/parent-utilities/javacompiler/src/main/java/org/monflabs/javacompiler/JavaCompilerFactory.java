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
package org.monflabs.javacompiler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;

import org.monflabs.javacompiler.javac.JavaCompilerJavac;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.builder.Required;
import org.monflabs.util.path.FilesUtil;


/**
 * JDK's JavaC compiler that works out a FileSystem.
 * 
 * How do I programmatically compile Java class?
 *   https://kodejava.org/how-do-i-programmatically-compile-java-class/
 *   
 * InMemoryJavaCompiler
 *   https://github.com/trung/InMemoryJavaCompiler
 *   
 * Using Eclipse compiler to create dynamic Java objects
 *   https://blog.nobel-joergensen.com/2008/07/16/using-eclipse-compiler-to-create-dynamic-java-objects-2/
 * 
 * @author priand
 *
 */
public class JavaCompilerFactory {

	public static final class Builder extends ObjectBuilder<JavaCompiler> {
		@Required
		private ClassLoader classLoader;

		@Required
		private SourceFactory sourceFactory;
		
		@Required
		private TargetFactory targetFactory;
		
		private List<String> options;
		private boolean failOnWarnings;
		
		private Builder() {}
		public Builder classLoader(ClassLoader classLoader) {
			this.classLoader = classLoader;
			return this;
		}
		public Builder sourceFactory(SourceFactory sourceFactory) {
			this.sourceFactory = sourceFactory;
			return this;
		}
		public Builder sourceFolder(FileSystem fs, Charset cs) {
			this.sourceFactory = new PathFileFactory(FilesUtil.getRoot(fs),cs);
			return this;
		}
		public Builder sourceFolder(Path folder, Charset cs) {
			this.sourceFactory = new PathFileFactory(folder,cs);
			return this;
		}
		public Builder targetFactory(TargetFactory targetFactory) {
			this.targetFactory = targetFactory;
			return this;
		}
		public Builder targetFolder(FileSystem fs) {
			this.targetFactory = new PathFileFactory(FilesUtil.getRoot(fs),StandardCharsets.UTF_8);
			return this;
		}
		public Builder targetFolder(Path folder) {
			this.targetFactory = new PathFileFactory(folder,StandardCharsets.UTF_8);
			return this;
		}
		public Builder options(List<String> options) {
			this.options = options;
			return this;
		}
		public Builder failOnWarnings(boolean failOnWarnings) {
			this.failOnWarnings = failOnWarnings;
			return this;
		}
		
		// The @Required annotations are only checked in debug mode: check them always
		@Override
		protected void validate() {
			assertNotNull(classLoader, "classLoader");
			assertNotNull(sourceFactory, "sourceFactory");
			assertNotNull(targetFactory, "targetFactory");
		}

		@Override
		protected JavaCompiler _build() {
			return new JavaCompilerJavac(classLoader,sourceFactory,targetFactory,options,failOnWarnings);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}
}
