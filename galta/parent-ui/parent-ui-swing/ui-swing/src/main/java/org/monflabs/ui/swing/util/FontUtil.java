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
package org.monflabs.ui.swing.util;

import java.awt.Font;

import javax.swing.plaf.FontUIResource;

/**
 * Some Font utilities
 * 
 * @author priand
 */
public class FontUtil {
	
	/**
	 * The bold version of a font: same family, size (fractional sizes
	 * included) and attributes. A {@link FontUIResource} stays one, so the
	 * look and feel still treats it as its own.
	 */
	public static Font boldify(Font font) {
		if(!font.isBold()) {
			Font bold = font.deriveFont(font.getStyle() | Font.BOLD, font.getSize2D());
			return font instanceof FontUIResource ? new FontUIResource(bold) : bold;
		}
	    return font;
    }
}
