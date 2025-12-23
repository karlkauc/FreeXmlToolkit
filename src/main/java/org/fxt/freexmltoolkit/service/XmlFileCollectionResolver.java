/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Saxon CollectionFinder implementation for batch XQuery processing.
 * Allows the XQuery collection() function to work with a predefined list of XML files.
 *
 * Usage in XQuery:
 * <pre>
 * for $doc in collection()  (: returns all configured files :)
 * return ...
 * </pre>
 */
public class XmlFileCollectionResolver implements CollectionFinder {

    private static final Logger logger = LogManager.getLogger(XmlFileCollectionResolver.class);

    /** The default collection URI when collection() is called without arguments */
    public static final String DEFAULT_COLLECTION_URI = "http://freexmltoolkit.org/default-collection";

    private final List<File> xmlFiles;

    /**
     * Create a new resolver with an empty file list.
     */
    public XmlFileCollectionResolver() {
        this.xmlFiles = new ArrayList<>();
    }

    /**
     * Create a new resolver with the given XML files.
     *
     * @param files list of XML files to include in the collection
     */
    public XmlFileCollectionResolver(List<File> files) {
        this.xmlFiles = new ArrayList<>(files);
    }

    /**
     * Set the XML files for this collection.
     *
     * @param files list of XML files
     */
    public void setXmlFiles(List<File> files) {
        this.xmlFiles.clear();
        if (files != null) {
            this.xmlFiles.addAll(files);
        }
        logger.debug("Collection resolver configured with {} files", xmlFiles.size());
    }

    /**
     * Add XML files from a directory.
     *
     * @param directory the directory to scan
     * @param pattern file pattern (e.g., "*.xml")
     */
    public void addDirectory(File directory, String pattern) {
        if (directory == null || !directory.isDirectory()) {
            logger.warn("Invalid directory: {}", directory);
            return;
        }

        File[] files = directory.listFiles((dir, name) -> {
            if (pattern == null || pattern.isEmpty() || pattern.equals("*") || pattern.equals("*.xml")) {
                return name.toLowerCase().endsWith(".xml");
            }
            // Simple pattern matching (e.g., "order*.xml")
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return name.matches(regex);
        });

        if (files != null) {
            for (File file : files) {
                if (!xmlFiles.contains(file)) {
                    xmlFiles.add(file);
                }
            }
            logger.debug("Added {} files from directory {}", files.length, directory.getAbsolutePath());
        }
    }

    /**
     * Clear all files from the collection.
     */
    public void clear() {
        xmlFiles.clear();
    }

    /**
     * Get the number of files in the collection.
     */
    public int size() {
        return xmlFiles.size();
    }

    /**
     * Get the list of files in this collection.
     */
    public List<File> getFiles() {
        return new ArrayList<>(xmlFiles);
    }

    @Override
    public ResourceCollection findCollection(net.sf.saxon.expr.XPathContext context, String collectionURI)
            throws XPathException {

        logger.debug("findCollection called with URI: {}", collectionURI);

        // Handle null URI (default collection) or our default URI
        if (collectionURI == null || collectionURI.isEmpty() ||
            DEFAULT_COLLECTION_URI.equals(collectionURI)) {
            return new XmlFileResourceCollection(xmlFiles, context.getConfiguration());
        }

        // Handle file:// URIs pointing to directories
        if (collectionURI.startsWith("file://")) {
            String path = collectionURI.substring(7);
            // Handle query parameters like ?select=*.xml
            int queryIndex = path.indexOf('?');
            String pattern = null;
            if (queryIndex > 0) {
                String query = path.substring(queryIndex + 1);
                path = path.substring(0, queryIndex);
                if (query.startsWith("select=")) {
                    pattern = query.substring(7);
                }
            }

            File dir = new File(path);
            if (dir.isDirectory()) {
                List<File> dirFiles = new ArrayList<>();
                addDirectoryFiles(dir, pattern, dirFiles);
                return new XmlFileResourceCollection(dirFiles, context.getConfiguration());
            }
        }

        // If we can't handle the URI, return our default collection
        logger.debug("Returning default collection for unhandled URI");
        return new XmlFileResourceCollection(xmlFiles, context.getConfiguration());
    }

    private void addDirectoryFiles(File directory, String pattern, List<File> result) {
        File[] files = directory.listFiles((dir, name) -> {
            if (pattern == null || pattern.isEmpty() || pattern.equals("*") || pattern.equals("*.xml")) {
                return name.toLowerCase().endsWith(".xml");
            }
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return name.matches(regex);
        });

        if (files != null) {
            for (File file : files) {
                result.add(file);
            }
        }
    }

    /**
     * ResourceCollection implementation that provides XML resources from files.
     */
    private static class XmlFileResourceCollection implements ResourceCollection {

        private final List<File> files;
        private final Configuration configuration;

        public XmlFileResourceCollection(List<File> files, Configuration configuration) {
            this.files = files;
            this.configuration = configuration;
        }

        @Override
        public String getCollectionURI() {
            return DEFAULT_COLLECTION_URI;
        }

        @Override
        public Iterator<String> getResourceURIs(net.sf.saxon.expr.XPathContext context) {
            return files.stream()
                    .map(File::toURI)
                    .map(Object::toString)
                    .iterator();
        }

        @Override
        public Iterator<? extends Resource> getResources(net.sf.saxon.expr.XPathContext context)
                throws XPathException {
            List<Resource> resources = new ArrayList<>();

            Processor processor = new Processor(configuration);
            DocumentBuilder builder = processor.newDocumentBuilder();

            for (File file : files) {
                try {
                    StreamSource source = new StreamSource(file);
                    XdmNode document = builder.build(source);
                    NodeInfo nodeInfo = document.getUnderlyingNode();
                    resources.add(new NodeResource(nodeInfo, file.toURI().toString()));
                } catch (SaxonApiException e) {
                    logger.error("Failed to load XML file: {}", file.getAbsolutePath(), e);
                    throw new XPathException("Failed to load file: " + file.getName() + " - " + e.getMessage());
                }
            }

            return resources.iterator();
        }

        @Override
        public boolean isStable(net.sf.saxon.expr.XPathContext context) {
            return true; // Files don't change during query execution
        }
    }

    /**
     * Simple Resource implementation wrapping a NodeInfo.
     */
    private static class NodeResource implements Resource {
        private final NodeInfo nodeInfo;
        private final String resourceURI;

        public NodeResource(NodeInfo nodeInfo, String resourceURI) {
            this.nodeInfo = nodeInfo;
            this.resourceURI = resourceURI;
        }

        @Override
        public String getResourceURI() {
            return resourceURI;
        }

        @Override
        public net.sf.saxon.om.Item getItem() throws XPathException {
            return nodeInfo;
        }

        @Override
        public String getContentType() {
            return "application/xml";
        }
    }
}
