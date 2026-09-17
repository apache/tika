/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.renderer.ImageType;
import org.apache.tika.renderer.RenderSettings;

public class PagesConfigTest {

    @Test
    public void testDefaultsAreComplete() {
        PagesConfig d = PagesConfig.defaults();
        assertEquals(TextPolicy.AUTO, d.getText());
        assertEquals(300, d.getRender().getDpi());
        assertEquals(-1, d.getOcr().getMaxPages());
        assertEquals(10, d.getOcr().getAuto().getTotalCharsPerPage());
        assertEquals(List.of(InputKind.TEXT), d.getInference());
        assertFalse(d.getEmit().getEnabled());
        assertTrue(d.emitsSameImage());
    }

    @Test
    public void testLayersFoldPerField() throws Exception {
        PagesConfig deployment = new PagesConfig();
        RenderSettings render = new RenderSettings();
        render.setDpi(200);
        deployment.setRender(render);
        deployment.setText(TextPolicy.EXTRACT);

        PagesConfig parser = new PagesConfig();
        PagesConfig.Emit emit = new PagesConfig.Emit();
        emit.setEnabled(true);
        emit.setMaxPages(1);
        RenderSettings emitted = new RenderSettings();
        emitted.setImageType(ImageType.RGB);
        emit.setRender(emitted);
        parser.setEmit(emit);

        PagesConfig request = new PagesConfig();
        PagesConfig.Emit requestEmit = new PagesConfig.Emit();
        RenderSettings requestEmitted = new RenderSettings();
        requestEmitted.setMaxWidth(256);
        requestEmit.setRender(requestEmitted);
        request.setEmit(requestEmit);

        ParseContext context = new ParseContext();
        context.set(PagesConfig.class, deployment);
        PagesConfig effective = PagesConfig.resolve(context, parser, request);

        assertEquals(TextPolicy.EXTRACT, effective.getText());
        assertEquals(200, effective.getRender().getDpi());
        assertTrue(effective.getEmit().getEnabled());
        assertEquals(1, effective.getEmit().getMaxPages());
        RenderSettings out = effective.emittedRender();
        // nested overlays accumulate: the parser's imageType and the request's box both hold
        assertEquals(ImageType.RGB, out.getImageType());
        assertEquals(256, out.getMaxWidth());
        assertEquals(200, out.getDpi());
        assertFalse(effective.emitsSameImage());
        // the layers are untouched
        assertNull(parser.getText());
        assertNull(request.getRender());
        assertEquals(TextPolicy.EXTRACT, deployment.getText());
    }

    @Test
    public void testNoContextAndNoOverlaysIsDefaults() throws Exception {
        PagesConfig effective = PagesConfig.resolve(new ParseContext());
        assertEquals(TextPolicy.AUTO, effective.getText());
        assertEquals(300, effective.getRender().getDpi());
        PagesConfig withNull = PagesConfig.resolve(null, (PagesConfig) null);
        assertEquals(TextPolicy.AUTO, withNull.getText());
    }

    @Test
    public void testEmitApplies() {
        PagesConfig.Emit emit = PagesConfig.Emit.defaults();
        Metadata top = new Metadata();
        assertFalse(emit.applies(top));
        emit.setEnabled(true);
        assertTrue(emit.applies(top));
        emit.setResourceTypes(Set.of("THUMBNAIL"));
        assertFalse(emit.applies(top));
        Metadata thumbnail = new Metadata();
        thumbnail.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "THUMBNAIL");
        assertTrue(emit.applies(thumbnail));
        assertTrue(emit.withinBudget(50));
        emit.setMaxPages(1);
        assertTrue(emit.withinBudget(1));
        assertFalse(emit.withinBudget(2));
    }

    @Test
    public void testValidation() {
        PagesConfig config = new PagesConfig();
        assertThrows(IllegalArgumentException.class,
                () -> config.setInference(List.of(InputKind.IMAGES)));
        PagesConfig.Emit emit = new PagesConfig.Emit();
        assertThrows(IllegalArgumentException.class, () -> emit.setResourceTypes(Set.of("THUMB")));
        assertThrows(IllegalArgumentException.class, () -> emit.setMaxPages(0));
        PagesConfig.Ocr ocr = new PagesConfig.Ocr();
        assertThrows(IllegalArgumentException.class, () -> ocr.setMaxPages(-3));
    }
}
