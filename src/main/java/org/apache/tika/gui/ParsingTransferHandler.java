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

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

public class ParsingTransferHandler extends TransferHandler {

    private final Parser parser = new AutoDetectParser();

    private final TransferHandler delegate;

    private final DefaultTableModel table;

    private final JEditorPane editor;

    public ParsingTransferHandler(
            TransferHandler delegate,
            DefaultTableModel table, JEditorPane editor) {
        this.delegate = delegate;
        this.table = table;
        this.editor = editor;
    }

    public boolean canImport(TransferSupport support) {
        return canImport(null, support.getDataFlavors());
    }

    public boolean canImport(JComponent component, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                return true;
            }
        }
        return false;
    }

    public boolean importData(TransferSupport support) {
        return importData(null, support.getTransferable());
    }

    public boolean importData(
            JComponent component, Transferable transferable) {
        try {
            List<?> files = (List<?>)
                transferable.getTransferData(DataFlavor.javaFileListFlavor);
            for (Object file : files) {
                importFile((File) file);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void importFile(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        try {
            StringWriter writer = new StringWriter();
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());

            SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
            TransformerHandler handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(
                    OutputKeys.METHOD, "html");
            handler.setResult(new StreamResult(writer));
            parser.parse(input, handler, metadata);

            table.setRowCount(0);
            for (String name : metadata.names()) {
                table.addRow(new Object[] { name, metadata.get(name) });
            }
            editor.setText(writer.toString());
        } finally {
            input.close();
        }
    }

    public void exportAsDrag(JComponent arg0, InputEvent arg1, int arg2) {
        delegate.exportAsDrag(arg0, arg1, arg2);
    }

    public void exportToClipboard(JComponent arg0, Clipboard arg1, int arg2)
            throws IllegalStateException {
        delegate.exportToClipboard(arg0, arg1, arg2);
    }

    public int getSourceActions(JComponent arg0) {
        return delegate.getSourceActions(arg0);
    }

    public Icon getVisualRepresentation(Transferable arg0) {
        return delegate.getVisualRepresentation(arg0);
    }

}