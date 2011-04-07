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
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/**
 * Utility class that turns drag-and-drop events into Tika parse requests.
 */
class ParsingTransferHandler extends TransferHandler {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -557932290014044494L;

    private final TransferHandler delegate;

    private final TikaGUI tika;

    private static DataFlavor uriListFlavor;
    private static DataFlavor urlListFlavor;
    static {
         try {
             uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String");
             urlListFlavor = new DataFlavor("text/plain;class=java.lang.String");
         } catch (ClassNotFoundException e) {
         }
    }

    public ParsingTransferHandler(TransferHandler delegate, TikaGUI tika) {
        this.delegate = delegate;
        this.tika = tika;
    }

    public boolean canImport(JComponent component, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            if (flavor.equals(DataFlavor.javaFileListFlavor) || flavor.equals(uriListFlavor) || flavor.equals(urlListFlavor)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean importData(
            JComponent component, Transferable transferable) {
        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                importFiles((List<File>) transferable.getTransferData(
                        DataFlavor.javaFileListFlavor));
            } else if (transferable.isDataFlavorSupported(urlListFlavor)) {
                Object data = transferable.getTransferData(urlListFlavor);
                tika.openURL(new URL(data.toString()));
            } else if (transferable.isDataFlavorSupported(uriListFlavor)) {
                importFiles(uriToFileList(
                        transferable.getTransferData(uriListFlavor)));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void importFiles(List<File> files) {
        for (File file : files) {
            tika.openFile(file);
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

    private static List<File> uriToFileList(Object data) {
        List<File> list = new ArrayList<File>();
        StringTokenizer st = new StringTokenizer(data.toString(), "\r\n");
        while (st.hasMoreTokens())
        {
            String s = st.nextToken();
            if (s.startsWith("#")) {
                continue;
            }
            try {
                list.add(new File(new URI(s)));
            } catch (Exception e) {
            }
        }
        return list;
    }
}
