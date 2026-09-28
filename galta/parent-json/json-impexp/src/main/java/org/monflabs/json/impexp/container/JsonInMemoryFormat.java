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
package org.monflabs.json.impexp.container;

// 1- List of records RECORDS
//    [ 'a', 'b', 'c' ]
// 2- List of records RECORDSWITHKEYS
//    [ {col:'col1',id:'k1',value:'a'}, {col:'col1',id:'k2',value:'b'}, {col:'col2',id:'k1',value:'c'} ]
// 3- List of records by collection RECORDSBYCOL
//    { col1: ['a','b','c'], col2: ['d','e'] }
// 4- List of records by key RECORDSBYKEY
//    { k1: 'a', k2: 'b', k3: 'c'}
// 5- List of records by collection and key RECORDSBYCOLKEY
//    { col1: { k1: 'a', k2: 'b', k3: 'c'}, col2: {k1:'d', k2:'e'} }
//
public enum JsonInMemoryFormat {
	RECORDS,
	RECORDSWITHKEYS,
	RECORDSBYCOL,
	RECORDSBYKEY,
	RECORDSBYCOLKEY,
}
