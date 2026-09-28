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
package org.monflabs.galtajs.rt.builtins.standard.regexp;

import java.util.Iterator;

import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.rt.JSRuntimeContext;

/**
 * RegExp engine.
 * 
 * Rhino with Java or Joni
 *     https://github.com/joelhockey/rhino-mirror/blob/master/src/org/mozilla/javascript/regexp/REJavaUtilRegex.java
 *   
 * ES6Draft with Joni
 *   https://github.com/anba/es6draft/tree/master/src/main/java/com/github/anba/es6draft/regexp
 *   
 * Nashorn using Joni
 *   https://github.com/JetBrains/jdk8u_nashorn/tree/master/src/jdk/nashorn/internal/runtime/regexp
 *   
 * Joni: https://github.com/jruby/joni
 * 
 * Could also use Joni https://github.com/jruby/joni
 * 
 * see: https://bugzilla.mozilla.org/show_bug.cgi?id=390659
 */
public interface RegExpEngine {
	
    public JSArray exec(JSRuntimeContext context, String str);
    public boolean test(JSRuntimeContext context, String str);
    public JSArray split(JSRuntimeContext context, String str, int limit);
    public JSArray match(JSRuntimeContext context, String str);
    public Iterator<JSArray> matchAll(JSRuntimeContext context, String str);
    public int search(JSRuntimeContext context, String str);
    public String replace(JSRuntimeContext context, String str, Object replace);
}