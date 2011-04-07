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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ProgressMonitorInputStream;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Simple Swing GUI for Apache Tika. You can drag and drop files on top
 * of the window to have them parsed.
 */
public class TikaGUI extends JFrame implements ActionListener {

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
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TikaGUI(new AutoDetectParser()).setVisible(true);
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
     * The card layout for switching between different views.
     */
    private final CardLayout layout = new CardLayout();

    /**
     * Container for the editor cards.
     */
    private final JPanel cards;

    /**
     * Formatted XHTML output.
     */
    private final JEditorPane html;

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
     * Document metadata.
     */
    private final JEditorPane metadata;

    /**
     * Parsing errors.
     */
    private final JEditorPane errors;

    /**
     * File chooser.
     */
    private final JFileChooser chooser = new JFileChooser();

    public TikaGUI(Parser parser) {
        super("Apache Tika");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addMenuBar();

        cards = new JPanel(layout);
        html = addCard(cards, "text/html", "html");
        text = addCard(cards, "text/plain", "text");
        textMain = addCard(cards, "text/plain", "main");
        xml = addCard(cards, "text/plain", "xhtml");
        metadata = addCard(cards, "text/plain", "metadata");
        errors = addCard(cards, "text/plain", "error");
        add(cards);
        layout.show(cards, "html");

        setPreferredSize(new Dimension(640, 480));
        pack();

        this.context = new ParseContext();
        this.parser = parser;

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

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        ButtonGroup group = new ButtonGroup();
        addRadioItem(view, group, true, "Formatted text", "html", KeyEvent.VK_F);
        addRadioItem(view, group, false, "Plain text", "text", KeyEvent.VK_P);
        addRadioItem(view, group, false, "Main content", "main", KeyEvent.VK_C);
        addRadioItem(view, group, false, "Structured text", "xhtml", KeyEvent.VK_S);
        addRadioItem(view, group, false, "Metadata", "metadata", KeyEvent.VK_M);
        addRadioItem(view, group, false, "Errors", "error", KeyEvent.VK_E);
        bar.add(view);

        bar.add(Box.createHorizontalGlue());
        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
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

    private void addRadioItem(
            JMenu menu, ButtonGroup group, boolean selected,
            String title, String command, int key) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(title, selected);
        item.setMnemonic(key);
        item.setActionCommand(command);
        item.addActionListener(this);
        group.add(item);
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
        } else if ("html".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("text".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("main".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("xhtml".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("metadata".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("error".equals(command)) {
            layout.show(cards, command);
            ((JRadioButtonMenuItem) e.getSource()).setSelected(true);
        } else if ("exit".equals(command)) {
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                    new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        }
    }

    public void openFile(File file) {
        try {
            Metadata metadata = new Metadata();
            TikaInputStream stream = TikaInputStream.get(file, metadata);
            try {
                handleStream(stream, metadata);
            } finally {
                stream.close();
            }
        } catch (Throwable t) {
            handleError(t);
        }
    }

    public void openURL(URL url) {
        try {
            Metadata metadata = new Metadata();
            TikaInputStream stream = TikaInputStream.get(url, metadata);
            try {
                handleStream(stream, metadata);
            } finally {
                stream.close();
            }
        } catch (Throwable t) {
            handleError(t);
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

        input = new ProgressMonitorInputStream(
                this, "Parsing stream", input);
        parser.parse(input, handler, md, context);

        String[] names = md.names();
        Arrays.sort(names);
        for (String name : names) {
            metadataBuffer.append(name);
            metadataBuffer.append(": ");
            metadataBuffer.append(md.get(name));
            metadataBuffer.append("\n");
        }

        setText(errors, "");
        setText(metadata, metadataBuffer.toString());
        setText(xml, xmlBuffer.toString());
        setText(text, textBuffer.toString());
        setText(textMain, textMainBuffer.toString());
        setText(html, htmlBuffer.toString());
    }

    private void handleError(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        setText(errors, writer.toString());
        setText(metadata, "");
        setText(xml, "");
        setText(text, "");
        setText(html, "");
        JOptionPane.showMessageDialog(
                this,
                "Apache Tika was unable to parse this document.\n "
                + " See the errors tab for the details of this error.",
                "Parse error",
                JOptionPane.ERROR_MESSAGE);
    }

    private JEditorPane addCard(JPanel panel, String type, String name) {
        JEditorPane editor = new JEditorPane();
        editor.setBackground(Color.WHITE);
        editor.setContentType(type);
        editor.setTransferHandler(new ParsingTransferHandler(
                editor.getTransferHandler(), this));
        panel.add(new JScrollPane(editor), name);
        return editor;
    }

    private void setText(JEditorPane editor, String text) {
        editor.setText(text);
        editor.setCaretPosition(0);
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
         String suffix = embeddedName.substring(embeddedName.lastIndexOf('.'));
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
