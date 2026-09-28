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
package org.monflabs.ui.swing.components.image;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.function.Consumer;

import javax.swing.Icon;

/**
 */
public class ImageUtil {
	
	public static enum HAlign {
    	LEFT,
    	CENTER,
    	RIGHT,
    	MOSAIC,
    	STRETCH
	}

	public static enum VAlign {
    	TOP,
    	CENTER,
    	BOTTOM,
    	MOSAIC,
    	STRETCH
	}
    
    public static final void drawIcon(Component c, Graphics g, HAlign hAlign, VAlign vAlign, Icon icon, Rectangle bounds, Color fillColor) {
        if( icon!=null ) {
            int iconWidth  = icon.getIconWidth();
            int iconHeight = icon.getIconHeight();
            draw(g,hAlign,vAlign,(rect) -> {icon.paintIcon( c, g, rect.x, rect.y );}, iconWidth, iconHeight, bounds, fillColor );
        }
    }

    private static final void draw(Graphics g, HAlign hAlign, VAlign vAlign, Consumer<Rectangle> painter, int imageWidth, int imageHeight, Rectangle bounds, Color fillColor) {
        // And draw it
        if( painter!=null ) {
            // Compute the icon position
            int dstX=bounds.x, dstY=bounds.y,
                dstW=imageWidth,
                dstH=imageHeight;
            int xRepeat=1, yRepeat=1;
            switch( hAlign ) {
                case LEFT -> {
                }
                case CENTER -> {
                    dstX = bounds.x + (bounds.width-imageWidth) /2;
                }
                case RIGHT -> {
                    dstX = bounds.x + bounds.width - imageWidth;
                }
                case MOSAIC -> {
                    xRepeat = (bounds.width+imageWidth-1)/imageWidth;
                }
                case STRETCH -> {
                    dstW = bounds.width;
                }
            }
            switch( vAlign ) {
                case TOP -> {
                }
                case CENTER -> {
                    dstY= bounds.y+ (bounds.height-imageHeight) /2;
                }
                case BOTTOM -> {
                    dstY = bounds.y + bounds.height - imageHeight;
                }
                case MOSAIC -> {
                    yRepeat = (bounds.height+imageHeight-1)/imageHeight;
                }
                case STRETCH -> {
                	dstH = bounds.height;
                }
            }

            // Compute the rectangle to be drawn
            int firstX = dstX, firstY = dstY;
            int lastX  = dstX+xRepeat*dstW, lastY = dstY+yRepeat*dstH;

            // And draw it
            for( int xr=0; xr<xRepeat; xr++ ) {
                int oldDstY = dstY;
                for( int yr=0; yr<yRepeat; yr++ ) {
                    painter.accept( new Rectangle(dstX, dstY, dstW, dstH) );
                    dstY += dstH;
                }
                dstY = oldDstY;
                dstX += dstW;
            }

            // Fill the parts that are not drawn
            if( fillColor!=null ) {
                Color oldColor = g.getColor(); g.setColor(fillColor);

                // Left
                if( firstX>bounds.x ) {
                    g.fillRect( bounds.x, bounds.y, firstX-bounds.x, bounds.height );
                }

                // Right
                if( lastX<bounds.x+bounds.width ) {
                    g.fillRect( lastX, bounds.y, bounds.x+bounds.width-lastX, bounds.height );
                }

                // Top
                if( firstY>bounds.y ) {
                    g.fillRect( bounds.x, bounds.y, bounds.width, firstY-bounds.y );
                }

                // Bottom
                if( lastY<bounds.y+bounds.height ) {
                    g.fillRect( bounds.x, lastY, bounds.width, bounds.y+bounds.height-lastY );
                }

                g.setColor(oldColor);
            }
        }
    }
}

