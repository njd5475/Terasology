/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.opengl.fbms;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.engine.SimpleUri;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.opengl.AbstractFBOsManager;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;

/**
 * TODO: Add javadocs
 * TODO: Better naming
 */
public class ShadowMapResolutionDependentFBOs extends AbstractFBOsManager implements PropertyChangeListener {
    private Config config = CoreRegistry.get(Config.class);
    private RenderingConfig renderingConfig = config.getRendering();
    private FBO.Dimensions shadowMapResolution;

    public ShadowMapResolutionDependentFBOs() {
        renderingConfig.subscribe(RenderingConfig.SHADOW_MAP_RESOLUTION, this);
        int resolution = renderingConfig.getShadowMapResolution();
        shadowMapResolution = new FBO.Dimensions(resolution, resolution);
    }

    @Override
    public FBO request(FBOConfig fboConfig) {
        FBO fbo;
        SimpleUri fboName = fboConfig.getName();
        if (fboConfigs.containsKey(fboName)) {
            if (!fboConfig.equals(fboConfigs.get(fboName))) {
                throw new IllegalArgumentException("Requested FBO is already available with different configuration");
            }
            fbo = fboLookup.get(fboConfig.getName());
        } else {
            fbo = generateWithDimensions(fboConfig, shadowMapResolution);
        }
        retain(fboName);
        return fbo;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (renderingConfig.isDynamicShadows()) {
            int shadowMapResFromSettings = (int) evt.getNewValue();
            shadowMapResolution = new FBO.Dimensions(shadowMapResFromSettings, shadowMapResFromSettings);

            for (Map.Entry<SimpleUri, FBOConfig> entry : fboConfigs.entrySet()) {
                SimpleUri fboName = entry.getKey();
                FBOConfig fboConfig = entry.getValue();

                if (fboLookup.containsKey(fboName)) {
                    FBO fbo = fboLookup.get(fboName);
                    if (fbo != null) { // TODO: validate if necessary
                        fbo.dispose();
                    }
                }
                FBO shadowMapResDependentFBO = generateWithDimensions(fboConfig, shadowMapResolution);
                if (shadowMapResDependentFBO.getStatus() == FBO.Status.DISPOSED) {
                    logger.warn("Failed to generate ShadowMap FBO. Turning off shadows.");
                    renderingConfig.setDynamicShadows(false);
                    break;
                }

                fboLookup.put(fboName, shadowMapResDependentFBO);
            }
        }
    }
}
