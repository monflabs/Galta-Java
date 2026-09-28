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
package org.monflabs.galtajs.rt.transpiler;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.transpiler.JSTranspilerMapException;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.util.IntList;
import org.monflabs.util.StringMatcher;

/**
 */
public class JSTranspilerMap {

	public static class Block {
		
		private Block parent;
		private List<Block> children = new ArrayList<>();
		private IntList javaLines = new IntList();
		private IntList scriptLines = new IntList();
		private int startLine;
		private int endLine;
		
		private Block(Block parent) {
			this.parent = parent;
		}

		public int getStartLine() {
			return startLine;
		}

		public int getEndLine() {
			return endLine;
		}

		public List<Block> getChildren() {
			return children;
		}

		public IntList getJavaLines() {
			return javaLines;
		}

		public IntList getScriptLines() {
			return scriptLines;
		}
		
		public void add(TranspilerJavaBuilder b, ASTNode node) {
			javaLines.add(b.getCurrentLine());
			scriptLines.add(node.getBeginLine());
		}
	}
	
	private Block mainBlock;
	private Block currentBlock;
	
	public JSTranspilerMap() {
	}

 	private JSTranspilerMap(Block mainBlock) {
		this.mainBlock = mainBlock;
	}

 	
 	//
 	// Runtime
 	//
 	
	public Block getMainBlock() {
		return mainBlock;
	}
	
	public int findScriptLine(int javaLine) {
		return findScriptLine(mainBlock, javaLine);
	}
	private static int findScriptLine(Block b, int javaLine) {
		for(Block child: b.children) {
			int line = findScriptLine(child, javaLine);
			if(line>0) {
				return line;
			}
		}
		if(javaLine>=b.startLine && javaLine<b.endLine) {
			// Should use a dichotomy search here...
			int sz = b.javaLines.size();
			if(sz==0) {
				return -1;
			}
			for(int i=0; i<sz; i++) {
				if(b.javaLines.get(i)>javaLine) {
					// A Java line before the block's first mapped line (its
					// prologue) belongs to the first script line
					return b.scriptLines.get(Math.max(0,i-1));
				}
			}
			return b.scriptLines.get(b.scriptLines.size()-1);
		}
		return -1;
	}

	//
	// Transpile time
	//
	public Block getCurrentBlock() {
		return currentBlock;
	}
	public Block pushBlock(int startLine) {
		Block b = new Block(currentBlock);
		b.startLine = startLine;
		if(currentBlock==null) {
			mainBlock = b;
		} else {
			currentBlock.children.add(b);
		}
		return currentBlock = b;
	}
	
	public void popBlock(int endLine) {
		if(currentBlock==null) {
			throw new IllegalStateException();
		}
		currentBlock.endLine = endLine;
		currentBlock = currentBlock.parent;
	}
	
	public String serialize() {
		StringBuilder b = new StringBuilder();
		serialize(b,mainBlock);
		return b.toString();
	}
	private void serialize(StringBuilder b, Block block) {
		b.append('{');
		b.append(block.startLine); 
		b.append(',');
		b.append(block.endLine); 
		b.append('[');
		for(int i=0; i<block.javaLines.size(); i++) {
			if(i>0) {
				b.append(',');
			}
			b.append(block.javaLines.get(i));
		}
		b.append(']');
		b.append('[');
		for(int i=0; i<block.scriptLines.size(); i++) {
			if(i>0) {
				b.append(',');
			}
			b.append(block.scriptLines.get(i));
		}
		b.append(']');
		if(!block.children.isEmpty()) {
			for(Block child: block.children) {
				serialize(b,child);
			}
		}
		b.append('}');
	}
	
	public static JSTranspilerMap deserialize(String map) {
		MapParser m = new MapParser(map);
		return m.parse();
	}
	
	private static class MapParser extends StringMatcher {
		MapParser(String map) {
			super(map,0);
		}
		JSTranspilerMap parse() {
			Block b = parseBlock(null);
			return new JSTranspilerMap(b);
		}
		Block parseBlock(Block parent) {
			if(!match('{')) {
				throw new JSTranspilerMapException("Missing block start");
			}
			Block b = new Block(parent);
			b.startLine = readInteger();

			if(!match(',')) {
				throw new JSTranspilerMapException("Missing block end line");
			}
			b.endLine = readInteger();
			
			if(!match('[')) {
				throw new JSTranspilerMapException("Missing java line array start");
			}
			while(!startsWith(']')) {
				int line = readInteger();
				b.javaLines.add(line);
				skipIf(',');
			}
			if(!match(']')) {
				throw new JSTranspilerMapException("Missing script line array end");
			}
			if(!match('[')) {
				throw new JSTranspilerMapException("Missing script line array start");
			}
			while(!startsWith(']')) {
				int line = readInteger();
				b.scriptLines.add(line);
				skipIf(',');
			}
			if(!match(']')) {
				throw new JSTranspilerMapException("Missing script line array end");
			}

			while(startsWith('{')) {
				Block child = parseBlock(b);
				b.children.add(child);
			}
			if(!match('}')) {
				throw new JSTranspilerMapException("Missing script line array start");
			}
			
			return b;
		}
	}	
}

	