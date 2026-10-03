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
package org.monflabs.galtajs.transpiler.context;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTNode.HighlightPosition;
import org.monflabs.galtajs.rt.transpiler.JSTranspilerMap;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.util.StringUtil;

/**
 * Transpiler context.
 */
public class TranspilerGeneratorMainContext extends JSTranspilerGeneratorContext {

	private JSTranspiler compiler;
	private String sourceCode;
	private JSTranspilerMap map;
	private String moduleName;
	
	private int uniqueId;

	private ConstantPool constantPool = new ConstantPool();

	private IntList linePositions;

	// The method splitting plan of each function transpiled so far (see
	// TranspilerMethodSplitter): the functions planned, and their regions
	private final java.util.Set<ASTNode> plannedFunctions = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private final java.util.List<TranspilerMethodSplitter> methodSplitters = new java.util.ArrayList<>();

	public TranspilerGeneratorMainContext(JSTranspiler compiler, String sourceCode, String moduleName) {
		this.compiler = compiler;
		this.sourceCode = sourceCode;
		this.moduleName = moduleName;
		this.map = new JSTranspilerMap();
	}
	
	/**
	 * Records the method splitting plan of a function (null when it needs none).
	 */
	public void addMethodSplitter(ASTNode function, TranspilerMethodSplitter splitter) {
		plannedFunctions.add(function);
		if(splitter!=null) {
			methodSplitters.add(splitter);
		}
	}
	/**
	 * Whether the size of a function's methods is managed by the method splitter.
	 */
	public boolean isMethodSplitPlanned(ASTNode function) {
		return function!=null && plannedFunctions.contains(function);
	}
	/**
	 * The runs of a statement list emitted as regions, or null.
	 */
	public java.util.List<TranspilerMethodSplitter.Region> getSplitRegions(ASTNode owner) {
		for(TranspilerMethodSplitter s: methodSplitters) {
			java.util.List<TranspilerMethodSplitter.Region> l = s.getRegions(owner);
			if(l!=null) {
				return l;
			}
		}
		return null;
	}
	/**
	 * The region of a statement list emitted whole as a region, or null.
	 */
	public TranspilerMethodSplitter.Region getWholeListRegion(ASTNode owner) {
		for(TranspilerMethodSplitter s: methodSplitters) {
			TranspilerMethodSplitter.Region r = s.getWholeListRegion(owner);
			if(r!=null) {
				return r;
			}
		}
		return null;
	}

	@Override
	public int generateUniqueId() {
		return uniqueId++;
	}
	
	@Override
	public String getModuleName() {
		return moduleName;
	}

	@Override
	public ConstantPool getConstantPool() {
		return constantPool;
	}

	@Override
	public JSTranspiler getTranspiler() {
		return compiler;
	}
	
	@Override
	public TranspilerGeneratorMainContext getMainContext() {
		return this;
	}
	
	public String getSourceCode() {
		return sourceCode;
	}

	@Override
	public JSTranspilerMap getTranspilerMap() {
		return map;
	}
	
	
	//
	// Extract the source code for a node
	//
    public String extractSourceCode(ASTNode node) {
        if (StringUtil.isNotEmpty(sourceCode)) {
        	int startLine = node.getBeginLine();
        	if(startLine>0) {
            	if(linePositions==null) {
            		calculateLinePositions();
            	}
	        	int start = linePositions.get(startLine-1);
	        	int startPos = start+node.getBeginCol()-1;
	        	int endPos = nextLine(startPos); // Could use endLine/endColumn?
	    		String l = sourceCode.substring(startPos,endPos);
	    		l = truncateSourceComment(l);
	    		return l;
        	}
        }
        return null;
    }
    public String extractSourceCode(HighlightPosition p) {
        if (StringUtil.isNotEmpty(sourceCode)) {
        	int startLine = p.getBeginLine();
        	if(startLine>0) {
            	if(linePositions==null) {
            		calculateLinePositions();
            	}
	        	int start = linePositions.get(startLine-1);
	        	int startPos = start+p.getBeginCol()-1;
	        	int endPos = nextLine(startPos); // Could use endLine/endColumn?
	    		String l = sourceCode.substring(startPos,endPos);
	    		l = truncateSourceComment(l);
	    		return l;
        	}
        }
        return null;
    }

    // Truncates a source-code line embedded as a debug comment above each
    // generated statement, capped at 128 chars - purely cosmetic, never
    // executable. A raw `substring(0,128)` can land mid-surrogate-pair for
    // a line containing supplementary-plane characters (e.g. a long
    // Unicode identifier), leaving an orphaned high surrogate in the
    // generated .java source text - which then fails to encode as UTF-8
    // when the test harness writes that source to disk
    // (`UnmappableCharacterException`, test262 `language/identifiers/
    // part-unicode-*.js`). Back off one character when the cut point would
    // split a pair.
    private static String truncateSourceComment(String l) {
    	if(l.length()>128) {
    		int cut = 128;
    		if(Character.isHighSurrogate(l.charAt(cut-1))) {
    			cut--;
    		}
    		l = l.substring(0,cut) + "...";
    	}
    	return l;
    }


    private void calculateLinePositions() {
    	linePositions  = new IntList();
        int codeLength = sourceCode.length();
        int pos = 0;
        while (pos < codeLength) {
        	linePositions.add(pos);
            pos = nextLine(pos);
            pos = skipEndline(pos);
        }
    }
    // Must recognize exactly the same set of line terminators as the parser's
    // own LINE_TERMINATOR token (JSParser.jj: LF | CR (LF)? | LS | PS) - U+2028
    // (line separator) and U+2029 (paragraph separator) are spec LineTerminator
    // characters too, not just \n/\r. Missing them here desyncs this class's own
    // line count from the AST nodes' getBeginLine() (computed by the parser),
    // which counts every line - an out-of-range linePositions.get() lookup
    // followed (test262 language/expressions/compound-assignment/*-whitespace.js).
    private static boolean isLineTerminator(char c) {
    	return c=='\r' || c=='\n' || c==' ' || c==' ';
    }
    private int nextLine(int pos) {
    	int length = sourceCode.length();
        while(pos<length && !isLineTerminator(sourceCode.charAt(pos))) {
            pos++;
        }
        return pos;
    }
    private int skipEndline(int pos) {
        // "\n\r" is two line terminators for the parser (only CR LF pairs up)
        if(charAt(pos)=='\n') {
            pos++;
        } else if(charAt(pos)=='\r') {
            pos++;
            if(charAt(pos)=='\n') {
                pos++;
            }
        } else if(charAt(pos)==' ' || charAt(pos)==' ') {
            pos++;
        }
        return pos;
    }
    private char charAt(int pos) {
    	return pos<sourceCode.length() ? sourceCode.charAt(pos) : '\0'; 
    }    
    private static class IntList {
        private int[] data;
        private int size;

        private IntList() {
            this.data = new int[128]; // default initial capacity
            this.size = 0;
        }

        private void add(int value) {
            if (size == data.length) {
                // grow array when full
                int[] newData = new int[data.length * 2];
                System.arraycopy(data, 0, newData, 0, data.length);
                this.data = newData;
            }
            data[size++] = value;
        }

        public int get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }
            return data[index];
        }
    }
}
