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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.jdom.filter.Filter;
import org.jdom.xpath.XPath;

/**
 * @author Nick Belaevski
 */
public class FacesConfigXmlResourceTransformer
    extends BaseFacesResourceTransformer
{

    private static final String BEHAVIOR_ID = "behavior-id";

    private static final String VALIDATOR_ID = "validator-id";

    private static final String CONVERTER_ID = "converter-id";

    private static final String COMPONENT_TYPE = "component-type";

    private static final String RENDER_KIT_EXTENSION = "render-kit-extension";

    private static final String CLIENT_BEHAVIOR_RENDERER = "client-behavior-renderer";

    private static final String ICON = "icon";

    private static final String DISPLAY_NAME = "display-name";

    private static final String DESCRIPTION = "description";

    private static final String LIFECYCLE_EXTENSION = "lifecycle-extension";

    private static final String PHASE_LISTENER = "phase-listener";

    private static final String FACES_CONFIG_EXTENSION = "faces-config-extension";

    private static final String BEHAVIOR = "behavior";

    private static final String VALIDATOR = "validator";

    private static final String LIFECYCLE = "lifecycle";

    private static final String REFERENCED_BEAN = "referenced-bean";

    private static final String NAVIGATION_RULE = "navigation-rule";

    private static final String NAME = "name";

    private static final String MANAGED_BEAN = "managed-bean";

    private static final String CONVERTER = "converter";

    private static final String COMPONENT = "component";

    private static final String FACTORY = "factory";

    private static final String ABSOLUTE_ORDERING = "absolute-ordering";

    private static final String ORDERING = "ordering";

    private static final String APPLICATION = "application";

    private static final String RENDERER = "renderer";

    private static final String RENDER_KIT_CLASS = "render-kit-class";

    private static final String RENDER_KIT_ID = "render-kit-id";

    private static final String RENDER_KIT = "render-kit";

    private static final String FACES_CONFIG = "faces-config";

    private static final String METADATA_COMPLETE = "metadata-complete";

    private static final String CURRENT_VERSION = "2.0";

    private static final String VERSION = "version";

    private static final String FACES_CONFIG_FILE_NAME = "faces-config.xml";

    private static final String DOT_FACES_CONFIG_FILE_NAME = ".faces-config.xml";

    private static final Set<String> AGGREGATOR_ELEMENTS_NAME_SET = new HashSet<String>( Arrays.asList( APPLICATION,
                                                                                                        FACTORY,
                                                                                                        LIFECYCLE ) );

    private static final Set<String> UNHANDLED_ELEMENTS_NAME_SET =
        new HashSet<String>( Arrays.asList( ORDERING, ABSOLUTE_ORDERING ) );

    private static final String RENDER_KIT_ID_EXPRESSION = MessageFormat.format( "./{0}:{1}|./{1}",
                                                                                 JAVAEE_PREFIX,
                                                                                 RENDER_KIT_ID );

    private static final String FACES_CONFIG_FILE_PATH = META_INF_PATH + FACES_CONFIG_FILE_NAME;

    private enum ThreeState
    {
        UNDEFINED, FALSE, TRUE
    }

    private final Map<String, List<Element>> aggregatorElements = new HashMap<String, List<Element>>();

    private final Map<String, List<Element>> renderkitElements = new HashMap<String, List<Element>>();

    private final List<Element> simpleElements = new ArrayList<Element>();

    private String configName = null;

    private boolean hasProcessedConfigFiles;

    private ThreeState metadataComplete = ThreeState.UNDEFINED;

    private Comparator<Element> createElementsComparator()
        throws JDOMException
    {
        List<String> elements =
            Arrays.asList( APPLICATION,
                           ORDERING,
                           ABSOLUTE_ORDERING,
                           FACTORY,
                           COMPONENT,
                           CONVERTER,
                           MANAGED_BEAN,
                           NAME,
                           NAVIGATION_RULE,
                           REFERENCED_BEAN,
                           RENDER_KIT,
                           LIFECYCLE,
                           VALIDATOR,
                           BEHAVIOR,
                           FACES_CONFIG_EXTENSION,
                           /* lifecycle inners */PHASE_LISTENER,
                           LIFECYCLE_EXTENSION, /* lifecycle inners end */
                           /* render-kit inners */DESCRIPTION,
                           DISPLAY_NAME,
                           ICON,
                           RENDER_KIT_ID,
                           RENDER_KIT_CLASS,
                           RENDERER,
                           CLIENT_BEHAVIOR_RENDERER,
                           RENDER_KIT_EXTENSION /* render-kit inners end */);

        Map<String, XPath> elementNameExpressions = new HashMap<String, XPath>();

        String componentTypeExpr = MessageFormat.format( "./{0}:{1}|./{1}", JAVAEE_PREFIX, COMPONENT_TYPE );
        elementNameExpressions.put( COMPONENT, createXPath( componentTypeExpr ) );

        String converterIdExpr = MessageFormat.format( "./{0}:{1}|./{1}", JAVAEE_PREFIX, CONVERTER_ID );
        elementNameExpressions.put( CONVERTER, createXPath( converterIdExpr ) );

        String validatorIdExpr = MessageFormat.format( "./{0}:{1}|./{1}", JAVAEE_PREFIX, VALIDATOR_ID );
        elementNameExpressions.put( VALIDATOR, createXPath( validatorIdExpr ) );

        String behaviorIdExpr = MessageFormat.format( "./{0}:{1}|./{1}", JAVAEE_PREFIX, BEHAVIOR_ID );
        elementNameExpressions.put( BEHAVIOR, createXPath( behaviorIdExpr ) );

        // renderer | client-behavior-renderer

        return new ElementsComparator( JAVAEE_URI, elements, elementNameExpressions );
    }

    private void checkRootElement( final Element element )
    {
        if ( !FACES_CONFIG.equals( element.getName() ) )
        {
            throw new IllegalArgumentException( "Root element name: " + element.getName() );
        }

        if ( !isJavaEEOrDefaultNamespace( element ) )
        {
            throw new IllegalArgumentException( "Root element namespace: " + element.getNamespaceURI() );
        }
    }

    @Override
    protected void processDocument( final String resource, final Document document )
        throws JDOMException
    {
        hasProcessedConfigFiles = true;

        Element rootElement = document.getRootElement();
        checkRootElement( rootElement );

        if ( metadataComplete == ThreeState.UNDEFINED || Boolean.TRUE.equals( metadataComplete ) )
        {
            String metadataCompleteString = rootElement.getAttributeValue( METADATA_COMPLETE );
            if ( !"true".equals( metadataCompleteString ) )
            {
                metadataComplete = ThreeState.FALSE;
            }
        }

        Filter renderkitIdFilter =
            new ElementFilter().and( new ElementFilter( RENDER_KIT_ID, getJavaEENamespace() ).negate() );
        XPath renderKitIdXPath = createXPath( RENDER_KIT_ID_EXPRESSION );
        List<Element> children = checkedList( rootElement.getChildren(), Element.class );
        for ( Element child : children )
        {
            if ( !JAVAEE_URI.equals( child.getNamespaceURI() ) )
            {
                simpleElements.add( child );
            }
            else
            {
                String name = child.getName();

                if ( UNHANDLED_ELEMENTS_NAME_SET.contains( name ) )
                {
                    // TODO nick - log
                    continue;
                }

                if ( NAME.equals( name ) )
                {
                    String childConfigName = child.getTextTrim();

                    if ( childConfigName.length() != 0 )
                    {
                        if ( configName != null && !configName.equals( childConfigName ) )
                        {
                            throw new IllegalArgumentException(
                                                                MessageFormat.format( "Conflicting <name> elements detected in faces-config.xml files: ''{0}'' & ''{1}''",
                                                                                      configName,
                                                                                      childConfigName ) );
                        }

                        if ( configName == null )
                        {
                            configName = childConfigName;
                        }
                    }
                }
                else if ( AGGREGATOR_ELEMENTS_NAME_SET.contains( name ) )
                {
                    List<Element> elementsList = aggregatorElements.get( name );
                    if ( elementsList == null )
                    {
                        elementsList = new ArrayList<Element>();
                        aggregatorElements.put( name, elementsList );
                    }

                    List<Element> aggregatorChildren = checkedList( child.getChildren(), Element.class );
                    elementsList.addAll( cloneAndImportElements( aggregatorChildren ) );
                }
                else if ( RENDER_KIT.equals( name ) )
                {
                    String renderkitId = renderKitIdXPath.valueOf( child );
                    if ( renderkitId == null )
                    {
                        renderkitId = "";
                    }

                    List<Element> elementsList = renderkitElements.get( renderkitId );
                    if ( elementsList == null )
                    {
                        elementsList = new ArrayList<Element>();
                        renderkitElements.put( renderkitId, elementsList );
                    }

                    List<Element> renderkitChildren =
                        checkedList( child.getContent( renderkitIdFilter ), Element.class );
                    elementsList.addAll( cloneAndImportElements( renderkitChildren ) );
                }
                else
                {
                    simpleElements.add( cloneAndImportElement( child ) );
                }
            }
        }
    }

    @Override
    protected void resetTransformer()
    {
        super.resetTransformer();

        metadataComplete = ThreeState.UNDEFINED;

        simpleElements.clear();
        aggregatorElements.clear();
        renderkitElements.clear();
    }

    @Override
    protected void writeMergedConfigFiles( final Archiver archiver )
        throws ArchiverException
    {
        Comparator<Element> comparator;
        try
        {
            comparator = createElementsComparator();
        }
        catch ( JDOMException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }

        Document document = new Document();
        Namespace javaEENamespace = getJavaEENamespace();
        Element rootElement = new Element( FACES_CONFIG, javaEENamespace );
        rootElement.setAttribute( VERSION, CURRENT_VERSION );

        if ( metadataComplete != ThreeState.UNDEFINED )
        {
            rootElement.setAttribute( METADATA_COMPLETE, String.valueOf( metadataComplete == ThreeState.TRUE ) );
        }
        addSchemaLocation( rootElement, "http://java.sun.com/xml/ns/javaee/web-facesconfig_2_0.xsd" );
        document.addContent( rootElement );

        List<Element> rootElementChildren = new ArrayList<Element>();

        if ( configName != null )
        {
            Element nameElement = new Element( NAME, javaEENamespace );
            nameElement.setText( configName );
            rootElementChildren.add( nameElement );
        }

        rootElementChildren.addAll( simpleElements );

        for ( Map.Entry<String, List<Element>> entry : aggregatorElements.entrySet() )
        {
            String elementName = entry.getKey();
            List<Element> aggregatorElementChildren = entry.getValue();

            Element aggregatorElement = new Element( elementName, javaEENamespace );
            rootElementChildren.add( aggregatorElement );

            Collections.sort( aggregatorElementChildren, comparator );
            aggregatorElement.addContent( aggregatorElementChildren );
        }

        for ( Map.Entry<String, List<Element>> entry : renderkitElements.entrySet() )
        {
            String renderkitId = entry.getKey();
            List<Element> renderkitElementChildren = entry.getValue();

            Element renderkitElement = new Element( RENDER_KIT, javaEENamespace );
            rootElementChildren.add( renderkitElement );

            if ( renderkitId.length() != 0 )
            {
                Element renderkitIdElement = new Element( RENDER_KIT_ID, javaEENamespace );
                renderkitIdElement.setText( renderkitId );
                renderkitElementChildren.add( renderkitIdElement );
            }

            Collections.sort( renderkitElementChildren, comparator );
            renderkitElement.addContent( renderkitElementChildren );
        }

        Collections.sort( rootElementChildren, comparator );

        rootElement.addContent( rootElementChildren );

        addToArchive( FACES_CONFIG_FILE_PATH, document, archiver );
    }

    @SuppressWarnings( "rawtypes" )
    @Override
    public List getVirtualFiles()
    {
        if ( hasProcessedConfigFiles )
        {
            return Collections.singletonList( FACES_CONFIG_FILE_PATH );
        }

        return Collections.emptyList();
    }

    @Override
    protected boolean isHandled( final FileInfo fileInfo )
    {
        if ( !fileInfo.isFile() )
        {
            return false;
        }

        String name = getMetaInfResourceName( fileInfo.getName() );
        if ( name == null )
        {
            return false;
        }

        return name.equals( FACES_CONFIG_FILE_NAME ) || name.endsWith( DOT_FACES_CONFIG_FILE_NAME );
    }
}
