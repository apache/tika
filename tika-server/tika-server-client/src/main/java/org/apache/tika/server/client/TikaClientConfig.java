package org.apache.tika.server.client;

import org.apache.tika.config.Param;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.pipes.fetchiterator.EmptyFetchIterator;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TikaClientConfig extends TikaConfig {
    public TikaClientConfig(String file) throws TikaException, IOException, SAXException {
        super(file);
    }

    public TikaClientConfig(Path path) throws TikaException, IOException, SAXException {
        super(path);
    }

    public TikaClientConfig(Path path, ServiceLoader loader) throws TikaException, IOException, SAXException {
        super(path, loader);
    }

    public TikaClientConfig(File file) throws TikaException, IOException, SAXException {
        super(file);
    }

    public TikaClientConfig(File file, ServiceLoader loader) throws TikaException, IOException, SAXException {
        super(file, loader);
    }

    public TikaClientConfig(URL url) throws TikaException, IOException, SAXException {
        super(url);
    }

    public TikaClientConfig(URL url, ClassLoader loader) throws TikaException, IOException, SAXException {
        super(url, loader);
    }

    public TikaClientConfig(URL url, ServiceLoader loader) throws TikaException, IOException, SAXException {
        super(url, loader);
    }

    public TikaClientConfig(InputStream stream) throws TikaException, IOException, SAXException {
        super(stream);
    }

    public TikaClientConfig(Document document) throws TikaException, IOException {
        super(document);
    }

    public TikaClientConfig(Document document, ServiceLoader loader) throws TikaException, IOException {
        super(document, loader);
    }

    public TikaClientConfig(Element element) throws TikaException, IOException {
        super(element);
    }

    public TikaClientConfig(Element element, ClassLoader loader) throws TikaException, IOException {
        super(element, loader);
    }

    public TikaClientConfig(ClassLoader loader) throws MimeTypeException, IOException {
        super(loader);
    }

    public TikaClientConfig() throws TikaException, IOException {
    }

}
