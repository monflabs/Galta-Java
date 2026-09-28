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
package org.monflabs.galtajs.rt.builtins.standard.regexp.jdk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Ground-truth Unicode property-escape codepoint data (one entry per exact
 * ECMAScript `\p{...}` argument string, e.g. "Script=Greek", "gc=Lu",
 * "ASCII"), extracted directly from test262's own generated
 * property-escapes test suite (Unicode v17.0.0). Used in preference to
 * java.util.regex's native `\p{IsXxx}` support, whose recognized name set
 * and bundled Unicode version both fall short of what ECMA-262 requires.
 */
public final class UnicodePropertyData {

    private static final String RESOURCE = "unicode-properties.txt";
    private static volatile Map<String,int[]> data;

    private UnicodePropertyData() {
    }

    public static int[] getRanges(String propertyExpr) {
        return data().get(propertyExpr);
    }

    private static Map<String,int[]> data() {
        Map<String,int[]> d = data;
        if(d==null) {
            synchronized(UnicodePropertyData.class) {
                d = data;
                if(d==null) {
                    d = load();
                    data = d;
                }
            }
        }
        return d;
    }

    private static Map<String,int[]> load() {
        Map<String,int[]> map = new HashMap<>();
        try(InputStream in = UnicodePropertyData.class.getResourceAsStream(RESOURCE)) {
            if(in==null) {
                return map;
            }
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while((line = reader.readLine())!=null) {
                    int tab = line.indexOf('\t');
                    if(tab<0) {
                        continue;
                    }
                    String name = line.substring(0, tab);
                    String[] parts = line.substring(tab+1).split(",");
                    int[] ranges = new int[parts.length*2];
                    for(int i=0; i<parts.length; i++) {
                        String p = parts[i];
                        int dash = p.indexOf('-');
                        if(dash<0) {
                            int cp = Integer.parseInt(p, 16);
                            ranges[i*2] = cp;
                            ranges[i*2+1] = cp;
                        } else {
                            ranges[i*2] = Integer.parseInt(p.substring(0,dash), 16);
                            ranges[i*2+1] = Integer.parseInt(p.substring(dash+1), 16);
                        }
                    }
                    map.put(name, ranges);
                }
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }
}
