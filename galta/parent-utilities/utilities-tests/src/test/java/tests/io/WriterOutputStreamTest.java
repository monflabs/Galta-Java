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
package tests.io;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.monflabs.util.io.WriterOutputStream;

import tests.ProjectTestCase;

public class WriterOutputStreamTest extends ProjectTestCase {
	
    public void testWrite() throws Exception {
        final String content = "Hello, товарищ! How are you?";
        
        StringWriter sw = new StringWriter();
        
        WriterOutputStream os = new WriterOutputStream(sw, StandardCharsets.UTF_8);
        os.write(content.getBytes(StandardCharsets.UTF_8));
        os.close();
        
        assertEquals(content, sw.toString());
    }
}
