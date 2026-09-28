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

package org.monflabs.js.debugger.ui.model;

/**
 * A parsed script the server told us about.
 * @param scriptId the server's id, the key for source and locations
 * @param url the script's url; the stable identity across re-parses
 * @param endLine the last line, zero based
 * @param module whether it is an ES module
 */
public record ScriptInfo(String scriptId, String url, int endLine, boolean module) {
    /** A short name for the navigator: the last path segment of the url. */
    public String shortName() {
        final int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(slash + 1);
    }
}
