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
package org.monflabs.util;

import java.io.File;

/**
 * 
 */ 
public class PathUtil {

	public static final char POSIX_SEP = '/';
	public static final char WIN_SEP = '\\';
	
	public static PathUtil of(char sep) {
		return new PathUtil(sep);
	}
	public static PathUtil of(char sep, char sep2) {
		return new PathUtil(sep,sep2);
	}

	public static final PathUtil POSIX = of(POSIX_SEP);
	public static final PathUtil WIN = of(WIN_SEP);
	public static final PathUtil FILE = of(File.separatorChar);
	public static final PathUtil FILE_AGNOSTIC = of(File.separatorChar,File.separatorChar==POSIX_SEP?WIN_SEP:POSIX_SEP);
	
	public static final PathUtil DOT = of('.');
	
//	public static String standardSeparator(String source) {
//		return StringUtil.replaceAll(source, WIN_SEP, POSIX_SEP);
//	}
//	public static String pathSeparator(String source, char sep) {
//		return StringUtil.replaceAll(source, POSIX_SEP, sep);
//	}
	
	private char sep;
	private char sep2;
	
	private PathUtil(char sep) {
		this.sep = sep;
	}
	private PathUtil(char sep, char sep2) {
		this.sep = sep;
		if(sep!=sep2) {
			this.sep2 = sep2;
		}
	}
	
	public String normalize(String path) {
		if(sep2!=0) {
			return StringUtil.replaceAll(path, sep2, sep);
		}
		return path;
	}
	
	public char getSep() {
		return sep;
	}

	public char getSep2() {
		return sep2;
	}
	
	private int firstSeparator(String path, int start) {
		if(sep2!=0) {
			int p1 = path.indexOf(sep,start);
			int p2 = path.indexOf(sep2,start);
			if(p1<0) return p2;
			if(p2<0) return p1;
			return Math.min(p1,p2);
		}
		return path.indexOf(sep,start);
	}
	private int lastSeparator(String path) {
		if(sep2!=0) {
			int p1 = path.lastIndexOf(sep);
			int p2 = path.lastIndexOf(sep2);
			if(p1<0) return p2;
			if(p2<0) return p1;
			return Math.max(p1,p2);
		}
		return path.lastIndexOf(sep);
	}
	private boolean isSeparator(char c) {
		if(sep2!=0) {
			return c==sep || c==sep2;
		}
		return c==sep;
	}
	private boolean startsWith(String path, String start) {
		if(sep2!=0) {
			return normalize(path).startsWith(normalize(start));
		}
		return path.startsWith(start);
	}
	
	public String getParentPath(String path) {
    	if(path!=null) {
			if(StringUtil.isEmpty(path)) {
				return null;
			}
			int pos = lastSeparator(path);
			if(pos<0) {
				return "";
			} else {
				return path.substring(0,pos);
			}
		}
		return null;
	}
	
