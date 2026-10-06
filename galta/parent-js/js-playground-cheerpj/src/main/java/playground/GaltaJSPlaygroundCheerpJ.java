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
package playground;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.monflabs.json.config.CustomJsonConfig;
import org.monflabs.ui.swing.ide.IDEApplication;
import org.monflabs.util.config.Config;

import com.monflabs.playground.swing.PlaygroundFrame;

import playground.impl.GaltaJSPlaygroundFrame;

/**
 *
 */
public class GaltaJSPlaygroundCheerpJ extends GaltaJSPlayground{

    public static void main(String[] args) {
    	SwingUtilities.invokeLater( () -> {
    		IDEApplication.newBuilder()
				.config(themeConfig(args))
				.build();
    		
			configure();
			
        	PlaygroundFrame f = new GaltaJSPlaygroundFrame();
        	f.setUndecorated(true);
            f.setExtendedState(JFrame.MAXIMIZED_BOTH);
            f.setVisible(true);
    	});
    }

    /**
     * The configuration for a --theme=dark or --theme=light argument (the mode of the
     * documentation site launching the playground), or null to follow the system.
     */
    static Config themeConfig(String[] args) {
    	for(String a: args) {
    		if(a.equals("--theme=dark") || a.equals("--theme=light")) {
    			byte[] json = ("{\"ui\":{\"dark\":"+a.endsWith("dark")+"}}").getBytes(StandardCharsets.UTF_8);
    			return CustomJsonConfig.newBuilder()
    					.resourceReader(path -> path==null ? new ByteArrayInputStream(json) : null)
    					.build();
    		}
    	}
    	return null;
    }
}
