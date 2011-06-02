/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.build.shade.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugins.shade.Shader;
import org.apache.maven.plugins.shade.filter.Filter;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.PlexusTestCase;

public class ShadeMojoTest extends PlexusTestCase {

    private Shader shader;

    public void setUp() throws Exception {
        super.setUp();
        shader = (Shader) lookup(Shader.ROLE);
    }

    public void tearDown() throws Exception {
        super.tearDown();
        shader = null;
    }

    private void doShade(File targetFile) throws Exception {
        List<Filter> filters = new ArrayList<Filter>();

        Set<File> set = new LinkedHashSet<File>();

        File basedir = new File(getBasedir());

        set.add(new File(basedir, "src/test/jars/componentcontrol-ui-4.0.0-SNAPSHOT.jar"));
        set.add(new File(basedir, "src/test/jars/functions-ui-4.0.0-SNAPSHOT.jar"));
        set.add(new File(basedir, "src/test/jars/richfaces-ui-core-ui-4.0.0-SNAPSHOT.jar"));

        List<Relocator> relocators = new ArrayList<Relocator>();
        relocators.add(new SimpleRelocator("/", null, Collections.emptyList()));

        List<ResourceTransformer> resourceTransformers = new ArrayList<ResourceTransformer>();

        resourceTransformers.add(new TaglibXmlResourceTransformer());
        resourceTransformers.add(new FacesConfigXmlResourceTransformer());

        shader.shade(set, targetFile, filters, relocators, resourceTransformers);
    }

    public void testShading() throws Exception {
        doShade(new File("target/ui-shaded.jar"));
        // TODO nick - check shaded .jar
    }
}
