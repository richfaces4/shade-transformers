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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.assembly.filter.ContainerDescriptorHandler;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.util.WriterFactory;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Nick Belaevski
 */
public abstract class BaseFacesResourceTransformer implements ContainerDescriptorHandler {

    protected static final String META_INF_PATH = "META-INF/";

    protected static final String JAVAEE_PREFIX = "javaee";

    protected static final String JAVAEE_URI = "http://java.sun.com/xml/ns/javaee";

    private static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";

    private static final String XSI_PREFIX = "xsi";

    protected NamespacesTracker namespacesFactory = new NamespacesTracker();

    private boolean excludeOverride = false;

    private boolean hasProcessedConfigFiles;
    
    private String outputDirectory;

    protected static XPath createXPath(final String path) throws JDOMException {
        XPath xPath = XPath.newInstance(path);
        xPath.addNamespace(Namespace.getNamespace(JAVAEE_PREFIX, JAVAEE_URI));
        xPath.addNamespace(Namespace.getNamespace(XSI_PREFIX, XSI_URI));

        return xPath;
    }

    protected Namespace getJavaEENamespace() {
        return namespacesFactory.getNamespace(JAVAEE_URI, null);
    }

    protected void addSchemaLocation(final Element element, final String schemaLocation) {
        if (schemaLocation != null && schemaLocation.length() != 0) {
            Namespace xsiNamespace = namespacesFactory.getNamespace(XSI_URI, XSI_PREFIX);
            element.setAttribute("schemaLocation", JAVAEE_URI + " " + schemaLocation, xsiNamespace);
        }
    }

    private void updateNamespaceRecursively(final Object object) {
        if (object instanceof Element) {
            Element element = (Element) object;

            element.setNamespace(namespacesFactory.getNamespace(element.getNamespace()));

            for (Object attributeObject : element.getAttributes()) {
                Attribute attribute = (Attribute) attributeObject;

                if (!Namespace.NO_NAMESPACE.equals(attribute.getNamespace())) {
                    attribute.setNamespace(namespacesFactory.getNamespace(attribute.getNamespace()));
                }
            }

            for (Object child : element.getChildren()) {
                updateNamespaceRecursively(child);
            }
        }
    }

    protected boolean isJavaEEOrDefaultNamespace(final Element element) {
        String namespaceURI = element.getNamespaceURI();
        if (namespaceURI == null || namespaceURI.trim().length() == 0) {
            return true;
        }

        return JAVAEE_URI.equals(namespaceURI);
    }

    protected Element cloneAndImportElement(final Element element) {
        Element clonedElement = (Element) element.clone();
        updateNamespaceRecursively(clonedElement);
        return clonedElement;
    }

    protected List<Element> cloneAndImportElements(final List<Element> elements) {
        List<Element> result = new ArrayList<Element>(elements.size());
        for (Element element : elements) {
            result.add(cloneAndImportElement(element));
        }

        return result;
    }

    protected void addToArchive(final String path, final Document document, final Archiver archiver) throws ArchiverException {
        File f;
        try {
            f = File.createTempFile("richfaces-assembly-transform.", ".tmp");
            f.deleteOnExit();

            final Writer fileWriter = WriterFactory.newXmlWriter(f);

            Format prettyFormat = Format.getPrettyFormat();
            prettyFormat.setIndent("    ");

            Element rootElement = document.getRootElement();
            Collection<Namespace> namespaces = namespacesFactory.getNamespaces();
            for (Namespace namespace : namespaces) {
                if (namespace.getPrefix().length() == 0) {
                    continue;
                }
                rootElement.addNamespaceDeclaration(namespace);
            }
            outputFilesToSeparateDir(document, path, prettyFormat);
            new XMLOutputter(prettyFormat).output(document, fileWriter);
        } catch (IOException e) {
            throw new ArchiverException("Error adding '" + path + "' to archive. Reason: " + e.getMessage(), e);
        }

        excludeOverride = true;
        archiver.addFile(f, path);
        excludeOverride = false;
    }
    
    public String getOutputDirectory() {
        return outputDirectory;
    }
    
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    protected void outputFilesToSeparateDir(final Document document, final String resourceName, final Format format)
            throws IOException {
        if (outputDirectory == null) {
            throw new NullPointerException("outputDirectory can't be null");
        }
        File outputDir = new File(outputDirectory).getAbsoluteFile();
        File metaInfDir = new File(outputDir, META_INF_PATH);
        metaInfDir.mkdirs();
        File outputFile = new File(outputDir, resourceName);
        FileOutputStream outFiles = new FileOutputStream(outputFile);
        try {
            new XMLOutputter(format).output(document, outFiles);
        } finally {
            outFiles.close();
        }

    }

    protected abstract void processDocument(String resource, Document document) throws JDOMException;

    protected void resetTransformer() {
        namespacesFactory = new NamespacesTracker();
        hasProcessedConfigFiles = false;
    }

    protected String getMetaInfResourceName(final String resource) {
        if (!resource.startsWith(META_INF_PATH)) {
            return null;
        }

        String subPath = resource.substring(META_INF_PATH.length());
        if (subPath.contains("/")) {
            return null;
        }

        return subPath;
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> checkedList(final List<?> list, final Class<T> clazz) {
        for (Object o : list) {
            if (!clazz.isInstance(o)) {
                throw new ClassCastException(o.toString());
            }
        }

        return (List<T>) list;
    }

    protected abstract boolean isHandled(FileInfo fileInfo);

    @Override
    public final boolean isSelected(final FileInfo fileInfo) throws IOException {
        if (isHandled(fileInfo)) {
            if (excludeOverride) {
                return true;
            }

            hasProcessedConfigFiles = true;
            InputStream is = fileInfo.getContents();
            try {
                SAXBuilder builder = new SAXBuilder(false);
                builder.setExpandEntities(false);
                // TODO nick - namespace aware?
                builder.setEntityResolver(new EntityResolver() {

                    @Override
                    public InputSource resolveEntity(final String publicId, final String systemId) throws SAXException,
                            IOException {
                        return new InputSource(new StringReader(""));
                    }
                });
                Document document = builder.build(is);
                processDocument(fileInfo.getName(), document);
            } catch (JDOMException e) {
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

    @Override
    public final void finalizeArchiveCreation(final Archiver archiver) throws ArchiverException {
        for (final ResourceIterator it = archiver.getResources(); it.hasNext();) {
            it.next();
        }

        if (hasProcessedConfigFiles) {
            try {
                writeMergedConfigFiles(archiver);
            } finally {
                resetTransformer();
            }
        }
    }

    protected abstract void writeMergedConfigFiles(Archiver archiver) throws ArchiverException;

    @Override
    public final void finalizeArchiveExtraction(final UnArchiver unarchiver) throws ArchiverException {
        // NOP.
    }

    protected boolean hasProcessedConfigFiles() {
        return hasProcessedConfigFiles;
    }

}
