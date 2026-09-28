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
package org.monflabs.galtajs.parser;

import java.io.PrintStream;
import java.util.ArrayList;

/**
 * List of parsing errors.
 */
public class ErrorList {

    private ArrayList<ScriptError> errors;

    ErrorList() {
        this.errors = new ArrayList<ScriptError>();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public ScriptError getError(int index) {
        return errors.get(index);
    }

    public void addError(ScriptError err) {
        errors.add(err);
    }

    public void dumpErrors(PrintStream ps) {
        ps.println(getErrorCount()+" errors");
        for( int i=0; i<getErrorCount(); i++) {
            ScriptError err = getError(i);
            ps.println(err.getErrorLine()+","+err.getErrorCol()+": "+err.getMessage());
        }
    }
}
