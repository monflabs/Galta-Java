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
package org.monflabs.json.impexp.file;

import org.monflabs.json.JsonException;

// Calculate a fast hash for a string 
public class FilenameHash { 
	
	public static String hash(String str, int levels) {
		// The hash is an int: 4 bytes, so 4 levels at most (more would only produce constant 00/FF levels)
		if(levels<=0 || levels>4) {
			throw new JsonException(null,"File hash levels must be between 1 and 4");
		}
        int len = str.length();
        int h = 1;
        int i = 0;
        for (; i + 3 < len; i += 4) {
            h =   31 * 31 * 31 * 31 * h 
            	+ 31 * 31 * 31 * str.charAt(i) 
            	+ 31 * 31  * str.charAt(i + 1) + 31 * str.charAt(i + 2) + str.charAt(i + 3);
        }
        for (; i < len; i++) {
            h = 31 * h + str.charAt(i);
        }
        
        StringBuilder b = new StringBuilder(levels*3-1);
        for(int l=0; l<levels; l++) {
        	if(l>0) {
        		b.append('/');
        	}
        	b.append( hexChar(h & 0x0F)); // reverse order
        	b.append( hexChar(h>>4 & 0x0F)); // reverse order
        	h >>= 8;
        }

        return b.toString();

    }
    private static final char hexChar(int v) {
        return (char)((v>=10) ? (v-10+'A') : (v+'0'));
    }

}
