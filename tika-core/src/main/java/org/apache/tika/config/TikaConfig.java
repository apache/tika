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
package org.apache.tika.config;

import javax.imageio.spi.ServiceRegistry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.concurrent.ConfigurableThreadPoolExecutor;
import org.apache.tika.concurrent.SimpleThreadPoolExecutor;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.DefaultMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.utils.AnnotationUtils;
import org.apache.tika.utils.XMLReaderUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.apache.tika.config.ServiceLoader.getContextClassLoader;

/**
 * Parse xml config file.
 */
public class TikaConfig {

    private static MimeTypes getDefaultMimeTypes(ClassLoader loader) {
        return MimeTypes.getDefaultMimeTypes(loader);
    }

    protected static CompositeDetector getDefaultDetector(
            MimeTypes types, ServiceLoader loader) {
        return new DefaultDetector(types, loader);
    }

    protected static CompositeEncodingDetector getDefaultEncodingDetector(
            ServiceLoader loader) {
        return new DefaultEncodingDetector(loader);
    }


    private static CompositeParser getDefaultParser(
            MimeTypes types, ServiceLoader loader, EncodingDetector encodingDetector) {
        return new DefaultParser(types.getMediaTypeRegistry(), loader, encodingDetector);
    }

    private static Translator getDefaultTranslator(ServiceLoader loader) {
        return new DefaultTranslator(loader);
    }

    private static ConfigurableThreadPoolExecutor getDefaultExecutorService() {
        return new SimpleThreadPoolExecutor();
    }

    private static MetadataFilter getDefaultMetadataFilter(ServiceLoader loader) {
        return new DefaultMetadataFilter(loader);
    }

    //use this to look for unneeded instantiations of TikaConfig
    protected static AtomicInteger TIMES_INSTANTIATED = new AtomicInteger();

    private final ServiceLoader serviceLoader;
    private final CompositeParser parser;
    private final CompositeDetector detector;
    private final Translator translator;

    private final MimeTypes mimeTypes;
    private final ExecutorService executorService;
    private final EncodingDetector encodingDetector;
    private final MetadataFilter metadataFilter;

    public TikaConfig(String file)
            throws TikaException, IOException, SAXException {
        this(Paths.get(file));
    }

