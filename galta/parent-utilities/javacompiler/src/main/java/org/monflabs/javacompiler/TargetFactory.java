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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

public interface TargetFactory {
	
	/**
	 * Open an output stream get get the class bytes from the compiler
	 * @param fileName
	 * @return the output stream
	 */
	public OutputStream openOutputStream(String fileName);
	
	/**
	 * Read bytes if the file exists
	 * @param fileName
	 * @return the bytes, or null is the file doesn't exist
	 * @throws IOException
	 */
	public byte[] readBytes(String fileName) throws IOException;

	/**
	 * List the class files directly contained in a package folder.
	 * <p>
	 * This is what lets javac compile against classes produced by an earlier compilation
	 * (in this target, or in the target of a parent {@link FactoryClassLoader}). A factory
	 * that cannot list its content returns an empty collection: its classes can then still
	 * be loaded, but not referenced by later compilations.
	 * @param packageFolder the package, as a folder ("com/acme"), or "" for the default package
	 * @return the file names, relative to the factory root ("com/acme/Greeter.class")
	 * @throws IOException
	 */
	public default Collection<String> listClassFiles(String packageFolder) throws IOException {
		return Collections.emptyList();
	}

	/**
	 * List the class files of a package folder, including its sub packages when
	 * <code>recurse</code> is true (javac asks for it through JavaFileManager.list()).
	 * The default implementation only lists the package itself.
	 * @param packageFolder the package, as a folder ("com/acme"), or "" for the default package
	 * @param recurse true to include the sub packages
	 * @return the file names, relative to the factory root
	 * @throws IOException
	 */
	public default Collection<String> listClassFiles(String packageFolder, boolean recurse) throws IOException {
		return listClassFiles(packageFolder);
	}
}