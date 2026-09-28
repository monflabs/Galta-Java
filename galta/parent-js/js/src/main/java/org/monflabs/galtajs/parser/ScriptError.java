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


/**
 * Compilation error.
 */
public class ScriptError {

    public static final int WARNING = 0;
    public static final int ERROR   = 1;

    private int type;
    private int code;
    private String message;
    private int errorLine;
    private int errorCol;

    public ScriptError(int type, int code, String message, int errorLine, int errorCol) {
        this.type = type;
        this.code = code;
        this.message = message;
        this.errorLine = errorLine;
        this.errorCol = errorCol;
    }

    /**
     * @return Returns the code.
     */
    public int getCode() {
        return code;
    }

    /**
     * @return Returns the errorCol.
     */
    public int getErrorCol() {
        return errorCol;
    }

    /**
     * @return Returns the errorLine.
     */
    public int getErrorLine() {
        return errorLine;
    }

    /**
     * @return Returns the message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return Returns the type.
     */
    public int getType() {
        return type;
    }
}
