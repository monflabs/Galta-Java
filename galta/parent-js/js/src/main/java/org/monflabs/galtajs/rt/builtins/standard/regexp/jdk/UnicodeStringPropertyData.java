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
 * Ground-truth data for ECMAScript's "binary properties of strings" - the
 * handful of {@code \p{...}} names (RGI_Emoji, Basic_Emoji, and their
 * sequence variants) whose VALUE is a set of multi-codepoint sequences
 * rather than individual codepoints, so they can't be represented by
 * {@link UnicodePropertyData}'s flat codepoint-range model at all. Only
 * used by 'v'-mode (unicodeSets) character classes - see {@code
 * VClassParser} - since these properties are a syntax error anywhere else.
 *
 * <p>Derived directly from test262's own {@code
 * property-escapes/generated/strings/*.js} test files (each such file's
 * {@code matchStrings} array is, by construction, the exhaustive set of
 * sequences the property must match) rather than from Unicode's published
 * {@code emoji-sequences.txt}/{@code emoji-zwj-sequences.txt}, which
 * weren't available to consult directly - see
 * docs/GaltaJS/KnownGaps.md's "RegExp v-flag" section for the extraction
 * method.
 */
public final class UnicodeStringPropertyData {

    private static final String RESOURCE = "unicode-string-properties.txt";
    private static volatile Map<String,int[][]> data;

    private UnicodeStringPropertyData() {
    }

    /** Returns the property's codepoint sequences (each element one member of the set), or null if unrecognized. */
    public static int[][] getSequences(String propertyName) {
        return data().get(propertyName);
    }

    private static Map<String,int[][]> data() {
        Map<String,int[][]> d = data;
        if(d==null) {
            synchronized(UnicodeStringPropertyData.class) {
                d = data;
                if(d==null) {
                    d = load();
                    data = d;
                }
            }
        }
        return d;
    }

    private static Map<String,int[][]> load() {
        Map<String,int[][]> map = new HashMap<>();
        try(InputStream in = UnicodeStringPropertyData.class.getResourceAsStream(RESOURCE)) {
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
                    String rest = line.substring(tab+1);
                    String[] seqParts = rest.isEmpty() ? new String[0] : rest.split(",");
                    int[][] sequences = new int[seqParts.length][];
                    for(int i=0; i<seqParts.length; i++) {
                        String[] cps = seqParts[i].split("-");
                        int[] seq = new int[cps.length];
                        for(int k=0; k<cps.length; k++) {
                            seq[k] = Integer.parseInt(cps[k], 16);
                        }
                        sequences[i] = seq;
                    }
                    map.put(name, sequences);
                }
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }
}
