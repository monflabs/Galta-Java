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
package org.monflabs.playground;

import java.util.List;

import org.monflabs.util.PathUtil;

public abstract class PlaygroundLayout {

	public static enum WINDOW {
		NONE,
		MAIN,
		SECONDARY,
		DOC,
	}
	
	public WINDOW getWindow(String name) {
		if(name.endsWith(".md")) {
			return WINDOW.DOC;
		}
		return WINDOW.MAIN;
	}
	public void sortTabs(WINDOW window, List<String> tabs) {
		sortTabs(window,tabs,null);
	}

	protected void sortTabs(WINDOW window, List<String> tabs, String first) {
		String mainExt = first!=null ? PathUtil.POSIX.getFileExtension(first) : null;
		tabs.sort( (f1,f2) -> {
			if(first!=null) {
				if(f1.equals(first)) {
					return -1;
				}
				if(f2.equals(first)) {
					return 1;
				}
			}
			if(mainExt!=null) {
				String e1 = PathUtil.POSIX.getFileExtension(f1);
				String e2 = PathUtil.POSIX.getFileExtension(f2);
				if(!e1.equals(e2)) {
					if(e1.equals(mainExt)) {
						return -1;
					}
					if(e2.equals(mainExt)) {
						return 1;
					}
				}
			}
			return f1.compareTo(f2);
		} );
	}

//	public List<String> sortTabs(WINDOW window, List<String> tabs) {
//		return sortByName(tabs,null);
//	}
//	
//	public static List<String> sortByName(List<String> tabs, List<String> names) {
//		tabs.sort( (t1,t2) -> {
//			int p1 = findIndex(names,t1);
//			int p2 = findIndex(names,t2);
//			if(p1==p2) {
//				return t1.compareTo(t2);
//			}
//			return p1-p2;
//		});
//		return tabs;
//	}
//	public static List<String> sortByExtension(List<String> tabs, List<String> extensions) {
//		tabs.sort( (t1,t2) -> {
//			int p1 = findIndex(extensions,PathUtil.STD.getFileExtension(t1));
//			int p2 = findIndex(extensions,PathUtil.STD.getFileExtension(t2));
//			if(p1==p2) {
//				return t1.compareTo(t2);
//			}
//			return p1-p2;
//		});
//		return tabs;
//	}
//	
//	private static int findIndex(List<String> array, String name) {
//		if(array!=null && array.size()==0) {
//			String ext = PathUtil.STD.getFileExtension(name);
//			if(ext.length()>0) {
//				for(int i=0; i<array.size(); i++) {
//					if(ext.equals(array.get(i))) {
//						return i;
//					}
//				}
//			}
//		}
//		return Integer.MAX_VALUE;
//	}
}
