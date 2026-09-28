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

import java.util.Map;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpointer.JsonPointer;

/**
 * Access to an array index
 * 
 * @author priand
 */
public class PathIndex extends PathNode {
	
	public static Index addIndex(Index prior, int start) {
		Index i = new SingleIndex(start);
		if(prior!=null) {
			prior.next = i;
		}
		return i;
	}
	
	public static Index addRange(Index prior, Integer start, Integer end, Integer inc) {
		Index i = new RangeIndex(start,end,inc);
		if(prior!=null) {
			prior.next = i;
		}
		return i;
	}

	public static Member addMember(Index prior, String member) {
		Member m = new Member(member);
		if(prior!=null) {
			prior.next = m;
		}
		return m;
	}

	public static Flatten addFlatten(Index prior) {
		Flatten m = new Flatten();
		if(prior!=null) {
			prior.next = m;
		}
		return m;
	}

	public static Filter addFilter(Index prior, ExprNode expr) {
		Filter m = new Filter(expr);
		if(prior!=null) {
			prior.next = m;
		}
		return m;
	}

	
	public static abstract class Index {
		private Index next;
		public final Index next() {
			return next;
		}
		public abstract boolean isDefinite();
		abstract void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer);
	}
	
	public static final class SingleIndex extends Index {
		private int start;
		private SingleIndex(int start) {
			this.start = start;
		}
		@Override
		public String toString() {
			return Integer.toString(start);
		}
		public int getIndex() {
			return start;
		}
		@Override
		public boolean isDefinite() {
			return true;
		}
		@Override
		void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer) {
			if(c instanceof JsonArray a) {
				int idx = a.actualIndex(start);
				if(idx>=0 && idx<a.size()) {
					if(sourcePointer!=null) {
						r._add(a.get(idx),sourcePointer.getChild(idx));
					} else {
						r._add(a.get(idx));
					}
				}
			} else if(c instanceof JsonObject o) {
				if(JsonPathParser.RELAXED_SYNTAX) {
					String member = Integer.toString(start);
					if(o.has(member)) {
						if(sourcePointer!=null) {
							r._add(o.get(member),sourcePointer.getChild(member));
						} else {
							r._add(o.get(member));
						}
					}
				}
			}
		}
	}
	
	public static final class RangeIndex extends Index {
		private Integer start;
		private Integer end;
		private Integer inc;
		private RangeIndex(Integer start, Integer end, Integer inc) {
			this.start = start;
			this.end = end;
			this.inc = inc;
		}
		@Override
		public String toString() {
			return       (start!=null?Integer.toString(start):"")
					+":"+(end!=null?Integer.toString(end):"")
					+":"+(inc!=null?Integer.toString(inc):"");
		}
		@Override
		public boolean isDefinite() {
			return false;
		}
		@Override
		void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer) {
			if(c instanceof JsonArray a) {
				int inc = this.inc!=null ? this.inc : 1;
				if(inc==0) {
					// RFC 9535: a step of 0 selects nothing
					return;
				}
				int size = a.size();
				
				if(inc>0) {
					int start = this.start!=null ? this.start : 0; 
					int end = this.end!=null ? this.end : Integer.MAX_VALUE; 
					int bStart = Math.max( 0, Math.min( size, start<0 ? start + size : start) );
					int bEnd   = Math.max( 0, Math.min( size, end<0 ? end + size : end) );
					if(bStart<size && bStart<bEnd) {
						// long: idx+inc must not overflow for a large step
						for(long l=bStart; l<bEnd; l+=inc) {
							int idx = (int)l;
							if(sourcePointer!=null) {
								r._add(a.get(idx),sourcePointer.getChild(idx));
							} else {
								r._add(a.get(idx));
							}
						}
					}
				} else {
					int start = this.start!=null ? this.start : Integer.MAX_VALUE; 
					int end = this.end!=null ? this.end : Integer.MIN_VALUE; 
					// The lower clamp is -1 (RFC 9535): an empty array, or a start below -size,
					// yields nothing rather than element 0
					int bStart = Math.max( -1, Math.min( size-1, start<0 ? start + size : start) );
					int bEnd   = Math.max( -1, Math.min( size, end<0 ? end + size : end) );
					if(bStart>=0 && bStart>bEnd) {
						for(long l=bStart; l>bEnd; l+=inc) {
							int idx = (int)l;
							if(sourcePointer!=null) {
								r._add(a.get(idx),sourcePointer.getChild(idx));
							} else {
								r._add(a.get(idx));
							}
						}
					}
				}
			}
		}
	}
	
	public static final class Member extends Index {
		
		private String member;
		
		private Member(String member) {
			this.member = member;
		}
		
		public String getMember() {
			return member;
		}
		
		@Override
		public String toString() {
			return JsonUtil.encodeString(member,'\'');
		}
		
		@Override
		public boolean isDefinite() {
			return true;
		}
		
		@Override
		void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer) {
			if(c instanceof JsonObject o) {
				if(o.has(member)) {
					if(sourcePointer!=null) {
						r._add(o.get(member),sourcePointer.getChild(member));
					} else {
						r._add(o.get(member));
					}
				}
			}
		}
	}
	
	public static final class Flatten extends Index {
		
		public Flatten() {
		}
		
		@Override
		public String toString() {
			return "*";
		}
		
		@Override
		public boolean isDefinite() {
			return false;
		}
		
		@Override
		public void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer) {
			if(c instanceof JsonArray a) {
				int sz = a.size();
				for(int i=0; i<sz; i++) {
					if(sourcePointer!=null) {
						r._add(a.get(i),sourcePointer.getChild(i));
					} else {
						r._add(a.get(i));
					}
				}
			} else if(c instanceof JsonObject o) {
				for(Map.Entry<String, Object> e: o.entrySet()) {
					if(sourcePointer!=null) {
						r._add(e.getValue(),sourcePointer.getChild(e.getKey()));
					} else {
						r._add(e.getValue());
					}
				}
			}
		}
	}
	
	public static final class Filter extends Index {

		private ExprNode expr;

		public Filter(ExprNode expr) {
			this.expr = expr;
		}
		
		@Override
		public String toString() {
			return "?(" + expr.toString() + ")";
		}
		
		@Override
		public boolean isDefinite() {
			return false;
		}
		
		@Override
		public void extract(JsonValues r, Object root, Object c, JsonPointer sourcePointer) {
			if(c instanceof JsonArray a) {
				int sz = a.size();
				for(int i=0; i<sz; i++) {
					boolean b = expr.execute(root,a.get(i));
					if(b) {
						if(sourcePointer!=null) {
							r._add(a.get(i),sourcePointer.getChild(i));
						} else {
							r._add(a.get(i));
						}
					}
				}
			} else if(c instanceof JsonObject o) {
				for(Map.Entry<String, Object> e: o.entrySet()) {
					boolean b = expr.execute(root,e.getValue());
					if(b) {
						if(sourcePointer!=null) {
							r._add(e.getValue(),sourcePointer.getChild(e.getKey()));
						} else {
							r._add(e.getValue());
						}
					}
				}
			} 
		}
	}


	private Index index;
	
	public PathIndex(PathNode next, Index index) {
		super(next);
		this.index = index;
	}
	
	@Override
	public void addCanonicalPath(StringBuilder b) {
		// User friendly '.member' if possible
		if(index instanceof Member m && index.next==null) {
			String s = m.member;
			if(JsonUtil.isIdentifier(s)) {
				// if the last char in the buffer is '.', it means '..' and we should not add a '.'
				if(b.length()==0 || b.charAt(b.length()-1)!='.') {
					b.append('.');
				}
				b.append(s);
				return;
			}
		}
		b.append('[');
		for(Index i=index; i!=null; i=i.next()) {
			if(i!=index) { // Not the first
				b.append(",");
			}
			b.append(i.toString());
		}
		b.append(']');
	}
	
	public Index getIndex() {
		return index;
	}
	
	@Override
	public boolean isDefinite() {
		if(index==null || (index.next()==null && index.isDefinite()) ) {
			return true;
		}
		return false;
	}
	
	@Override
	public void execute(JsonValues r, Object root, Object source, JsonPointer sourcePointer) {
		for(Index i=index; i!=null; i=i.next) {
			i.extract(r, root, source, sourcePointer);
		}
	}
}
