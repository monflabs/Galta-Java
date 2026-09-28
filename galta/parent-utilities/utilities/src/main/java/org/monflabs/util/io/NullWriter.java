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

import java.io.IOException;
import java.io.Writer;

public class NullWriter extends Writer {

	public static final Writer instance = new NullWriter();

    private NullWriter() {
    }
    
    @Override
	public void write(char cbuf[], int off, int len) throws IOException {
    }
    
    @Override
	public void write(char cbuf[]) throws IOException {
    }
    
    @Override
	public void write(int c) throws IOException {
    }
    
    @Override
	public void write(String str, int off, int len) throws IOException {
    }
    
    @Override
	public void write(String str) throws IOException {
    }
    
    @Override
	public void flush() throws java.io.IOException {
    }
    
    @Override
	public void close() throws java.io.IOException {
    }
}
