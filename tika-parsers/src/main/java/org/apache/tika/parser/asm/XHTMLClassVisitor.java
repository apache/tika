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
package org.apache.tika.parser.asm;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Class visitor that generates XHTML SAX events to describe the
 * contents of the visited class.
 */
class XHTMLClassVisitor implements ClassVisitor {

    private final XHTMLContentHandler xhtml;

    private final Metadata metadata;

    private Type type;

    private String packageName;

    public XHTMLClassVisitor(ContentHandler handler, Metadata metadata) {
        this.xhtml = new XHTMLContentHandler(handler, metadata);
        this.metadata = metadata;
    }

    public void parse(InputStream stream)
            throws TikaException, SAXException, IOException {
        try {
            ClassReader reader = new ClassReader(stream);
            reader.accept(this, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Failed to parse a Java class", e);
            }
        }
    }

    public void visit(
            int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        type = Type.getObjectType(name);

        String className = type.getClassName();
        int dot = className.lastIndexOf('.');
        if (dot != -1) {
            packageName = className.substring(0, dot);
            className = className.substring(dot + 1);
        }

        metadata.set(TikaCoreProperties.TITLE, className);
        metadata.set(Metadata.RESOURCE_NAME_KEY, className + ".class");

        try {
            xhtml.startDocument();
            xhtml.startElement("pre");

            if (packageName != null) {
                writeKeyword("package");
                xhtml.characters(" " + packageName + ";\n");
            }

            writeAccess(access);
            if (isSet(access, Opcodes.ACC_INTERFACE)) {
                writeKeyword("interface");
                writeSpace();
                writeType(type);
                writeSpace();
                writeInterfaces("extends", interfaces);
            } else if (isSet(access, Opcodes.ACC_ENUM)) {
                writeKeyword("enum");
                writeSpace();
                writeType(type);
                writeSpace();
            } else {
                writeKeyword("class");
                writeSpace();
                writeType(type);
                writeSpace();
                if (superName != null) {
                    Type superType = Type.getObjectType(superName);
                    if (!superType.getClassName().equals("java.lang.Object")) {
                        writeKeyword("extends");
                        writeSpace();
                        writeType(superType);
                        writeSpace();
                    }
                }
                writeInterfaces("implements", interfaces);
            }
            xhtml.characters("{\n");
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeInterfaces(String keyword, String[] interfaces)
            throws SAXException {
        if (interfaces != null && interfaces.length > 0) {
            writeKeyword(keyword);
            String separator = " ";
            for (String iface : interfaces) {
                xhtml.characters(separator);
                writeType(Type.getObjectType(iface));
                separator = ", ";
            }
            writeSpace();
        }
    }

    public void visitEnd() {
        try {
            xhtml.characters("}\n");
            xhtml.endElement("pre");
            xhtml.endDocument();
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Ignored.
     */
    public void visitOuterClass(String owner, String name, String desc) {
    }

    /**
     * Ignored.
     */
    public void visitSource(String source, String debug) {
    }


    /**
     * Ignored.
     */
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    /**
     * Ignored.
     */
    public void visitAttribute(Attribute attr) {
    }

    /**
     * Ignored.
     */
    public void visitInnerClass(
            String name, String outerName, String innerName, int access) {
    }

    /**
     * Visits a field.
     */
    public FieldVisitor visitField(
            int access, String name, String desc, String signature,
            Object value) {
        if (!isSet(access, Opcodes.ACC_SYNTHETIC)) {
            try {
                xhtml.characters("    ");
                writeAccess(access);
                writeType(Type.getType(desc));
                writeSpace();
                writeIdentifier(name);

                if (isSet(access, Opcodes.ACC_STATIC) && value != null) {
                    xhtml.characters(" = ");
                    xhtml.characters(value.toString());
                }

                writeSemicolon();
                writeNewline();
            } catch (SAXException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    /**
     * Visits a method.
     */
    public MethodVisitor visitMethod(
            int access, String name, String desc, String signature,
            String[] exceptions) {
        if (!isSet(access, Opcodes.ACC_SYNTHETIC)) {
            try {
                xhtml.characters("    ");
                writeAccess(access);
                writeType(Type.getReturnType(desc));
                writeSpace();
                if ("<init>".equals(name)) {
                    writeType(type);
                } else {
                    writeIdentifier(name);
                }

                xhtml.characters("(");
                String separator = "";
                for (Type arg : Type.getArgumentTypes(desc)) {
                    xhtml.characters(separator);
                    writeType(arg);
                    separator = ", ";
                }
                xhtml.characters(")");

                if (exceptions != null && exceptions.length > 0) {
                    writeSpace();
                    writeKeyword("throws");
                    separator = " ";
                    for (String exception : exceptions) {
                        xhtml.characters(separator);
                        writeType(Type.getObjectType(exception));
                        separator = ", ";
                    }
                }

                writeSemicolon();
                writeNewline();
            } catch (SAXException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    private void writeIdentifier(String identifier) throws SAXException {
        xhtml.startElement("span", "class", "java-identifier");
        xhtml.characters(identifier);
        xhtml.endElement("span");
    }

    private void writeKeyword(String keyword) throws SAXException {
        xhtml.startElement("span", "class", "java-keyword");
        xhtml.characters(keyword);
        xhtml.endElement("span");
    }

    private void writeSemicolon() throws SAXException {
        xhtml.characters(";");
    }

    private void writeSpace() throws SAXException {
        xhtml.characters(" ");
    }

    private void writeNewline() throws SAXException {
        xhtml.characters("\n");
    }

    private void writeAccess(int access) throws SAXException {
        writeAccess(access, Opcodes.ACC_PRIVATE, "private");
        writeAccess(access, Opcodes.ACC_PROTECTED, "protected");
        writeAccess(access, Opcodes.ACC_PUBLIC, "public");
        writeAccess(access, Opcodes.ACC_STATIC, "static");
        writeAccess(access, Opcodes.ACC_FINAL, "final");
        writeAccess(access, Opcodes.ACC_ABSTRACT, "abstract");
        writeAccess(access, Opcodes.ACC_SYNCHRONIZED, "synchronized");
        writeAccess(access, Opcodes.ACC_TRANSIENT, "transient");
        writeAccess(access, Opcodes.ACC_VOLATILE, "volatile");
        writeAccess(access, Opcodes.ACC_NATIVE, "native");
    }

    private void writeAccess(int access, int code, String keyword)
            throws SAXException {
        if (isSet(access, code)) {
            writeKeyword(keyword);
            xhtml.characters(" ");
        }
    }

    private void writeType(Type type) throws SAXException {
        String name = type.getClassName();
        if (name.startsWith(packageName + ".")) {
            xhtml.characters(name.substring(packageName.length() + 1));
        } else if (name.startsWith("java.lang.")) {
            xhtml.characters(name.substring("java.lang.".length()));
        } else {
            xhtml.characters(name);
        }
    }

    private static boolean isSet(int value, int flag) {
        return (value & flag) != 0;
    }

}
