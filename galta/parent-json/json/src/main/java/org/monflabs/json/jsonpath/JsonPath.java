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
package org.monflabs.json.jsonpath;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.monflabs.json.JsonException;
import org.monflabs.json.jsonpointer.JsonPointer;

/**
 * JsonPath object.
 * 
 * It only handles a subset of the JsonPath spec, typically the field/index access.
 * 
 * @author priand
 */
public class JsonPath {

	private String jsonPath;
	private PathNode pathNode;
	private JsonPointer jsonPointer;
	private Boolean definite;

	public JsonPath(String jsonPath, PathNode pathNode) {
		this.jsonPath = jsonPath;
		this.pathNode = pathNode;
	}
	
	@Override
	public String toString() {
		return getJsonPath();
	}
	
	/**
	 * Return the json path source as a string.
	 * 
	 * @return the JsonPath representation as a string
	 */
	public String getJsonPath() {
		return jsonPath;
	}
	
	public PathNode getPathNode() {
		return pathNode;
	}
	
	public boolean isEmpty() {
		return pathNode==null;
	}
	
	public String canonicalPath() {
		StringBuilder b = new StringBuilder();
		b.append('$');
		for(Iterator<PathNode> it=nodeIterator(); it.hasNext(); ) {
			PathNode n = it.next();
			n.addCanonicalPath(b);
		}
		return b.toString();
	}
	
    /**
     * Checks if a path points to a single item or if it potentially returns multiple items.
     * a path is considered <strong>not</strong> definite if it contains a scan fragment ".."
     * or an array position fragment that is not based on a single index
     * 
     * @return true if path is definite (points to single item)
     */
	public boolean isDefinite() {
		if(definite==null) {
			definite = true;
			for(PathNode n=pathNode; n!=null; n=n.next()) {
				if(!n.isDefinite()) {
					definite = false;
					break;
				}
			}
		}
		return definite;
	}

	public Iterator<PathNode> nodeIterator() {
        return new Iterator<PathNode>() {
            private PathNode p = pathNode;
            @Override
            public boolean hasNext() {
                return p!=null;
            }
            @Override
            public PathNode next() {
            	if(p!=null) {
            		PathNode res = p;
            		p = p.next();
            		return res;
            	}
				throw new NoSuchElementException();
            }
        };	
    }

	/**
	 * Execute the Json path on the on given JSON value.
	 * 
	 * @return the execution result containing the values
	 */
	public JsonValues read(Object json) {
		return read(json,false);
	}
	
	/**
	 * Execute the Json path on the on given JSON value.
	 * 
	 * If the pointer parameter is not null, then the result will also contain the path
	 * relative to this pointer. A typical call will pass JsonPointer.EMPTY to have absolute paths.
	 * 
	 * @return the execution result containing the values and the corresponding JSON pointers
	 */
	public JsonValues read(Object json, boolean pointer) {
		return pointer ? read(json, JsonPointer.EMPTY) : read(json, (JsonPointer)null);
	}
	
	/**
	 * Execute the Json path on the given JSON value, the pointers of the result being
	 * relative to the base pointer. A null base means no pointers.
	 */
	public JsonValues read(Object json, JsonPointer base) {
		if(base!=null) {
			JsonValues r = new JsonValues(json, base);
			for(PathNode p=pathNode; p!=null; p=p.next()) {
				switch(r.getType()) {
					case EMPTY -> {
					}
					case VALUE -> { 
						Object v=r.value(); 
						JsonPointer vp=r.getPointer(); 
						r.clear(); 
						p.execute(r,json,v,vp); 
					}
					case LIST ->  {
						List<?> l=(List<?>)r.value; 
						List<JsonPointer> lp=(List<JsonPointer>)r.getPointers(); 
						r.clear(); 
						for(int i=0; i<l.size(); i++) {
							p.execute(r,json,l.get(i),lp.get(i));
						}
					}
				}
			}
			return r;
		} else {
			JsonValues r = new JsonValues(json);
			for(PathNode p=pathNode; p!=null; p=p.next()) {
				switch(r.getType()) {
					case EMPTY -> {
					}
					case VALUE -> { 
						Object v=r.value(); 
						r.clear(); 
						p.execute(r,json,v,null); 
					}
					case LIST ->  {
						List<?> l=(List<?>)r.value; 
						r.clear(); 
						for(int i=0; i<l.size(); i++) {
							p.execute(r,json,l.get(i),null);
						}
					}
				}
			}
			return r;
		}
	}
	
	/**
	 * Set the value corresponding to the path.
	 * The JSON path must be definite.Internally, it creates a a JSON pointer
	 * and uses its methods to set the value.
	 * The intermediate parts are created as needed.
	 * @param json
	 * @param value
	 */
	public boolean write(Object json, Object value) {
		return toJsonPointer().setValue(json, value);
	}

	
	/**
	 * Create a JSON pointer for that path.
	 * @return
	 */
	public synchronized JsonPointer toJsonPointer() {
		if(jsonPointer==null) {
			if(!isDefinite()) {
				throw new JsonException(null,"The JSON path must be definite to evaluate as a JSON pointer");
			}
			// Built from the parsed nodes rather than by re-parsing the source with the
			// (different) pointer parser, so both always agree on the syntax and on
			// member names vs indexes ($['0'] is the member "0", $[0] the index 0)
			java.util.List<Object> parts = new java.util.ArrayList<>();
			for(PathNode n=pathNode; n!=null; n=n.next()) {
				if(n instanceof PathIndex pi && pi.getIndex()!=null) {
					PathIndex.Index idx = pi.getIndex();
					if(idx instanceof PathIndex.Member m) {
						parts.add(m.getMember());
					} else if(idx instanceof PathIndex.SingleIndex si) {
						if(si.getLongIndex()!=si.getIndex()) {
							throw new JsonException(null,"The index {0} is too large for a JSON pointer", si.getLongIndex());
						}
						parts.add(si.getIndex());
					} else {
						throw new JsonException(null,"The JSON path must be definite to evaluate as a JSON pointer");
					}
				}
			}
			jsonPointer = JsonPointer.ofJsonPathParts(parts.toArray());

		}
		return jsonPointer;
	}
	
	public static final JsonPath parse(String jsonPath, int _start, boolean partial) {
		return parse(jsonPath, _start, partial, false);
	}
	
	/**
	 * Parse a JSON Path.
	 * @param strict if true, the path must comply with RFC 9535 (see {@link JsonPathParser})
	 */
	public static final JsonPath parse(String jsonPath, int _start, boolean partial, boolean strict) {
		if(jsonPath==null || jsonPath.isEmpty()) {
			if(strict && !partial) {
				throw new JsonException(null,"Json Path must start with a leading '$': the empty string is not a query");
			}
			return new JsonPath(jsonPath, null);
		}
		
		JsonPathParser m = new JsonPathParser(jsonPath,_start,strict);
		
		if(!m.match('$')) {
			if(partial) {
				return new JsonPath("", null);
			}
			throw new JsonException(null,"Json Path must start with a leading '$'");
		}
		
		PathNode node = m.readPathNode(partial);
		if(strict && !partial && m.getRemaining()==0) {
			char last = jsonPath.charAt(jsonPath.length()-1);
			if(last==' ' || last=='\t' || last=='\n' || last=='\r') {
				throw new JsonException(null,"Json Path cannot end with a blank: ''{0}''", jsonPath);
			}
		}
		String sPath = _start>0 || m.getRemaining()>0 ? jsonPath.substring(_start,m.getPtr()) : jsonPath;
		return new JsonPath( sPath, node);
	}
}
