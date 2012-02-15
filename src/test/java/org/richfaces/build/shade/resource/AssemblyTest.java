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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.assembly.model.ContainerDescriptorHandlerConfig;
import org.apache.maven.plugin.assembly.model.DependencySet;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AssemblyTest
    extends PlexusTestCase
{

    private AssemblyArchiver assemblyArchiver;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    @Before
    public void setUp()
        throws Exception
    {
        super.setUp();
        assemblyArchiver = (AssemblyArchiver) lookup( AssemblyArchiver.ROLE );
    }

    @Override
    @After
    public void tearDown()
        throws Exception
    {
        super.tearDown();
        assemblyArchiver = null;
    }

    private File doAssembly( final File targetFile )
        throws Exception
    {
        Assembly assembly = new Assembly();
        assembly.setId( targetFile.getName() );
        assembly.setIncludeBaseDirectory( false );

        DependencySet ds = new DependencySet();
        ds.setUnpack( true );
        ds.setOutputDirectory( "/" );
        ds.setUseTransitiveDependencies( false );
        ds.setUseProjectArtifact( false );

        assembly.addDependencySet( ds );
        
        String configuration = "<configuration><outputDirectory>target/taglibs</outputDirectory></configuration>";
        Xpp3Dom configurationDom = Xpp3DomBuilder.build(new ByteArrayInputStream(configuration.getBytes()), "UTF-8");
        
        ContainerDescriptorHandlerConfig taglibConfig = new ContainerDescriptorHandlerConfig();
        taglibConfig.setHandlerName( "taglib-xml" );
        taglibConfig.setConfiguration(configurationDom);

        ContainerDescriptorHandlerConfig facesConfig = new ContainerDescriptorHandlerConfig();
        facesConfig.setHandlerName( "faces-xml" );
        facesConfig.setConfiguration(configurationDom);
        
        ContainerDescriptorHandlerConfig resourceMappingProperties = new ContainerDescriptorHandlerConfig();
        resourceMappingProperties.setHandlerName( "resource-mappings-properties" );

        assembly.addContainerDescriptorHandler( taglibConfig );
        assembly.addContainerDescriptorHandler( facesConfig );
        assembly.addContainerDescriptorHandler( resourceMappingProperties );

        File jarBasedir = new File( getBasedir(), "src/test/jars" );

        TestConfigSource configSource = new TestConfigSource( targetFile, jarBasedir, getContainer(), tempFolder );

        return assemblyArchiver.createArchive( assembly, targetFile.getName(), "jar", configSource );
    }

    @Test
    public void testTransformation()
        throws Exception
    {
        File assemblyOutput = doAssembly( new File( "target/ui-shaded.jar" ) );

        JarFile jf = new JarFile( assemblyOutput );

        System.out.println( assemblyOutput.getAbsolutePath() + ":" );
        Enumeration<JarEntry> e = jf.entries();
        while ( e.hasMoreElements() )
        {
            String file = e.nextElement().getName();

            if ( file.endsWith( ".xml" ) )
            {
                System.out.println( file );
            }
        }

        // TODO nick - check shaded .jar
    }

    public class TestConfigSource
        implements AssemblerConfigurationSource
    {

        private final File basedir;

        private final MavenFileFilter mavenFileFilter;

        private final List<ArtifactRepository> remoteRepos;

        private final ArtifactRepository localRepo;

        private final MavenProject project;

        private final MavenSession session;

        private final String finalName;

        public TestConfigSource( final File targetFile, final File jarBasedir, final PlexusContainer container,
                                 final TemporaryFolder tempFolder )
            throws ComponentLookupException
        {
            this.basedir = tempFolder.newFolder( "target/test-assembly-basedir" );

            this.mavenFileFilter = (MavenFileFilter) container.lookup( MavenFileFilter.class.getName() );
            remoteRepos =
                Collections.<ArtifactRepository> singletonList( new TestArtifactRepository( "remote", jarBasedir ) );

            localRepo = new TestArtifactRepository( "local", new File( basedir, "target/local-repo" ) );

            String name = targetFile.getName();
            int idx = name.indexOf( '.' );
            if ( idx > 0 )
            {
                name = name.substring( 0, idx );
            }
            finalName = name;

            Model model = new Model();
            model.setModelVersion( "4.0.0" );
            model.setGroupId( "foo" );
            model.setVersion( "1" );
            model.setArtifactId( finalName );

            model.addDependency( newDep( "componentcontrol-ui", "4.0.0-SNAPSHOT" ) );
            model.addDependency( newDep( "functions-ui", "4.0.0-SNAPSHOT" ) );
            model.addDependency( newDep( "richfaces-ui-core-ui", "4.0.0-SNAPSHOT" ) );
            model.addDependency( newDep( "richfaces-ui-input-ui", "4.1.0-SNAPSHOT" ) );
            model.addDependency( newDep( "richfaces-ui-iteration-ui", "4.1.0-SNAPSHOT" ) );

            project = new MavenProject( model );

            session =
                new MavenSession( container, new Settings(), localRepo, null, null, Collections.emptyList(),
                                  basedir.getAbsolutePath(), System.getProperties(), new Properties(), new Date() );
        }

        private Dependency newDep( final String aid, final String ver )
        {
            Dependency dep = new Dependency();
            dep.setGroupId( "foo" );
            dep.setScope( "compile" );

            dep.setArtifactId( aid );
            dep.setVersion( ver );

            return dep;
        }

        @Override
        public MavenProject getProject()
        {
            return project;
        }

        @Override
        public ArtifactRepository getLocalRepository()
        {
            return localRepo;
        }

        @Override
        public List<ArtifactRepository> getRemoteRepositories()
        {
            return remoteRepos;
        }

        @Override
        public MavenSession getMavenSession()
        {
            return session;
        }

        @Override
        public MavenFileFilter getMavenFileFilter()
        {
            return mavenFileFilter;
        }

        @Override
        public String getFinalName()
        {
            return finalName;
        }

        @Override
        public File getBasedir()
        {
            return basedir;
        }

        @Override
        public File getTemporaryRootDirectory()
        {
            return new File( basedir, "target/temp-root" );
        }

        @Override
        public File getArchiveBaseDirectory()
        {
            return new File( basedir, "target/archive-basedir" );
        }

        @Override
        public File getOutputDirectory()
        {
            return new File( basedir, "target" );
        }

        @Override
        public File getWorkingDirectory()
        {
            return new File( basedir, "target/archive-workdir" );
        }

        @Override
        public String getDescriptor()
        {
            // NOP. Used by higher levels of assembly plugin superstructure.
            return null;
        }

        @Override
        public String getDescriptorId()
        {
            // NOP. Used by higher levels of assembly plugin superstructure.
            return null;
        }

        @Override
        public String[] getDescriptors()
        {
            // NOP. Used by higher levels of assembly plugin superstructure.
            return null;
        }

        @Override
        public String[] getDescriptorReferences()
        {
            // NOP. Used by higher levels of assembly plugin superstructure.
            return null;
        }

        @Override
        public File getDescriptorSourceDirectory()
        {
            // NOP. Used by higher levels of assembly plugin superstructure.
            return null;
        }

        @Override
        public boolean isSiteIncluded()
        {
            return false;
        }

        @Override
        public File getSiteDirectory()
        {
            return null;
        }

        @Override
        public boolean isAssemblyIdAppended()
        {
            return false;
        }

        @Override
        public String getClassifier()
        {
            return null;
        }

        @Override
        public String getTarLongFileMode()
        {
            return "gnu";
        }

        @Override
        public MavenArchiveConfiguration getJarArchiveConfiguration()
        {
            return new MavenArchiveConfiguration();
        }

        @Override
        public List<String> getFilters()
        {
            return null;
        }

        @Override
        public List<MavenProject> getReactorProjects()
        {
            return Collections.singletonList( getProject() );
        }

        @Override
        public boolean isDryRun()
        {
            return false;
        }

        @Override
        public boolean isIgnoreDirFormatExtensions()
        {
            return true;
        }

        @Override
        public boolean isIgnoreMissingDescriptor()
        {
            return false;
        }

        @Override
        public String getArchiverConfig()
        {
            return null;
        }

        @Override
        public boolean isUpdateOnly()
        {
            return false;
        }

        @Override
        public boolean isUseJvmChmod()
        {
            return true;
        }

        @Override
        public boolean isIgnorePermissions()
        {
            return true;
        }

    }

    public static final class TestArtifactRepository
        implements ArtifactRepository
    {

        private final File basedir;

        private final String id;

        public TestArtifactRepository( final String id, final File basedir )
        {
            this.id = id;
            this.basedir = basedir;
        }

        @Override
        public String pathOf( final Artifact artifact )
        {
            return artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getType();
        }

        @Override
        public String pathOfRemoteRepositoryMetadata( final ArtifactMetadata md )
        {
            return "maven-metadata.xml";
        }

        @Override
        public String pathOfLocalRepositoryMetadata( final ArtifactMetadata metadata,
                                                     final ArtifactRepository repository )
        {
            return "maven-metadata.xml";
        }

        @Override
        public String getUrl()
        {
            try
            {
                return basedir.toURI().normalize().toURL().toExternalForm();
            }
            catch ( MalformedURLException e )
            {
            }

            return "file:" + basedir.getAbsolutePath();
        }

        @Override
        public String getBasedir()
        {
            return basedir.getAbsolutePath();
        }

        @Override
        public String getProtocol()
        {
            return "file";
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public ArtifactRepositoryPolicy getSnapshots()
        {
            return new ArtifactRepositoryPolicy();
        }

        @Override
        public ArtifactRepositoryPolicy getReleases()
        {
            return new ArtifactRepositoryPolicy();
        }

        @Override
        public ArtifactRepositoryLayout getLayout()
        {
            return new DefaultRepositoryLayout();
        }

        @Override
        public String getKey()
        {
            return id;
        }

        @Override
        public boolean isUniqueVersion()
        {
            return false;
        }

        @Override
        public void setBlacklisted( final boolean blackListed )
        {
            new Throwable( "Cannot blacklist! Called from:" ).printStackTrace();
        }

        @Override
        public boolean isBlacklisted()
        {
            return false;
        }

    }
}