    public TikaConfig(Path path)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(path));
    }
    public TikaConfig(Path path, ServiceLoader loader)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(path), loader);
    }

    public TikaConfig(File file)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(file.toPath()));
    }

    public TikaConfig(File file, ServiceLoader loader)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(file.toPath()), loader);
    }

    public TikaConfig(URL url)
            throws TikaException, IOException, SAXException {
        this(url, ServiceLoader.getContextClassLoader());
    }
    public TikaConfig(URL url, ClassLoader loader)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(url.toString()).getDocumentElement(), loader);
    }
    public TikaConfig(URL url, ServiceLoader loader)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(url.toString()).getDocumentElement(), loader);
    }

    public TikaConfig(InputStream stream)
            throws TikaException, IOException, SAXException {
        this(XMLReaderUtils.buildDOM(stream));
    }

    public TikaConfig(Document document) throws TikaException, IOException {
        this(document.getDocumentElement());
    }
    public TikaConfig(Document document, ServiceLoader loader) throws TikaException, IOException {
        this(document.getDocumentElement(), loader);
    }

    public TikaConfig(Element element) throws TikaException, IOException {
        this(element, serviceLoaderFromDomElement(element, null));
    }

    public TikaConfig(Element element, ClassLoader loader)
            throws TikaException, IOException {
        this(element, serviceLoaderFromDomElement(element, loader));
    }

    private TikaConfig(Element element, ServiceLoader loader)
            throws TikaException, IOException {
        DetectorXmlLoader detectorLoader = new DetectorXmlLoader();
        TranslatorXmlLoader translatorLoader = new TranslatorXmlLoader();
        ExecutorServiceXmlLoader executorLoader = new ExecutorServiceXmlLoader();
        EncodingDetectorXmlLoader encodingDetectorXmlLoader = new EncodingDetectorXmlLoader();
        MetadataFilterXmlLoader metadataFilterXmlLoader = new MetadataFilterXmlLoader();
        updateXMLReaderUtils(element);
        this.mimeTypes = typesFromDomElement(element);
        this.detector = detectorLoader.loadOverall(element, mimeTypes, loader);
        this.encodingDetector = encodingDetectorXmlLoader.loadOverall(element, mimeTypes, loader);

        ParserXmlLoader parserLoader = new ParserXmlLoader(encodingDetector);
        this.parser = parserLoader.loadOverall(element, mimeTypes, loader);
        this.translator = translatorLoader.loadOverall(element, mimeTypes, loader);
        this.executorService = executorLoader.loadOverall(element, mimeTypes, loader);
        this.metadataFilter = metadataFilterXmlLoader.loadOverall(element, mimeTypes, loader);
        this.serviceLoader = loader;
        TIMES_INSTANTIATED.incrementAndGet();
    }

    /**
     * Creates a Tika configuration from the built-in media type rules
     * and all the {@link Parser} implementations available through the
     * {@link ServiceRegistry service provider mechanism} in the given
     * class loader.
     *
     * @since Apache Tika 0.8
     * @param loader the class loader through which parser implementations
     *               are loaded, or <code>null</code> for no parsers
     * @throws MimeTypeException if the built-in media type rules are broken
     * @throws IOException  if the built-in media type rules can not be read
     */
    public TikaConfig(ClassLoader loader)
            throws MimeTypeException, IOException {
        this.serviceLoader = new ServiceLoader(loader);
        this.mimeTypes = getDefaultMimeTypes(loader);
        this.detector = getDefaultDetector(mimeTypes, serviceLoader);
        this.encodingDetector = getDefaultEncodingDetector(serviceLoader);
        this.parser = getDefaultParser(mimeTypes, serviceLoader, encodingDetector);
        this.translator = getDefaultTranslator(serviceLoader);
        this.executorService = getDefaultExecutorService();
        this.metadataFilter = getDefaultMetadataFilter(serviceLoader);
        TIMES_INSTANTIATED.incrementAndGet();
    }

    /**
     * Creates a default Tika configuration.
     * First checks whether an XML config file is specified, either in
     * <ol>
     * <li>System property "tika.config", or</li>
     * <li>Environment variable TIKA_CONFIG</li>
     * </ol>
     * <p>If one of these have a value, try to resolve it relative to file
     * system or classpath.</p>
     * <p>If XML config is not specified, initialize from the built-in media
     * type rules and all the {@link Parser} implementations available through
     * the {@link ServiceRegistry service provider mechanism} in the context
     * class loader of the current thread.</p>
     *
     * @throws IOException if the configuration can not be read
     * @throws TikaException if problem with MimeTypes or parsing XML config
     */
    public TikaConfig() throws TikaException, IOException {

        String config = System.getProperty("tika.config");
        if (config == null || config.trim().equals("")) {
            config = System.getenv("TIKA_CONFIG");
        }

        if (config == null || config.trim().equals("")) {
            this.serviceLoader = new ServiceLoader();
            this.mimeTypes = getDefaultMimeTypes(getContextClassLoader());
            this.encodingDetector = getDefaultEncodingDetector(serviceLoader);
            this.parser = getDefaultParser(mimeTypes, serviceLoader, encodingDetector);
            this.detector = getDefaultDetector(mimeTypes, serviceLoader);
            this.translator = getDefaultTranslator(serviceLoader);
            this.executorService = getDefaultExecutorService();
            this.metadataFilter = getDefaultMetadataFilter(serviceLoader);
        } else {
            ServiceLoader tmpServiceLoader = new ServiceLoader();
            try (InputStream stream = getConfigInputStream(config, tmpServiceLoader)) {
                Element element = XMLReaderUtils.buildDOM(stream).getDocumentElement();
                updateXMLReaderUtils(element);
                serviceLoader = serviceLoaderFromDomElement(element, tmpServiceLoader.getLoader());
                DetectorXmlLoader detectorLoader = new DetectorXmlLoader();
                EncodingDetectorXmlLoader encodingDetectorLoader = new EncodingDetectorXmlLoader();
                TranslatorXmlLoader translatorLoader = new TranslatorXmlLoader();
                ExecutorServiceXmlLoader executorLoader = new ExecutorServiceXmlLoader();
                MetadataFilterXmlLoader metadataFilterXmlLoader = new MetadataFilterXmlLoader();

                this.mimeTypes = typesFromDomElement(element);
                this.encodingDetector = encodingDetectorLoader.loadOverall(element, mimeTypes, serviceLoader);


                ParserXmlLoader parserLoader = new ParserXmlLoader(encodingDetector);
                this.parser = parserLoader.loadOverall(element, mimeTypes, serviceLoader);
                this.detector = detectorLoader.loadOverall(element, mimeTypes, serviceLoader);
                this.translator = translatorLoader.loadOverall(element, mimeTypes, serviceLoader);
                this.executorService = executorLoader.loadOverall(element, mimeTypes, serviceLoader);
                this.metadataFilter = metadataFilterXmlLoader.loadOverall(element, mimeTypes, serviceLoader);
            } catch (SAXException e) {
                throw new TikaException(
                        "Specified Tika configuration has syntax errors: "
                                + config, e);
            }
        }
        TIMES_INSTANTIATED.incrementAndGet();
    }

    private void updateXMLReaderUtils(Element element) throws TikaException {

        Element child = getChild(element, "xml-reader-utils");
        if (child == null) {
            return;
        }
        String attr = child.getAttribute("maxEntityExpansions");
        if (attr != null) {
            XMLReaderUtils.setMaxEntityExpansions(Integer.parseInt(attr));
        }

        //make sure to call this after set entity expansions
        attr = child.getAttribute("poolSize");
        if (attr != null) {
            XMLReaderUtils.setPoolSize(Integer.parseInt(attr));
        }

    }

    private static InputStream getConfigInputStream(String config, ServiceLoader serviceLoader)
            throws TikaException, IOException {
        InputStream stream = null;
        try {
            stream = new URL(config).openStream();
        } catch (IOException ignore) {
        }
        if (stream == null) {
            stream = serviceLoader.getResourceAsStream(config);
        }
        if (stream == null) {
            Path file = Paths.get(config);
            if (Files.isRegularFile(file)) {
                stream = Files.newInputStream(file);
            }
        }
        if (stream == null) {
            throw new TikaException(
                    "Specified Tika configuration not found: " + config);
        }
        return stream;
    }

    private static String getText(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            return node.getNodeValue();
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            StringBuilder builder = new StringBuilder();
            NodeList list = node.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                builder.append(getText(list.item(i)));
            }
            return builder.toString();
        } else {
            return "";
        }
    }

    /**
     * @deprecated Use the {@link #getParser()} method instead
     */
    public Parser getParser(MediaType mimeType) {
        return parser.getParsers().get(mimeType);
    }

    /**
     * Returns the configured parser instance.
     *
     * @return configured parser
     */
    public Parser getParser() {
        return parser;
    }

    /**
     * Returns the configured detector instance.
     *
     * @return configured detector
     */
    public Detector getDetector() {
        return detector;
    }

    /**
     * Returns the configured encoding detector instance
     * @return configured encoding detector
     */
    public EncodingDetector getEncodingDetector() {
        return encodingDetector;
    }

    /**
     * Returns the configured translator instance.
     *
     * @return configured translator
     */
    public Translator getTranslator() {
        return translator;
    }
    
    public ExecutorService getExecutorService() {
        return executorService;
    }

    public MimeTypes getMimeRepository(){
        return mimeTypes;
    }

    public MediaTypeRegistry getMediaTypeRegistry() {
        return mimeTypes.getMediaTypeRegistry();
    }
    
    public ServiceLoader getServiceLoader() {
        return serviceLoader;
    }

    public MetadataFilter getMetadataFilter() {
        return metadataFilter;
    }
    /**
     * Provides a default configuration (TikaConfig).  Currently creates a
     * new instance each time it's called; we may be able to have it
     * return a shared instance once it is completely immutable.
     *
     * @return default configuration
     */
    public static TikaConfig getDefaultConfig() {
        try {
            return new TikaConfig();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to read default configuration", e);
        } catch (TikaException e) {
            throw new RuntimeException(
                    "Unable to access default configuration", e);
        }
    }

    private static Element getChild(Element element, String name) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && name.equals(child.getNodeName())) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }
    private static List<Element> getTopLevelElementChildren(Element element, 
            String parentName, String childrenName) throws TikaException {
        Node parentNode = null;
        if (parentName != null) {
            // Should be only zero or one <parsers> / <detectors> etc tag
            NodeList nodes = element.getElementsByTagName(parentName);
            if (nodes.getLength() > 1) {
                throw new TikaException("Properties may not contain multiple "+parentName+" entries");
            }
            else if (nodes.getLength() == 1) {
                parentNode = nodes.item(0);
            }
        } else {
            // All children directly on the master element
            parentNode = element;
        }
        
        if (parentNode != null) {
            // Find only the direct child parser/detector objects
            NodeList nodes = parentNode.getChildNodes();
            List<Element> elements = new ArrayList<Element>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element nodeE = (Element)node;
                    if (childrenName.equals(nodeE.getTagName())) {
                        elements.add(nodeE);
                    }
                }
            }
            return elements;
        } else {
            // No elements of this type
            return Collections.emptyList();
        }
    }

    private static MimeTypes typesFromDomElement(Element element)
            throws TikaException, IOException {
        Element mtr = getChild(element, "mimeTypeRepository");
        if (mtr != null && mtr.hasAttribute("resource")) {
            return MimeTypesFactory.create(mtr.getAttribute("resource"));
        } else {
            return getDefaultMimeTypes(null);
        }
    }
    
    private static Set<MediaType> mediaTypesListFromDomElement(
            Element node, String tag) 
            throws TikaException, IOException {
        Set<MediaType> types = null;
        NodeList children = node.getChildNodes();
        for (int i=0; i<children.getLength(); i++) {
            Node cNode = children.item(i);
            if (cNode instanceof Element) {
                Element cElement = (Element)cNode;
                if (tag.equals(cElement.getTagName())) {
                    String mime = getText(cElement);
                    MediaType type = MediaType.parse(mime);
                    if (type != null) {
                        if (types == null) types = new HashSet<>();
                        types.add(type);
                    } else {
                        throw new TikaException(
                                "Invalid media type name: " + mime);
                    }
                }
            }
        }
        if (types != null) return types;
        return Collections.emptySet();
    }
    
    private static ServiceLoader serviceLoaderFromDomElement(Element element, ClassLoader loader) throws TikaConfigException {
        Element serviceLoaderElement = getChild(element, "service-loader");
        ServiceLoader serviceLoader;

        if (serviceLoaderElement != null) {
            boolean dynamic = Boolean.parseBoolean(serviceLoaderElement.getAttribute("dynamic"));
            LoadErrorHandler loadErrorHandler = LoadErrorHandler.IGNORE;
            String loadErrorHandleConfig = serviceLoaderElement.getAttribute("loadErrorHandler");
            if(LoadErrorHandler.WARN.toString().equalsIgnoreCase(loadErrorHandleConfig)) {
                loadErrorHandler = LoadErrorHandler.WARN;
            } else if(LoadErrorHandler.THROW.toString().equalsIgnoreCase(loadErrorHandleConfig)) {
                loadErrorHandler = LoadErrorHandler.THROW;
            }
            InitializableProblemHandler initializableProblemHandler = getInitializableProblemHandler(serviceLoaderElement.getAttribute("initializableProblemHandler"));

            if (loader == null) {
                loader = ServiceLoader.getContextClassLoader();
            }
            serviceLoader = new ServiceLoader(loader, loadErrorHandler, initializableProblemHandler, dynamic);
        } else if(loader != null) {
            serviceLoader = new ServiceLoader(loader);
        } else {
            serviceLoader = new ServiceLoader();
        }
        return serviceLoader;
    }

    private static InitializableProblemHandler getInitializableProblemHandler(String initializableProblemHandler)
        throws TikaConfigException {
        if (initializableProblemHandler == null || initializableProblemHandler.length() == 0) {
            return InitializableProblemHandler.DEFAULT;
        }
        if (InitializableProblemHandler.IGNORE.toString().equalsIgnoreCase(initializableProblemHandler)) {
            return InitializableProblemHandler.IGNORE;
        } else if (InitializableProblemHandler.INFO.toString().equalsIgnoreCase(initializableProblemHandler)) {
            return InitializableProblemHandler.INFO;
        } else if (InitializableProblemHandler.WARN.toString().equalsIgnoreCase(initializableProblemHandler)) {
            return InitializableProblemHandler.WARN;
        } else if (InitializableProblemHandler.THROW.toString().equalsIgnoreCase(initializableProblemHandler)) {
            return InitializableProblemHandler.THROW;
        }
        throw new TikaConfigException(
                String.format(Locale.US, "Couldn't parse non-null '%s'. Must be one of 'ignore', 'info', 'warn' or 'throw'",
                        initializableProblemHandler));
    }


    private static abstract class XmlLoader<CT,T> {
        protected static final String PARAMS_TAG_NAME = "params";
        abstract boolean supportsComposite();
        abstract String getParentTagName(); // eg parsers
        abstract String getLoaderTagName(); // eg parser
        abstract Class<? extends T> getLoaderClass(); // Generics workaround
        abstract boolean isComposite(T loaded);
        abstract boolean isComposite(Class<? extends T> loadedClass);
        abstract T preLoadOne(Class<? extends T> loadedClass, String classname, 
                MimeTypes mimeTypes) throws TikaException;
        abstract CT createDefault(MimeTypes mimeTypes, ServiceLoader loader);
        abstract CT createComposite(List<T> loaded, MimeTypes mimeTypes, ServiceLoader loader);
        abstract T createComposite(Class<? extends T> compositeClass, 
                List<T> children, Set<Class<? extends T>> excludeChildren,
                Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException, InstantiationException;
        abstract T decorate(T created, Element element) 
                throws IOException, TikaException; // eg explicit mime types 
        
        @SuppressWarnings("unchecked")
        CT loadOverall(Element element, MimeTypes mimeTypes, 
                ServiceLoader loader) throws TikaException, IOException {
            List<T> loaded = new ArrayList<T>();
            
            // Find the children of the parent tag, if any
            for (Element le : getTopLevelElementChildren(element, getParentTagName(), getLoaderTagName())) {
                T loadedChild = loadOne(le, mimeTypes, loader);
                if (loadedChild != null) loaded.add(loadedChild);
            }
            
            // Build the classes, and wrap as needed
            if (loaded.isEmpty()) {
                // Nothing defined, create a Default
                return createDefault(mimeTypes, loader);
            } else if (loaded.size() == 1) {
                T single = loaded.get(0);
                if (isComposite(single)) {
                    // Single Composite defined, use that
                    return (CT)single;
                }
            } else if (! supportsComposite()) {
                // No composite support, just return the first one
                return (CT)loaded.get(0);
            }
            // Wrap the defined parsers/detectors up in a Composite
            return createComposite(loaded, mimeTypes, loader);
        }

        T loadOne(Element element, MimeTypes mimeTypes, ServiceLoader loader) 
                throws TikaException, IOException {
            String name = element.getAttribute("class");

            String initProbHandler = element.getAttribute("initializableProblemHandler");
            InitializableProblemHandler initializableProblemHandler;
            if (initProbHandler == null || initProbHandler.length() == 0) {
                initializableProblemHandler = loader.getInitializableProblemHandler();
            } else {
                 initializableProblemHandler =
                        getInitializableProblemHandler(initProbHandler);
            }

            T loaded = null;

            try {
                Class<? extends T> loadedClass =
                        loader.getServiceClass(getLoaderClass(), name);

                // Do pre-load checks and short-circuits
                //TODO : allow duplicate instances with different configurations
                loaded = preLoadOne(loadedClass, name, mimeTypes);
                if (loaded != null) return loaded;
                
                // Get any parameters / settings for the parser
                Map<String, Param> params = null;
                try {
                    params = getParams(element);
                } catch (Exception e) {
                    throw new TikaConfigException(e.getMessage(), e);
                }
                
                // Is this a composite or decorated class? If so, support recursion
                if (isComposite(loadedClass)) {
                    // Get the child objects for it
                    List<T> children = new ArrayList<T>();
                    NodeList childNodes = element.getElementsByTagName(getLoaderTagName());
                    if (childNodes.getLength() > 0) {
                        for (int i = 0; i < childNodes.getLength(); i++) {
                            T loadedChild = loadOne((Element)childNodes.item(i), 
                                                    mimeTypes, loader);
                            if (loadedChild != null) children.add(loadedChild);
                        }
                    }
                    
                    // Get the list of children to exclude
                    Set<Class<? extends T>> excludeChildren = new HashSet<Class<? extends T>>();
                    NodeList excludeChildNodes = element.getElementsByTagName(getLoaderTagName()+"-exclude");
                    if (excludeChildNodes.getLength() > 0) {
                        for (int i = 0; i < excludeChildNodes.getLength(); i++) {
                            Element excl = (Element)excludeChildNodes.item(i);
                            String exclName = excl.getAttribute("class");
                            excludeChildren.add(loader.getServiceClass(getLoaderClass(), exclName));
                        }
                    }
                    
                    // Create the Composite
                    loaded = createComposite(loadedClass, children, excludeChildren, params, mimeTypes, loader);

                    // Default constructor fallback
                    if (loaded == null) {
                        loaded = newInstance(loadedClass);
                    }
                } else {
                    // Regular class, create as-is
                    loaded = newInstance(loadedClass);
                    // TODO Support arguments, needed for Translators etc
                    // See the thread "Configuring parsers and translators" for details 
                }

                //Assigning the params to bean fields/setters
                AnnotationUtils.assignFieldParams(loaded, params);
                if (loaded instanceof Initializable) {
                    ((Initializable) loaded).initialize(params);
                    ((Initializable) loaded).checkInitialization(initializableProblemHandler);
                }
                // Have any decoration performed, eg explicit mimetypes
                loaded = decorate(loaded, element);
                // All done with setup
                return loaded;
            } catch (ClassNotFoundException e) {
                if (loader.getLoadErrorHandler() == LoadErrorHandler.THROW) {
                    // Use a different exception signature here
                    throw new TikaException(
                        "Unable to find a "+getLoaderTagName()+" class: " + name, e);
                }
                // Report the problem
                loader.getLoadErrorHandler().handleLoadError(name, e);
                return null;
            } catch (IllegalAccessException e) {
                throw new TikaException(
                        "Unable to access a "+getLoaderTagName()+" class: " + name, e);
            } catch (InvocationTargetException e) {
                throw new TikaException(
                        "Unable to create a "+getLoaderTagName()+" class: " + name, e);
            } catch (InstantiationException e) {
                throw new TikaException(
                        "Unable to instantiate a "+getLoaderTagName()+" class: " + name, e);
            } catch (NoSuchMethodException e) {
                throw new TikaException(
                        "Unable to find the right constructor for "+getLoaderTagName()+" class: " + name, e);
            }
        }


        T newInstance(Class<? extends T> loadedClass) throws
                IllegalAccessException, InstantiationException,
                NoSuchMethodException, InvocationTargetException {
            return loadedClass.newInstance();
        }

        /**
         * Gets parameters from a given
         * @param el xml node which has {@link #PARAMS_TAG_NAME} child
         * @return Map of key values read from xml
         */
        Map<String, Param>  getParams(Element el){
            Map<String, Param> params = new HashMap<>();
            for (Node child = el.getFirstChild(); child != null;
                 child = child.getNextSibling()){
                if (PARAMS_TAG_NAME.equals(child.getNodeName())){ //found the node
                    if (child.hasChildNodes()) {//it has children
                        NodeList childNodes = child.getChildNodes();
                        for (int i = 0; i < childNodes.getLength(); i++) {
                            Node item = childNodes.item(i);
                            if (item.getNodeType() == Node.ELEMENT_NODE){
                                Param<?> param = Param.load(item);
                                params.put(param.getName(), param);
                            }
                        }
                    }
                    break; //only the first one is used
                }
            }
            return params;
        }

    }
    private static class ParserXmlLoader extends XmlLoader<CompositeParser,Parser> {

        private final EncodingDetector encodingDetector;

        boolean supportsComposite() { return true; }
        String getParentTagName() { return "parsers"; }
        String getLoaderTagName() { return "parser"; }

        private ParserXmlLoader(EncodingDetector encodingDetector) {
            this.encodingDetector = encodingDetector;
        }
        @Override
        Class<? extends Parser> getLoaderClass() {
            return Parser.class;
        }
        @Override
        Parser preLoadOne(Class<? extends Parser> loadedClass, String classname, 
                          MimeTypes mimeTypes) throws TikaException {
            // Check for classes which can't be set in config
            if (AutoDetectParser.class.isAssignableFrom(loadedClass)) {
                // https://issues.apache.org/jira/browse/TIKA-866
                throw new TikaException(
                        "AutoDetectParser not supported in a <parser>"
                        + " configuration element: " + classname);
            }
            // Continue with normal loading
            return null;
        }
        @Override
        boolean isComposite(Parser loaded) {
            return loaded instanceof CompositeParser;
        }
        @Override
        boolean isComposite(Class<? extends Parser> loadedClass) {
            if (CompositeParser.class.isAssignableFrom(loadedClass) ||
                ParserDecorator.class.isAssignableFrom(loadedClass)) {
                return true;
            }
            return false;
        }
        @Override
        CompositeParser createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultParser(mimeTypes, loader, encodingDetector);
        }
        @Override
        CompositeParser createComposite(List<Parser> parsers, MimeTypes mimeTypes, ServiceLoader loader) {
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            return new CompositeParser(registry, parsers);
        }
        @Override
        Parser createComposite(Class<? extends Parser> parserClass,
                List<Parser> childParsers, Set<Class<? extends Parser>> excludeParsers,
                Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader) 
                throws InvocationTargetException, IllegalAccessException, InstantiationException {
            Parser parser = null;
            Constructor<? extends Parser> c = null;
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            
            // Try the possible default and composite parser constructors
            if (parser == null) {
                try {
                    c = parserClass.getConstructor(MediaTypeRegistry.class,
                            ServiceLoader.class, Collection.class, EncodingDetector.class);
                    parser = c.newInstance(registry, loader, excludeParsers, encodingDetector);
                }
                catch (NoSuchMethodException me) {}
            }
            if (parser == null) {
                try {
                    c = parserClass.getConstructor(MediaTypeRegistry.class, ServiceLoader.class, Collection.class);
                    parser = c.newInstance(registry, loader, excludeParsers);
                } 
                catch (NoSuchMethodException me) {}
            }
            if (parser == null) {
                try {
                    c = parserClass.getConstructor(MediaTypeRegistry.class, List.class, Collection.class);
                    parser = c.newInstance(registry, childParsers, excludeParsers);
                } catch (NoSuchMethodException me) {}
            }
            if (parser == null) {
                try {
                    c = parserClass.getConstructor(MediaTypeRegistry.class, Collection.class, Map.class);
                    parser = c.newInstance(registry, childParsers, params);
                } catch (NoSuchMethodException me) {}
            }
            if (parser == null) {
                try {
                    c = parserClass.getConstructor(MediaTypeRegistry.class, List.class);
                    parser = c.newInstance(registry, childParsers);
                } catch (NoSuchMethodException me) {}
            }
            
            // Create as a Parser Decorator
            if (parser == null && ParserDecorator.class.isAssignableFrom(parserClass)) {
                try {
                    CompositeParser cp = null;
                    if (childParsers.size() == 1 && excludeParsers.size() == 0 &&
                            childParsers.get(0) instanceof CompositeParser) {
                        cp = (CompositeParser)childParsers.get(0);
                    } else {
                        cp = new CompositeParser(registry, childParsers, excludeParsers);
                    }
                    c = parserClass.getConstructor(Parser.class);
                    parser = c.newInstance(cp);
                } catch (NoSuchMethodException me) {}
            }
            return parser;
        }

        @Override
        Parser newInstance(Class<? extends Parser> loadedClass) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
            if (AbstractEncodingDetectorParser.class.isAssignableFrom(loadedClass)) {
                Constructor ctor = loadedClass.getConstructor(EncodingDetector.class);
                return (Parser) ctor.newInstance(encodingDetector);
            } else {
                return loadedClass.newInstance();
            }
        }

        @Override
        Parser decorate(Parser created, Element element) throws IOException, TikaException {
            Parser parser = created;
            
            // Is there an explicit list of mime types for this to handle?
            Set<MediaType> parserTypes = mediaTypesListFromDomElement(element, "mime");
            if (! parserTypes.isEmpty()) {
                parser = ParserDecorator.withTypes(parser, parserTypes);
            }
            // Is there an explicit list of mime types this shouldn't handle?
            Set<MediaType> parserExclTypes = mediaTypesListFromDomElement(element, "mime-exclude");
            if (! parserExclTypes.isEmpty()) {
                parser = ParserDecorator.withoutTypes(parser, parserExclTypes);
            }
            
            // All done with decoration
            return parser;
        }

    }
    private static class DetectorXmlLoader extends XmlLoader<CompositeDetector,Detector> {
        boolean supportsComposite() { return true; }
        String getParentTagName() { return "detectors"; }
        String getLoaderTagName() { return "detector"; }
        
        @Override
        Class<? extends Detector> getLoaderClass() {
            return Detector.class;
        }
        @Override
        Detector preLoadOne(Class<? extends Detector> loadedClass, String classname, 
                            MimeTypes mimeTypes) throws TikaException {
            // If they asked for the mime types as a detector, give
            //  them the one we've already created. TIKA-1708
            if (MimeTypes.class.equals(loadedClass)) {
                return mimeTypes;
            }
            // Continue with normal loading
            return null;
        }
        @Override
        boolean isComposite(Detector loaded) {
            return loaded instanceof CompositeDetector;
        }
        @Override
        boolean isComposite(Class<? extends Detector> loadedClass) {
            return CompositeDetector.class.isAssignableFrom(loadedClass);
        }
        @Override
        CompositeDetector createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultDetector(mimeTypes, loader);
        }
        @Override
        CompositeDetector createComposite(List<Detector> detectors, MimeTypes mimeTypes, ServiceLoader loader) {
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            return new CompositeDetector(registry, detectors);
        }
        @Override
        Detector createComposite(Class<? extends Detector> detectorClass,
                List<Detector> childDetectors,
                Set<Class<? extends Detector>> excludeDetectors,
                Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException,
                InstantiationException {
            Detector detector = null;
            Constructor<? extends Detector> c;
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            
            // Try the possible default and composite detector constructors
            if (detector == null) {
                try {
                    c = detectorClass.getConstructor(MimeTypes.class, ServiceLoader.class, Collection.class);
                    detector = c.newInstance(mimeTypes, loader, excludeDetectors);
                } 
                catch (NoSuchMethodException me) {}
            }
            if (detector == null) {
                try {
                    c = detectorClass.getConstructor(MediaTypeRegistry.class, List.class, Collection.class);
                    detector = c.newInstance(registry, childDetectors, excludeDetectors);
                } catch (NoSuchMethodException me) {}
            }
            if (detector == null) {
                try {
                    c = detectorClass.getConstructor(MediaTypeRegistry.class, List.class);
                    detector = c.newInstance(registry, childDetectors);
                } catch (NoSuchMethodException me) {}
            }
            if (detector == null) {
                try {
                    c = detectorClass.getConstructor(List.class);
                    detector = c.newInstance(childDetectors);
                } catch (NoSuchMethodException me) {}
            }
            
            return detector;
        }
        @Override
        Detector decorate(Detector created, Element element) {
            return created; // No decoration of Detectors
        }
    }
    private static class TranslatorXmlLoader extends XmlLoader<Translator,Translator> {
        boolean supportsComposite() { return false; }
        String getParentTagName() { return null; }
        String getLoaderTagName() { return "translator"; }
        
        @Override
        Class<? extends Translator> getLoaderClass() {
            return Translator.class;
        }
        @Override
        Translator preLoadOne(Class<? extends Translator> loadedClass, String classname, 
                              MimeTypes mimeTypes) throws TikaException {
            // Continue with normal loading
            return null;
        }
        @Override
        boolean isComposite(Translator loaded) { return false; }
        @Override
        boolean isComposite(Class<? extends Translator> loadedClass) { return false; }
        @Override
        Translator createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultTranslator(loader);
        }
        @Override
        Translator createComposite(List<Translator> loaded,
                MimeTypes mimeTypes, ServiceLoader loader) {
            return loaded.get(0);
        }
        @Override
        Translator createComposite(Class<? extends Translator> compositeClass,
                List<Translator> children,
                Set<Class<? extends Translator>> excludeChildren,
                Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException,
                InstantiationException {
            throw new InstantiationException("Only one translator supported");
        }
        @Override
        Translator decorate(Translator created, Element element) {
            return created; // No decoration of Translators
        }        
    }
    
    private static class ExecutorServiceXmlLoader extends XmlLoader<ConfigurableThreadPoolExecutor,ConfigurableThreadPoolExecutor> {
        @Override
        ConfigurableThreadPoolExecutor createComposite(
                Class<? extends ConfigurableThreadPoolExecutor> compositeClass,
                List<ConfigurableThreadPoolExecutor> children,
                Set<Class<? extends ConfigurableThreadPoolExecutor>> excludeChildren,
                Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException,
                InstantiationException {
            throw new InstantiationException("Only one executor service supported");
        }
        
        @Override
        ConfigurableThreadPoolExecutor createComposite(List<ConfigurableThreadPoolExecutor> loaded,
                MimeTypes mimeTypes, ServiceLoader loader) {
            return loaded.get(0);
        }
        
        @Override
        ConfigurableThreadPoolExecutor createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultExecutorService();
        }
        
        @Override
        ConfigurableThreadPoolExecutor decorate(ConfigurableThreadPoolExecutor created, Element element)
                throws IOException, TikaException {
            
            Element maxThreadElement = getChild(element, "max-threads");
            if(maxThreadElement != null)
            {
                created.setMaximumPoolSize(Integer.parseInt(getText(maxThreadElement)));
            }
            
            Element coreThreadElement = getChild(element, "core-threads");
            if(coreThreadElement != null)
            {
                created.setCorePoolSize(Integer.parseInt(getText(coreThreadElement)));
            }
            return created;
        }
        
        @Override
        Class<? extends ConfigurableThreadPoolExecutor> getLoaderClass() {
            return ConfigurableThreadPoolExecutor.class;
        }
        
        @Override
        ConfigurableThreadPoolExecutor loadOne(Element element, MimeTypes mimeTypes,
                ServiceLoader loader) throws TikaException, IOException {
            return super.loadOne(element, mimeTypes, loader);
        }

        @Override
        boolean supportsComposite() {return false;}

        @Override
        String getParentTagName() {return null;}

        @Override
        String getLoaderTagName() {return "executor-service";}

        @Override
        boolean isComposite(ConfigurableThreadPoolExecutor loaded) {return false;}

        @Override
        boolean isComposite(Class<? extends ConfigurableThreadPoolExecutor> loadedClass) {return false;}

        @Override
        ConfigurableThreadPoolExecutor preLoadOne(
                Class<? extends ConfigurableThreadPoolExecutor> loadedClass, String classname,
                MimeTypes mimeTypes) throws TikaException {
            return null;
        }
    }

    private static class EncodingDetectorXmlLoader extends
            XmlLoader<EncodingDetector, EncodingDetector> {

        boolean supportsComposite() {
            return true;
        }

        String getParentTagName() {
            return "encodingDetectors";
        }

        String getLoaderTagName() {
            return "encodingDetector";
        }

        @Override
        Class<? extends EncodingDetector> getLoaderClass() {
            return EncodingDetector.class;
        }


        @Override
        boolean isComposite(EncodingDetector loaded) {
            return loaded instanceof CompositeEncodingDetector;
        }

        @Override
        boolean isComposite(Class<? extends EncodingDetector> loadedClass) {
            return CompositeEncodingDetector.class.isAssignableFrom(loadedClass);
        }

        @Override
        EncodingDetector preLoadOne(Class<? extends EncodingDetector> loadedClass,
                                    String classname, MimeTypes mimeTypes) throws TikaException {
            // Check for classes which can't be set in config
            // Continue with normal loading
            return null;
        }

        @Override
        EncodingDetector createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultEncodingDetector(loader);
        }

        @Override
        CompositeEncodingDetector createComposite(List<EncodingDetector> encodingDetectors,
                                                  MimeTypes mimeTypes, ServiceLoader loader) {
            return new CompositeEncodingDetector(encodingDetectors);
        }

        @Override
        EncodingDetector createComposite(Class<? extends EncodingDetector> encodingDetectorClass,
                                         List<EncodingDetector> childEncodingDetectors,
                                         Set<Class<? extends EncodingDetector>> excludeDetectors,
                                         Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException,
                InstantiationException {
            EncodingDetector encodingDetector = null;
            Constructor<? extends EncodingDetector> c;

            // Try the possible default and composite detector constructors
            if (encodingDetector == null) {
                try {
                    c = encodingDetectorClass.getConstructor(ServiceLoader.class, Collection.class);
                    encodingDetector = c.newInstance(loader, excludeDetectors);
                } catch (NoSuchMethodException me) {
                    me.printStackTrace();
                }
            }
            if (encodingDetector == null) {
                try {
                    c = encodingDetectorClass.getConstructor(List.class);
                    encodingDetector = c.newInstance(childEncodingDetectors);
                } catch (NoSuchMethodException me) {
                    me.printStackTrace();
                }
            }

            return encodingDetector;
        }

        @Override
        EncodingDetector decorate(EncodingDetector created, Element element) {
            return created; // No decoration of EncodingDetectors
        }
    }

    private static class MetadataFilterXmlLoader extends
            XmlLoader<MetadataFilter, MetadataFilter> {

        boolean supportsComposite() {
            return true;
        }

        String getParentTagName() {
            return "metadataFilters";
        }

        String getLoaderTagName() {
            return "metadataFilter";
        }

        @Override
        Class<? extends MetadataFilter> getLoaderClass() {
            return MetadataFilter.class;
        }


        @Override
        boolean isComposite(MetadataFilter loaded) {
            return loaded instanceof CompositeMetadataFilter;
        }

        @Override
        boolean isComposite(Class<? extends MetadataFilter> loadedClass) {
            return CompositeMetadataFilter.class.isAssignableFrom(loadedClass);
        }

        @Override
        MetadataFilter preLoadOne(Class<? extends MetadataFilter> loadedClass,
                                    String classname, MimeTypes mimeTypes) throws TikaException {
            // Check for classes which can't be set in config
            // Continue with normal loading
            return null;
        }

        @Override
        MetadataFilter createDefault(MimeTypes mimeTypes, ServiceLoader loader) {
            return getDefaultMetadataFilter(loader);
        }

        //this ignores the service loader
        @Override
        MetadataFilter createComposite(List<MetadataFilter> loaded, MimeTypes mimeTypes, ServiceLoader loader) {
            return new DefaultMetadataFilter(loaded);
        }

        @Override
        MetadataFilter createComposite(Class<? extends MetadataFilter> metadataFilterClass,
                                         List<MetadataFilter> childMetadataFilters,
                                         Set<Class<? extends MetadataFilter>> excludeFilters,
                                         Map<String, Param> params, MimeTypes mimeTypes, ServiceLoader loader)
                throws InvocationTargetException, IllegalAccessException,
                InstantiationException {
            MetadataFilter metadataFilter = null;
            Constructor<? extends MetadataFilter> c;

            // Try the possible default and composite detector constructors
            if (metadataFilter == null) {
                try {
                    c = metadataFilterClass.getConstructor(ServiceLoader.class, Collection.class);
                    metadataFilter = c.newInstance(loader, excludeFilters);
                } catch (NoSuchMethodException me) {
                    me.printStackTrace();
                }
            }
            if (metadataFilter == null) {
                try {
                    c = metadataFilterClass.getConstructor(List.class);
                    metadataFilter = c.newInstance(childMetadataFilters);
                } catch (NoSuchMethodException me) {
                    me.printStackTrace();
                }
            }

            return metadataFilter;
        }

        @Override
        MetadataFilter decorate(MetadataFilter created, Element element) {
            return created; // No decoration of MetadataFilters
        }
    }

}
