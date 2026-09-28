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
package org.monflabs.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

import org.monflabs.util.ForwardRuntimeException;

/**
 * Stream Supplier utilities.
 */
public class StreamSuppliers {

	public static Supplier<InputStream> fileInputStream(File file) {
		return () -> {
			try { return new FileInputStream(file); } catch(IOException ex) {throw new ForwardRuntimeException(ex);}
		};
	}

	public static Supplier<OutputStream> fileOutputStream(File file) {
		return () -> {
			try { return new FileOutputStream(file); } catch(IOException ex) {throw new ForwardRuntimeException(ex);}
		};
	}
}
