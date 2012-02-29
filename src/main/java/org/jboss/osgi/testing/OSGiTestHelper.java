/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.testing;

import org.jboss.logging.Logger;
import org.jboss.osgi.spi.ConstantsHelper;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.osgi.framework.Bundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * An OSGi Test Helper
 * 
 * @author Thomas.Diesler@jboss.org
 * @since 25-Sep-2008
 */
public class OSGiTestHelper {

    // Provide logging
    private static final Logger log = Logger.getLogger(OSGiTestHelper.class);

    public static final String SYSPROP_TEST_RESOURCES_DIRECTORY = "test.resources.directory";
    public static final String SYSPROP_TEST_ARCHIVE_DIRECTORY = "test.archive.directory";

    private static String testResourcesDir;
    private static String testArchiveDir;
    static {
        testResourcesDir = System.getProperty(SYSPROP_TEST_RESOURCES_DIRECTORY, "target/test-classes");
        testArchiveDir = System.getProperty(SYSPROP_TEST_ARCHIVE_DIRECTORY, "target/test-libs");
    }

    // Hide ctor
    private OSGiTestHelper() {
    }

    /** Try to discover the URL for the test resource */
    public static URL getResourceURL(String resource) {
        URL resURL = null;
        try {
            File resourceFile = getResourceFile(resource);
            resURL = resourceFile.toURI().toURL();
        } catch (MalformedURLException e) {
            // ignore
        }
        return resURL;
    }

    /** Try to discover the File for the test resource */
    public static File getResourceFile(String resource) {
        File file = new File(resource);
        if (file.exists())
            return file;

        file = new File(testResourcesDir + "/" + resource);
        if (file.exists())
            return file;

        throw new IllegalArgumentException("Cannot obtain '" + testResourcesDir + "/" + resource + "'");
    }

    /** Try to discover the URL for the deployment archive */
    public static URL getTestArchiveURL(String archive) {
        try {
            URL archiveURL = new URL(archive);
            return archiveURL;
        } catch (MalformedURLException ex) {
            // ignore
        }
        try {
            File file = getTestArchiveFile(archive);
            return file.toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Try to discover the absolute path for the deployment archive */
    public static String getTestArchivePath(String archive) {
        File archiveFile = getTestArchiveFile(archive);
        return archiveFile.getAbsolutePath();
    }

    /** Try to discover the File for the deployment archive */
    public static File getTestArchiveFile(String archive) {
        File file = new File(archive);
        if (file.exists())
            return file;

        file = new File(testArchiveDir + "/" + archive);
        if (file.exists())
            return file;

        throw new IllegalArgumentException("Cannot obtain '" + testArchiveDir + "/" + archive + "'.");
    }

    public static String getServerHost() {
        String bindAddress = System.getProperty("jboss.bind.address", "localhost");
        return bindAddress;
    }

    public static String getTargetContainer() {
        String targetContainer = System.getProperty("target.container");
        return targetContainer;
    }

    public static String getFrameworkName() {
        String framework = System.getProperty("framework");
        if (framework == null || framework.length() == 0 || framework.equals("${framework}"))
            framework = "jbossmsc";

        return framework;
    }

    public static JavaArchive assembleArchive(String name, String resource, Class<?>... packages) throws Exception {
        return assembleArchive(name, new String[] { resource }, packages);
    }

    public static JavaArchive assembleArchive(String name, String[] resources, Class<?>... packages) throws IOException {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name + ".jar");
        if (resources != null) {
            for (String res : resources) {
                URL url = OSGiTestHelper.class.getResource(res);
                if (url == null)
                    throw new IllegalArgumentException("Cannot load resource: " + res);

                final VirtualFile file = AbstractVFS.toVirtualFile(url);
                if (file.isDirectory()) {
                    addResources(archive, file, file);
                } else {
                    addResource(archive, res, file);
                }
            }
        }
        if (packages != null) {
            for (Class<?> clazz : packages) {
                URL url = clazz.getResource("/");
                VirtualFile base = AbstractVFS.toVirtualFile(url);

                String path = clazz.getName().replace('.', '/');
                path = path.substring(0, path.lastIndexOf("/"));

                VirtualFile classes = base.getChild(path);
                addResources(archive, base, classes);
            }
        }
        return archive;
    }

    public static VirtualFile toVirtualFile(Archive<?> archive) throws IOException, MalformedURLException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        return AbstractVFS.toVirtualFile(archive.getName(), exporter.exportAsInputStream());
    }

    public static InputStream toInputStream(Archive<?> archive) throws IOException {
        ZipExporter exporter = archive.as(ZipExporter.class);
        return exporter.exportAsInputStream();
    }

    private static void addResources(JavaArchive archive, VirtualFile basedir, VirtualFile resdir) throws IOException {
        String basepath = basedir.getPathName();
        for (final VirtualFile child : resdir.getChildrenRecursively()) {
            if (child.isDirectory())
                continue;

            String path = child.getPathName();
            path = path.substring(basepath.length());

            addResource(archive, path, child);
        }
    }

    private static void addResource(JavaArchive archive, String path, final VirtualFile file) {
        Asset asset = new Asset() {

            public InputStream openStream() {
                try {
                    return file.openStream();
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot open stream for: " + file, ex);
                }
            }
        };
        archive.add(asset, path);
    }

    public static void assertBundleState(int expState, int wasState) {
        String expstr = ConstantsHelper.bundleState(expState);
        String wasstr = ConstantsHelper.bundleState(wasState);
        assertEquals("Bundle " + expstr, expstr, wasstr);
    }

    public static Class<?> assertLoadClass(Bundle bundle, String className) {
        try {
            return bundle.loadClass(className);
        } catch (ClassNotFoundException ex) {
            String message = "Unexpected ClassNotFoundException for: " + bundle.getSymbolicName() + " loads " + className;
            log.error(message, ex);
            fail(message);
            return null;
        }
    }

    public static void assertLoadClassFail(Bundle bundle, String className) {
        try {
            Class<?> clazz = bundle.loadClass(className);
            String message = bundle.getSymbolicName() + " loads " + className;
            fail("ClassNotFoundException expected for: " + message + "\nLoaded from " + clazz.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // expected
        }
    }

    public static void assertLoadClass(Bundle bundle, String className, Bundle exporter) {
        Class<?> importerClazz = assertLoadClass(bundle, className);
        Class<?> exporterClazz = assertLoadClass(exporter, className);
        assertEquals("Loaded from ClassLoader", exporterClazz.getClassLoader(), importerClazz.getClassLoader());
    }
}
