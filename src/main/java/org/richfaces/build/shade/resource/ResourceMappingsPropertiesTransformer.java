package org.richfaces.build.shade.resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;

public class ResourceMappingsPropertiesTransformer extends BaseResourceTransformer {

    private static final String RESOURCE_MAPPINGS_FILE_PATH = "META-INF/richfaces/resource-mappings.properties";

    private Set<String> records = new TreeSet<String>();

    @Override
    protected void resetTransformer() {
        super.resetTransformer();
    }

    @Override
    protected boolean isHandled(FileInfo fileInfo) {
        if (!fileInfo.isFile()) {
            return false;
        }
        return fileInfo.getName().equals(RESOURCE_MAPPINGS_FILE_PATH);
    }

    @Override
    protected String getMergedFileName() {
        return RESOURCE_MAPPINGS_FILE_PATH;
    }

    @Override
    protected void processFile(FileInfo fileInfo) {
        InputStream is = null;
        StringBuffer buffer = new StringBuffer();
        try {
            is = fileInfo.getContents();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }

            records.add(buffer.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeSafely(is);
        }
    }

    @Override
    protected void writeMergedConfigFiles(Archiver archiver) throws ArchiverException {
        File f;
        BufferedWriter writer = null;
        try {
            f = File.createTempFile("richfaces-assembly-transform.", ".tmp");
            f.deleteOnExit();

            writer = new BufferedWriter(new FileWriter(f));
            for (String buffer : records) {
                writer.append(buffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closeSafely(writer);
        }

        archiver.addFile(f, RESOURCE_MAPPINGS_FILE_PATH);
    }

    private void closeSafely(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
