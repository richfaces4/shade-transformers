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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.assembly.filter.ContainerDescriptorHandler;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;

/**
 * @author Nick Belaevski
 */
public abstract class BaseResourceTransformer implements ContainerDescriptorHandler {

    protected static final String META_INF_PATH = "META-INF/";

    private boolean hasProcessedFiles;

    protected void resetTransformer() {
        hasProcessedFiles = false;
    }

    protected abstract boolean isHandled(FileInfo fileInfo);

    @Override
    public final boolean isSelected(final FileInfo fileInfo) throws IOException {
        if (isHandled(fileInfo)) {
            hasProcessedFiles = true;

            InputStream is = fileInfo.getContents();
            try {
                processFile(fileInfo);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    // TODO: handle exception
                }
            }

            return false;
        } else {
            return true;
        }
    }

    protected abstract void processFile(FileInfo fileInfo);

    @Override
    public final void finalizeArchiveCreation(final Archiver archiver) throws ArchiverException {
        for (final ResourceIterator it = archiver.getResources(); it.hasNext();) {
            it.next();
        }

        if (hasProcessedFiles) {
            try {
                writeMergedConfigFiles(archiver);
            } finally {
                resetTransformer();
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List getVirtualFiles() {
        if (hasProcessedFiles) {
            return Collections.singletonList(getMergedFileName());
        }

        return Collections.emptyList();
    }

    protected abstract String getMergedFileName();

    protected abstract void writeMergedConfigFiles(Archiver archiver) throws ArchiverException;

    @Override
    public final void finalizeArchiveExtraction(final UnArchiver unarchiver) throws ArchiverException {
    }

    protected boolean hasProcessedConfigFiles() {
        return hasProcessedFiles;
    }
}
