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

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/**
 * Simple Swing GUI for Apache Tika. Opens a window with tabs for
 * "Text content" and "Metadata". You can drag and drop files on top
 * of the window to have them parsed.
 */
public class TikaGUI implements Runnable {

    public void run() {
        JFrame frame = new JFrame("Apache Tika");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        frame.add(tabs);

        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.setText("<center>Drop file here</center>");
        tabs.add("Text content", new JScrollPane(editor));

        DefaultTableModel model = new DefaultTableModel(
                new Object[][] { { "", "" } },
                new Object[] { "Name", "Value" });
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        tabs.addTab("Metadata", new JScrollPane(table));

        table.setTransferHandler(new ParsingTransferHandler(
                table.getTransferHandler(), model, editor));
        editor.setTransferHandler(new ParsingTransferHandler(
                editor.getTransferHandler(), model, editor));

        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(new TikaGUI());
    }

}
