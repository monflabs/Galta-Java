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
package org.monflabs.util.io;

public abstract class LRUCharBuffer {

	public static final int MAX_BUFFER = 20_000;
	public static final int OFFSET     =  2_000;

	protected int bufferCapacity;
	protected int offset;
	
	protected LRUCharBuffer(int bufferCapacity, int offset) {
		this.bufferCapacity = bufferCapacity;
		this.offset = offset;
	}
	
	@Override
	public String toString() {
		return getBuffer();
	}

	public void write(String s) {
		write(s.toCharArray());
	}
	public void write(char[] chars) {
		write(chars, 0, chars.length);
	}
	public synchronized void write(char[] chars, int pos, int len) {
		int size = bufferSize();
		
		int available = bufferCapacity-size;
		if(len<=available) {
			update(0, chars, pos, len);
			return;
		}
		
		int overflow = len - (bufferCapacity-offset); 
		if(overflow>0) {
			pos += overflow;
			len -= overflow;
		}

		int delete = size - (bufferCapacity - (len+offset));
		update(delete, chars, pos, len);
	}
	
	public int capacity() {
		return bufferCapacity;
	}
	public int capacityOffset() {
		return offset;
	}
	public abstract int bufferSize();
	protected abstract void update(int delete, char[] chars, int pos, int len);

	protected abstract String getBuffer();

	public static class MemoryCharBuffer extends LRUCharBuffer {
		private char[] buffer;
		private int ptr;
		
		public MemoryCharBuffer() {
			this(MAX_BUFFER, OFFSET);
		}

		public MemoryCharBuffer(int bufferSize, int offset) {
			super(bufferSize,offset);
			buffer = new char[bufferSize];
		}

		@Override
		public int bufferSize() {
			return ptr;
		}
		
		@Override
		protected synchronized String getBuffer() {
			return new String(buffer,0,ptr);
		}

	    @Override
		protected void update(int delete, char[] chars, int pos, int len) {
	    	if(delete>0) {
	    		System.arraycopy( buffer, delete, buffer, 0, ptr-delete);
	    		ptr -= delete;
	    	}
	    	if(len>0) {
	    		System.arraycopy( chars, pos, buffer, ptr, len);
	    		ptr += len;
	    	}
	    }
	}
}
