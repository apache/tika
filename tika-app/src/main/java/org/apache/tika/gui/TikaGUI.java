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
package org.apache.tika.gui;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.lucene.DocumentIndexer;
import org.apache.tika.lucene.FoundItem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.utils.CommonsDigester;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Simple Swing GUI for Apache Tika. You can drag and drop files on top
 * of the window to have them parsed.
 */
public class TikaGUI extends JFrame
        implements ActionListener, HyperlinkListener {

    //maximum length to allow for mark for reparse to get JSON
    private static final int MAX_MARK = 20*1024*1024;//20MB

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 5883906936187059495L;

    /**
     * Main method. Sets the Swing look and feel to the operating system
     * settings, and starts the Tika GUI with an {@link AutoDetectParser}
     * instance as the default parser.
     *
     * @param args ignored
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        if (args.length > 0) {
            File configFile = new File(args[0]);
            config = new TikaConfig(configFile);
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        final TikaConfig finalConfig = config;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TikaGUI(new DigestingParser(
                        new AutoDetectParser(finalConfig),
                        new CommonsDigester(MAX_MARK,
                                CommonsDigester.DigestAlgorithm.MD5,
                                CommonsDigester.DigestAlgorithm.SHA256)
                        )).setVisible(true);
            }
        });
    }

    /**
     * Parsing context.
     */
    private final ParseContext context;

    /**
     * Configured parser instance.
     */
    private final Parser parser;
    
    /**
     * Captures requested embedded images
     */
    private final ImageSavingParser imageParser;

    /**
     * Document indexer
     */
    private DocumentIndexer docIndexer;

    /**
     * Container for the editor tabs.
     */
    private final JTabbedPane tabs;

    /**
     * Tabs definitions.
     */
    private enum TabDef {
        WELCOME("Welcome", ""),
        METADATA("Metadata", "text/plain"),
        XHTML("Formatted XHTML", ""),
        TEXT("Plain text", "text/plain"),
        TEXT_MAIN("Main content", "text/plain"),
        XML("Structured XML", "text/plain"),
        JSON("Recursive JSON", "text/plain"),
        LUCENE("Lucene", ""),
        ;

        String title, content;
        TabDef(String title, String content) {
            this.title=title;
            this.content=content;
        }
    }

    /**
     * Formatted XHTML output.
     */
    private final JFXPanel xhtml;

    /**
     * Plain text output.
     */
    private final JEditorPane text;

    /**
     * Main content output.
     */
    private final JEditorPane textMain;
    
    /**
     * Raw XHTML source.
     */
    private final JEditorPane xml;

    /**
     * Raw JSON source.
     */
    private final JEditorPane json;

    /**
     * Document metadata.
     */
    private final JEditorPane metadata;

    /**
     * Index queries.
     */
    private JEditorPane queryEditor;
    private JEditorPane resultEditor;

    /**
     * File chooser.
     */
    private final JFileChooser chooser = new JFileChooser();

    public TikaGUI(Parser parser) {
        super("Apache Tika");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addMenuBar();

        tabs = new JTabbedPane();
        addWelcomeTab(tabs, TabDef.WELCOME);
        metadata = addTab(tabs, TabDef.METADATA);
        xhtml = addWebViewTab(tabs, TabDef.XHTML);
        text = addTab(tabs, TabDef.TEXT);
        textMain = addTab(tabs, TabDef.TEXT_MAIN);
        xml = addTab(tabs, TabDef.XML);
        json = addTab(tabs, TabDef.JSON);
        addDocIndexerTab(tabs, TabDef.LUCENE);
        add(tabs);

        setPreferredSize(new Dimension(640, 480));
        pack();

        this.context = new ParseContext();
        this.parser = parser;
        try {
            this.docIndexer = new DocumentIndexer();
        } catch (IOException e) {
            this.docIndexer = null;
            e.printStackTrace();
        }

        this.imageParser = new ImageSavingParser(parser);
        this.context.set(DocumentSelector.class, new ImageDocumentSelector());
        this.context.set(Parser.class, imageParser);
    }

    private void addMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        addMenuItem(file, "Open...", "openfile", KeyEvent.VK_O);
        addMenuItem(file, "Open URL...", "openurl", KeyEvent.VK_U);
        file.addSeparator();
        addMenuItem(file, "Exit", "exit", KeyEvent.VK_X);
        bar.add(file);

        bar.add(Box.createHorizontalGlue());
        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        addMenuItem(help, "About Tika", "about", KeyEvent.VK_A);
        bar.add(help);

        setJMenuBar(bar);
    }

    private void addMenuItem(
            JMenu menu, String title, String command, int key) {
        JMenuItem item = new JMenuItem(title, key);
        item.setActionCommand(command);
        item.addActionListener(this);
        menu.add(item);
    }

    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if ("openfile".equals(command)) {
            int rv = chooser.showOpenDialog(this);
            if (rv == JFileChooser.APPROVE_OPTION) {
                openFile(chooser.getSelectedFile());
            }
        } else if ("openurl".equals(command)) {
            Object rv = JOptionPane.showInputDialog(
                    this, "Enter the URL of the resource to be parsed:",
                    "Open URL", JOptionPane.PLAIN_MESSAGE,
                    null, null, "");
            if (rv != null && rv.toString().length() > 0) {
                try {
                    openURL(new URL(rv.toString().trim()));
                } catch (MalformedURLException exception) {
                    JOptionPane.showMessageDialog(
                            this, "The given string is not a valid URL",
                            "Invalid URL", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if ("query".equals(command)) {
            executeQuery();
        } else if ("about".equals(command)) {
            textDialog(
                    "About Apache Tika",
                    TikaGUI.class.getResource("about.html"));
        } else if ("exit".equals(command)) {
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                    new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        }
    }

    public void openFile(File file) {
        try {
            Metadata metadata = new Metadata();
            try (TikaInputStream stream = TikaInputStream.get(file, metadata)) {
                handleStream(stream, metadata);
            }
        } catch (Throwable t) {
            handleError(file.getPath(), t);
        }
    }

    public void openURL(URL url) {
        try {
            Metadata metadata = new Metadata();
            try (TikaInputStream stream = TikaInputStream.get(url, metadata)) {
                handleStream(stream, metadata);
            }
        } catch (Throwable t) {
            handleError(url.toString(), t);
        }
    }

    private void handleStream(InputStream input, Metadata md)
            throws Exception {
        StringWriter htmlBuffer = new StringWriter();
        StringWriter textBuffer = new StringWriter();
        StringWriter textMainBuffer = new StringWriter();
        StringWriter xmlBuffer = new StringWriter();
        StringBuilder metadataBuffer = new StringBuilder();

        ContentHandler handler = new TeeContentHandler(
                getHtmlHandler(htmlBuffer),
                getTextContentHandler(textBuffer),
                getTextMainContentHandler(textMainBuffer),
                getXmlContentHandler(xmlBuffer));

        context.set(DocumentSelector.class, new ImageDocumentSelector());

        input = TikaInputStream.get(new ProgressMonitorInputStream(
                this, "Parsing stream", input));

        if (input.markSupported()) {
            int mark = -1;
            if (input instanceof TikaInputStream) {
                if (((TikaInputStream)input).hasFile()) {
                    mark = (int)((TikaInputStream)input).getLength();
                }
            }
            if (mark == -1) {
                mark = MAX_MARK;
            }
            input.mark(mark);
        }
        parser.parse(input, handler, md, context);

        String[] names = md.names();
        Arrays.sort(names);
        for (String name : names) {
            for (String val : md.getValues(name)) {
                metadataBuffer.append(name);
                metadataBuffer.append(": ");
                metadataBuffer.append(val);
                metadataBuffer.append("\n");
            }
        }

        String name = md.get(Metadata.RESOURCE_NAME_KEY);
        if (name != null && name.length() > 0) {
            setTitle("Apache Tika: " + name);
        } else {
            setTitle("Apache Tika: unnamed document");
        }

        setText(metadata, metadataBuffer.toString());
        setText(xml, xmlBuffer.toString());
        setText(text, textBuffer.toString());
        setText(textMain, textMainBuffer.toString());
        setHtml(xhtml, htmlBuffer.toString());
        addDocumentToIndex(name,htmlBuffer.toString());
        if (!input.markSupported()) {
            setText(json, "InputStream does not support mark/reset for Recursive Parsing");
            selectTab(TabDef.JSON);
            return;
        }
        boolean isReset = false;
        try {
            input.reset();
            isReset = true;
        } catch (IOException e) {
            setText(json, "Error during stream reset.\n"+
                    "There's a limit of "+MAX_MARK + " bytes for this type of processing in the GUI.\n"+
                    "Try the app with command line argument of -J."
            );
        }
        if (isReset) {
            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser,
                    new BasicContentHandlerFactory(
                            BasicContentHandlerFactory.HANDLER_TYPE.BODY, -1));
            wrapper.parse(input, null, new Metadata(), new ParseContext());
            StringWriter jsonBuffer = new StringWriter();
            JsonMetadataList.setPrettyPrinting(true);
            JsonMetadataList.toJson(wrapper.getMetadata(), jsonBuffer);
            setText(json, jsonBuffer.toString());
        }
        selectTab(TabDef.XHTML);
    }

    private void handleError(String name, Throwable t) {
        StringWriter writer = new StringWriter();
        writer.append("Apache Tika was unable to parse the document\n");
        writer.append("at " + name + ".\n\n");
        writer.append("The full exception stack trace is included below:\n\n");
        t.printStackTrace(new PrintWriter(writer));

        JEditorPane editor =
            new JEditorPane("text/plain", writer.toString());
        editor.setEditable(false);
        editor.setBackground(Color.WHITE);
        editor.setCaretPosition(0);
        editor.setPreferredSize(new Dimension(600, 400));

        JDialog dialog = new JDialog(this, "Apache Tika error");
        dialog.add(new JScrollPane(editor));
        dialog.pack();
        dialog.setVisible(true);
    }

    private void addWelcomeTab(JTabbedPane panel, TabDef tabDef) {
        try {
            JEditorPane editor =
                new JEditorPane(TikaGUI.class.getResource("welcome.html"));
            editor.setContentType("text/html");
            editor.setEditable(false);
            editor.setBackground(Color.WHITE);
            editor.setTransferHandler(new ParsingTransferHandler(
                    editor.getTransferHandler(), this));
            panel.addTab(tabDef.title, new JScrollPane(editor));
            selectTab(tabDef);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JEditorPane addTab(JTabbedPane panel, TabDef tabDef) {
        JEditorPane editor = new JTextPane();
        editor.setBackground(Color.WHITE);
        editor.setContentType(tabDef.content);
        editor.setTransferHandler(new ParsingTransferHandler(
                editor.getTransferHandler(), this));
        panel.addTab(tabDef.title, new JScrollPane(editor));
        return editor;
    }

    private JFXPanel addWebViewTab(final JTabbedPane panel, final TabDef tabDef){
        final JFXPanel jfxPanel = new JFXPanel();
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                WebView webView = new WebView();
                jfxPanel.setScene( new Scene( webView ) );
                panel.addTab(tabDef.title,jfxPanel);
            }
        });
        return jfxPanel;
    }

    private JPanel addDocIndexerTab(JTabbedPane tabs, TabDef tabDef) {
        JPanel docIndexerPanel = new JPanel();
        docIndexerPanel.setLayout(new BoxLayout(docIndexerPanel, BoxLayout.PAGE_AXIS));

        this.queryEditor = new JTextPane();
        this.queryEditor.setText("contents:\"*01*\"");
        JScrollPane queryScroller = new JScrollPane(queryEditor);
        queryScroller.setPreferredSize(new Dimension(tabs.getWidth(), 50));
        queryScroller.setAlignmentX(LEFT_ALIGNMENT);

        //Lay out the buttons from left to right.
        final JButton queryButton = new JButton("Query");
        queryButton.setActionCommand("query");
        queryButton.addActionListener(this);
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.PAGE_AXIS));
        buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        buttonPane.add(Box.createHorizontalGlue());
        buttonPane.add(queryButton);

        this.resultEditor = new JTextPane();
        JScrollPane resultsScroller = new JScrollPane(resultEditor);
        resultsScroller.setPreferredSize(new Dimension(tabs.getWidth(), 280));
        resultsScroller.setAlignmentX(LEFT_ALIGNMENT);

        //Put everything together, using the content pane's BorderLayout.
        docIndexerPanel.add(queryScroller);
        docIndexerPanel.add(buttonPane);
        docIndexerPanel.add(resultsScroller);
        tabs.add(tabDef.title,docIndexerPanel);
        return docIndexerPanel;
    }

    private void addDocumentToIndex(String filename, String contents) {
        try {
            docIndexer.addDocument(filename, contents);
        } catch (IOException e) {
            this.resultEditor.setText(e.getMessage());
            e.printStackTrace();
        }
    }
    private void executeQuery() {
        try {
            List<FoundItem> fis = docIndexer.searchDocuments(this.queryEditor.getText());
            StringBuffer res = new StringBuffer();
            for (FoundItem fi : fis) {
                res.append(fi.getScoreDoc().toString()+"\n  - "+fi.getDocument().toString()+"\n");
            }
            this.resultEditor.setText(res.toString());
        } catch (ParseException e) {
            this.resultEditor.setText(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            this.resultEditor.setText(e.getMessage());
            e.printStackTrace();
        }
    }
    private void textDialog(String title, URL resource) {
        try {
            JDialog dialog = new JDialog(this, title);
            JEditorPane editor = new JEditorPane(resource);
            editor.setContentType("text/html");
            editor.setEditable(false);
            editor.setBackground(Color.WHITE);
            editor.setPreferredSize(new Dimension(400, 250));
            editor.addHyperlinkListener(this);
            dialog.add(editor);
            dialog.pack();
            dialog.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == EventType.ACTIVATED) {
            try {
                URL url = e.getURL();
                try (InputStream stream = url.openStream()) {
                    JEditorPane editor =
                        new JEditorPane("text/plain", IOUtils.toString(stream, UTF_8));
                    editor.setEditable(false);
                    editor.setBackground(Color.WHITE);
                    editor.setCaretPosition(0);
                    editor.setPreferredSize(new Dimension(600, 400));

                    String name = url.toString();
                    name = name.substring(name.lastIndexOf('/') + 1);

                    JDialog dialog = new JDialog(this, "Apache Tika: " + name);
                    dialog.add(new JScrollPane(editor));
                    dialog.pack();
                    dialog.setVisible(true);
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    private void setText(JEditorPane editor, String text) {
        editor.setText(text);
        editor.setCaretPosition(0);
    }

    private void setHtml(final JFXPanel htmlPanel, final String html) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                WebView webView = (WebView)htmlPanel.getScene().getRoot();
                WebEngine engine = webView.getEngine();
                //webView.getEngine().setUserStyleSheetLocation();
                engine.loadContent(html);
            }
        });
    }

    private void selectTab(TabDef tabDef){
        tabs.setSelectedIndex(tabs.indexOfTab(tabDef.title));

    }

    /**
     * Creates and returns a content handler that turns XHTML input to
     * simplified HTML output that can be correctly parsed and displayed
     * by {@link JEditorPane}.
     * <p>
     * The returned content handler is set to output <code>html</code>
     * to the given writer. The XHTML namespace is removed from the output
     * to prevent the serializer from using the &lt;tag/&gt; empty element
     * syntax that causes extra "&gt;" characters to be displayed.
     * The &lt;head&gt; tags are dropped to prevent the serializer from
     * generating a &lt;META&gt; content type tag that makes
     * {@link JEditorPane} fail thinking that the document character set
     * is inconsistent.
     * <p>
     * Additionally, it will use ImageSavingParser to re-write embedded:(image) 
     * image links to be file:///(temporary file) so that they can be loaded.
     *
     * @param writer output writer
     * @return HTML content handler
     * @throws TransformerConfigurationException if an error occurs
     */
    private ContentHandler getHtmlHandler(Writer writer)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
            SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
        handler.setResult(new StreamResult(writer));
        return new ContentHandlerDecorator(handler) {
            @Override
            public void startElement(
                    String uri, String localName, String name, Attributes atts)
                    throws SAXException {
                if (XHTMLContentHandler.XHTML.equals(uri)) {
                    uri = null;
                }
                if (!"head".equals(localName)) {
                    if("img".equals(localName)) {
                       AttributesImpl newAttrs;
                       if(atts instanceof AttributesImpl) {
                          newAttrs = (AttributesImpl)atts;
                       } else {
                          newAttrs = new AttributesImpl(atts);
                       }
                       
                       for(int i=0; i<newAttrs.getLength(); i++) {
                          if("src".equals(newAttrs.getLocalName(i))) {
                             String src = newAttrs.getValue(i);
                             if(src.startsWith("embedded:")) {
                                String filename = src.substring(src.indexOf(':')+1);
                                try {
                                   File img = imageParser.requestSave(filename);
                                   String newSrc = img.toURI().toString();
                                   newAttrs.setValue(i, newSrc);
                                } catch(IOException e) {
                                   System.err.println("Error creating temp image file " + filename);
                                   // The html viewer will show a broken image too to alert them
                                }
                             }
                          }
                       }
                       super.startElement(uri, localName, name, newAttrs);
                    } else {
                       super.startElement(uri, localName, name, atts);
                    }
                }
            }
            @Override
            public void endElement(String uri, String localName, String name)
                    throws SAXException {
                if (XHTMLContentHandler.XHTML.equals(uri)) {
                    uri = null;
                }
                if (!"head".equals(localName)) {
                    super.endElement(uri, localName, name);
                }
            }
            @Override
            public void startPrefixMapping(String prefix, String uri) {
            }
            @Override
            public void endPrefixMapping(String prefix) {
            }
        };
    }

    private ContentHandler getTextContentHandler(Writer writer) {
        return new BodyContentHandler(writer);
    }
    private ContentHandler getTextMainContentHandler(Writer writer) {
        return new BoilerpipeContentHandler(writer);
    }

    private ContentHandler getXmlContentHandler(Writer writer)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
            SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.setResult(new StreamResult(writer));
        return handler;
    }

    /**
     * A {@link DocumentSelector} that accepts only images.
     */
    private static class ImageDocumentSelector implements DocumentSelector {
      public boolean select(Metadata metadata) {
         String type = metadata.get(Metadata.CONTENT_TYPE);
         return type != null && type.startsWith("image/");
      }
    }
    
    /**
     * A recursive parser that saves certain images into the temporary
     *  directory, and delegates everything else to another downstream
     *  parser.
     */
    private static class ImageSavingParser extends AbstractParser {
      private Map<String,File> wanted = new HashMap<String,File>();
      private Parser downstreamParser;
      private File tmpDir;
      
      private ImageSavingParser(Parser downstreamParser) {
         this.downstreamParser = downstreamParser;
         
         try {
            File t = File.createTempFile("tika", ".test");
            tmpDir = t.getParentFile();
         } catch(IOException e) {}
      }
      
      public File requestSave(String embeddedName) throws IOException {
         String suffix = ".tika";
         
         int splitAt = embeddedName.lastIndexOf('.');
         if (splitAt > 0) {
            embeddedName.substring(splitAt);
         }
         
         File tmp = File.createTempFile("tika-embedded-", suffix);
         wanted.put(embeddedName, tmp);
         return tmp;
      }
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
         // Never used in an auto setup
         return null;
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
         String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
         if(name != null && wanted.containsKey(name)) {
            FileOutputStream out = new FileOutputStream(wanted.get(name));
            IOUtils.copy(stream, out);
            out.close();
         } else {
            if(downstreamParser != null) {
               downstreamParser.parse(stream, handler, metadata, context);
            }
         }
      }

    }

}
