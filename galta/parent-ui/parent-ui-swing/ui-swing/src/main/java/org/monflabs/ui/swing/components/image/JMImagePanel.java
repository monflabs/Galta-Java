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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.monflabs.util.Console;



@SuppressWarnings("serial")
public class JMImagePanel extends JPanel {
	
    public static enum Style {
        CENTERED, TILED, SCALED, SCALED_KEEP_ASPECT_RATIO
    }

    private Style style = Style.CENTERED;
    private Image image;
    private String imageResource;
    private Dimension dimension;

    public JMImagePanel() {
    }

    public JMImagePanel(URL imageUrl) {
        try {
            setImage(ImageIO.read(imageUrl));
        } catch (Exception e) {
        	Console.log(e);
        }
    }
    
    public Dimension getDimension() {
    	return dimension;
    }
    public void setDimension(Dimension dimension) {
    	this.dimension = dimension;
    }

    public Image getImage() {
        return image;
    }
    public void setImage(Image image) {
        this.image = image;
        invalidate();
        repaint();
    }

    public String getImageResource() {
        return imageResource;
    }
    public void setImageResource(String imageResource) {
        this.imageResource = imageResource;
        URL res = getClass().getResource(imageResource);
        if(res!=null) {
        	setImage(res);
        } else {
        	setImage((Image)null);
        }
    }

    public void setImage(URL imageUrl) {
    	try {
    		setImage(ImageIO.read(imageUrl));
    	} catch(IOException ex) {
    		Console.log(ex);
    	}
    }
    public void setImage(File file) {
    	try {
        	setImage(file.toURI().toURL());
    	} catch(IOException ex) {
    		Console.log(ex);
    	}
    }
    

    public Style getStyle() {
        return style;
    }
    public void setStyle(Style s) {
        if (style != s) {
            Style oldStyle = style;
            style = s;
            firePropertyChange("style", oldStyle, s);
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
    	if(dimension!=null) {
    		return dimension;
    	}
        if (!isPreferredSizeSet() && image!=null) {
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return super.getPreferredSize();
            }
            Insets insets = getInsets();
            width += insets.left + insets.right;
            height += insets.top + insets.bottom;
            return new Dimension(width, height);
        }
        return super.getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        if (image != null) {
            final int imgWidth = image.getWidth(null);
            final int imgHeight = image.getHeight(null);
            if (imgWidth == -1 || imgHeight == -1) {
                return;
            }

            Insets insets = getInsets();
            final int pw = getWidth() - insets.left - insets.right;
            final int ph = getHeight() - insets.top - insets.bottom;

            switch (style) {
            case CENTERED:
                Rectangle clipRect = g2.getClipBounds();
                int imageX = (pw - imgWidth) / 2 + insets.left;
                int imageY = (ph - imgHeight) / 2 + insets.top;
                Rectangle r = SwingUtilities.computeIntersection(imageX, imageY, imgWidth, imgHeight, clipRect);
                if (r.x == 0 && r.y == 0 && (r.width == 0 || r.height == 0)) {
                    return;
                }
                // I have my new clipping rectangle "r" in clipRect space.
                // It is therefore the new clipRect.
                clipRect = r;
                // since I have the intersection, all I need to do is adjust the
                // x & y values for the image
                int txClipX = clipRect.x - imageX;
                int txClipY = clipRect.y - imageY;
                int txClipW = clipRect.width;
                int txClipH = clipRect.height;

                g2.drawImage(image, clipRect.x, clipRect.y, clipRect.x + clipRect.width, clipRect.y + clipRect.height, txClipX, txClipY, txClipX + txClipW, txClipY + txClipH, null);
                break;
            case TILED:
                g2.translate(insets.left, insets.top);
                Rectangle clip = g2.getClipBounds();
                g2.setClip(0, 0, pw, ph);

                int totalH = 0;

                while (totalH < ph) {
                    int totalW = 0;

                    while (totalW < pw) {
                        g2.drawImage(image, totalW, totalH, null);
                        totalW += image.getWidth(null);
                    }

                    totalH += image.getHeight(null);
                }

                g2.setClip(clip);
                g2.translate(-insets.left, -insets.top);
                break;
            case SCALED:
                g2.drawImage(image, insets.left, insets.top, pw, ph, null);
                break;
            case SCALED_KEEP_ASPECT_RATIO:
                int w = pw;
                int h = ph;
                final float ratioW = ((float) w) / ((float) imgWidth);
                final float ratioH = ((float) h) / ((float) imgHeight);

                if (ratioW < ratioH) {
                    h = (int) (imgHeight * ratioW);
                } else {
                    w = (int) (imgWidth * ratioH);
                }

                final int x = (pw - w) / 2 + insets.left;
                final int y = (ph - h) / 2 + insets.top;
                g2.drawImage(image, x, y, w, h, null);
                break;
            default:
                g2.drawImage(image, insets.left, insets.top, this);
                break;
            }
        }
    }
}