    public String concat(String path1, String path2) {
    	if(StringUtil.isEmpty(path1)) {
    		return StringUtil.isEmpty(path2) ? "" : path2;
    	}
    	if(StringUtil.isEmpty(path2)) {
    		return StringUtil.isEmpty(path1) ? "" : path1;
    	}
    	return removeTrailingSep(path1)+sep+removeLeadingSep(path2);
    } 
    public String concat(String... paths) {
        if (paths.length == 0) return "";
        if (paths.length == 1) return paths[0];
        
        StringBuilder b = new StringBuilder(128);
        for (String path : paths) {
            if (path == null || path.isEmpty()) continue;
            
            int start = 0;
            int end = path.length();
            
            // Remove leading separators
            if (b.length()>0) {
	            if (start < end && isSeparator(path.charAt(start))) {
	                start++;
	            }
            }
            if (start == end) continue;
            
            // Add separator if needed
            int len = b.length();
            if (len > 0 && !isSeparator(b.charAt(len-1))) {
                b.append(sep);
            }
            
            b.append(path, start, end);
        }
        
        return b.toString();
    }

    
    public String[] getParts(String path) {
    	if(path!=null) {
	    	if(StringUtil.isNotEmpty(path)) {
	    		return splitString(removeSep(path));
	    	}
    	}
    	return StringUtil.EMPTY_STRING_ARRAY;
    }
    private String[] splitString( String s) {
        if( s==null ) {
            return StringUtil.EMPTY_STRING_ARRAY;
        }
        // Iterative: the recursive version used one stack frame per path segment
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        int pos = 0;
        int newPos;
        while( (newPos=firstSeparator(s, pos))>=0 ) {
            parts.add( s.substring( pos, newPos ) );
            pos = newPos+1;
        }
        parts.add( s.substring( pos ) );
        return parts.toArray(new String[parts.size()]);
    }
    
    
    public String compose(String[] parts) {
    	return compose(parts,0,Integer.MAX_VALUE);
    }
    public String compose(String[] parts, int offset, int limit) {
    	if(parts!=null) {
    		StringBuilder b = new StringBuilder();
    		int max = (int)Math.min((long)parts.length,(long)offset+(long)limit);
    		for(int i=offset; i<max; i++) {
    			if(i>offset) {
    				b.append(sep);
    			}
    			b.append(parts[i]);
    		}
    		return b.toString();
    	}
    	return "";
    }

    
    public String removeLeadingSep(String path) {
    	if(path!=null) {
	    	if(StringUtil.isNotEmpty(path)) {
	    		if(isSeparator(path.charAt(0)) ) {
	    			return path.substring(1);
	    		}
	    	}
    	}
    	return path;
    }

    
    public String removeTrailingSep(String path) {
    	if(path!=null) {
	    	if(StringUtil.isNotEmpty(path)) {
	    		if(isSeparator(path.charAt(path.length()-1))) {
	    			return path.substring(0,path.length()-1);
	    		}
	    	}
    	}
    	return path;
    }

    
    public String removeSep(String path) {
    	if(path!=null) {
        	return removeLeadingSep(removeTrailingSep(path));
    	}
    	return path;
    }

    
    public String getFileName(String path) {
    	if(path!=null) {
	    	int pos = lastSeparator(path);
	    	if(pos>=0) {
	    		path = path.substring(pos+1);
	    	}
    	}
    	return path;
    }

    
    public boolean hasFileExtension(String path) {
    	if(path!=null) {
	    	int pos = Math.max(0, lastSeparator(path));
	    	int ext = path.lastIndexOf('.');
	    	if(ext>=pos) {
	    		return true;
	    	}
    	}
    	return false;
    }

    
    /**
     * Returns the extension of the last name of the path, without the dot: "" when there
     * is none, including for a dotfile like ".bashrc" (as FilesUtil.getFileExtension()), and
     * null for a null path.
     */
    public String getFileExtension(String path) {
    	if(path!=null) {
	    	int ext = extensionDot(path);
	    	return ext>=0 ? path.substring(ext+1) : "";
    	}
    	return null;
    }

    /**
     * Removes the extension of the last name of the path, if any: a dotfile like ".bashrc"
     * has no extension and is returned unchanged.
     */
    public String removeExtension(String path) {
    	if(path!=null) {
	    	int ext = extensionDot(path);
	    	if(ext>=0) {
	    		return path.substring(0,ext);
	    	}
    	}
    	return path;
    }
    // The dot starting the extension of the last name, which can't be its first character
    private int extensionDot(String path) {
    	int nameStart = lastSeparator(path)+1;
    	int ext = path.lastIndexOf('.');
    	return ext>nameStart ? ext : -1;
    }

    public String setExtension(String path, String extension) {
    	return removeExtension(path) + "." + extension;
    }
	
	public String getRelativePath(String base, String file) {
		if(base!=null && file!=null) {
			// Everything is relative to an empty base
			if(base.isEmpty()) {
				return file;
			}
			// A base that is the root (or ends with a separator) has no trailing separator to skip
			if(isSeparator(base.charAt(base.length()-1))) {
				// Separator agnostic, like the other branch
				if(startsWith(file,base)) {
					return file.substring(base.length());
				}
				return null;
			}
			if(startsWith(file,base)) {
				if(file.length()>base.length()) {
					if(isSeparator(file.charAt(base.length()))) {
						return file.substring(base.length()+1);
					} else {
						return null;
					}
				} else {
					return "";
				}
			}
		}
		return null;
	}
}