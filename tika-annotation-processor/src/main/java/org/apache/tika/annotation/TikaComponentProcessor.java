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
package org.apache.tika.annotation;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.apache.tika.config.TikaComponent;

/**
 * Annotation processor for {@link TikaComponent} that generates:
 * <ul>
 *   <li>Standard Java SPI files (META-INF/services/*) for ServiceLoader</li>
 *   <li>Component index files (META-INF/tika/*.idx) for name-based lookup</li>
 * </ul>
 *
 * <p>The processor maintains an inclusion list of known Tika service interfaces
 * to avoid generating SPI files for utility interfaces like Serializable, etc.
 */
@SupportedAnnotationTypes("org.apache.tika.config.TikaComponent")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class TikaComponentProcessor extends AbstractProcessor {

    /**
     * Known Tika service interfaces for SPI generation.
     * Only classes implementing these interfaces will have SPI files generated.
     * <p>
     * Note: DigesterFactory and ContentHandlerFactory are NOT in this map because
     * they are parse-context components, not top-level service interfaces.
     * Their implementations go to parse-context.idx instead.
     */
    private static final Map<String, String> SERVICE_INTERFACES = new LinkedHashMap<>();

    static {
        // Map interface fully qualified name -> index file name
        SERVICE_INTERFACES.put("org.apache.tika.parser.Parser", "parsers");
        SERVICE_INTERFACES.put("org.apache.tika.detect.Detector", "detectors");
        SERVICE_INTERFACES.put("org.apache.tika.detect.EncodingDetector", "encoding-detectors");
        SERVICE_INTERFACES.put("org.apache.tika.language.detect.LanguageDetector", "language-detectors");
        SERVICE_INTERFACES.put("org.apache.tika.language.translate.Translator", "translators");
        SERVICE_INTERFACES.put("org.apache.tika.renderer.Renderer", "renderers");
        SERVICE_INTERFACES.put("org.apache.tika.metadata.filter.MetadataFilter", "metadata-filters");
    }

    /**
     * Interfaces whose implementations should go to parse-context.idx.
     * These are factory interfaces used via ParseContext, not loaded via SPI.
     */
    private static final Set<String> PARSE_CONTEXT_INTERFACES = Set.of(
            "org.apache.tika.digest.DigesterFactory",
            "org.apache.tika.sax.ContentHandlerFactory",
            "org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory",
            "org.apache.tika.extractor.EmbeddedDocumentExtractorFactory"
    );

    private Messager messager;
    private Filer filer;

    // Accumulate components across rounds
    // Map: service interface name -> set of implementing class names
    private final Map<String, Set<String>> spiServices = new HashMap<>();

    // Map: index file name -> map of (component name -> class name)
    private final Map<String, Map<String, String>> indexFiles = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            // Final round - write accumulated data
            writeServiceFiles();
            writeIndexFiles();
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(TikaComponent.class)) {
            if (element instanceof TypeElement) {
                processComponent((TypeElement) element);
            }
        }

        return true;
    }

    private void processComponent(TypeElement element) {
        String className = element.getQualifiedName().toString();
        TikaComponent annotation = element.getAnnotation(TikaComponent.class);

        // Determine component name
        String componentName = annotation.name();
        if (componentName == null || componentName.isEmpty()) {
            // Auto-generate from class name
            String simpleName = element.getSimpleName().toString();
            componentName = KebabCaseConverter.toKebabCase(simpleName);
        }

        // Check if component should be included in SPI
        boolean includeSpi = annotation.spi();

        // Get contextKey if specified (need to use mirror API for Class types)
        String contextKey = getContextKeyFromAnnotation(element);

        // Get defaultFor if specified (need to use mirror API for Class types)
        String defaultFor = getDefaultForFromAnnotation(element);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Processing @TikaComponent: " + className + " -> " + componentName +
                " (SPI: " + includeSpi + ", contextKey: " + contextKey +
                ", defaultFor: " + defaultFor + ")");

        // Find all implemented service interfaces (both SPI and parse-context)
        List<String> serviceInterfaces = findServiceInterfaces(element);
        List<String> parseContextInterfaces = findParseContextInterfaces(element);

        // Combine all interfaces for context key detection
        List<String> allInterfaces = new ArrayList<>(serviceInterfaces);
        allInterfaces.addAll(parseContextInterfaces);

        // Build the index entry value (className or className:key=X[:default])
        // Auto-detect contextKey from service interface if not explicitly specified
        String indexValue = className;
        if (contextKey != null) {
            // Explicit contextKey specified
            indexValue = className + ":key=" + contextKey;
        } else if (allInterfaces.size() == 1) {
            // Auto-detect contextKey from single interface
            indexValue = className + ":key=" + allInterfaces.get(0);
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Auto-detected contextKey=" + allInterfaces.get(0) + " for " + className);
        } else if (allInterfaces.size() > 1) {
            // Multiple interfaces - warn that contextKey should be specified
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Class " + className + " implements multiple interfaces: " +
                    allInterfaces + ". Consider specifying @TikaComponent(contextKey=...) " +
                    "to select which one to use as ParseContext key.", element);
        }

        // Add :default marker if defaultFor is specified
        if (defaultFor != null) {
            indexValue = indexValue + ":default";
        }

        // Check if this is a parse-context component (implements a parse-context interface
        // or doesn't implement any known service interface)
        if (!parseContextInterfaces.isEmpty() || serviceInterfaces.isEmpty()) {
            // Put in parse-context.idx
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Class " + className + " is a parse-context component, " +
                    "adding to parse-context.idx", element);

            Map<String, String> index = indexFiles.computeIfAbsent("parse-context",
                    k -> new LinkedHashMap<>());
            addToIndex(index, componentName, indexValue, className, element);
        }

        // Process SPI service interfaces (these also get their own idx files)
        for (String serviceInterface : serviceInterfaces) {
            // Add to SPI services only if spi = true
            if (includeSpi) {
                spiServices.computeIfAbsent(serviceInterface, k -> new LinkedHashSet<>())
                        .add(className);
            }

            // Always add to index files for name-based lookup, regardless of spi value
            String indexFileName = SERVICE_INTERFACES.get(serviceInterface);
            if (indexFileName != null) {
                Map<String, String> index = indexFiles.computeIfAbsent(indexFileName,
                        k -> new LinkedHashMap<>());
                addToIndex(index, componentName, indexValue, className, element);
            }
        }
    }

    /**
     * Adds an entry to an index, checking for duplicates.
     */
    private void addToIndex(Map<String, String> index, String componentName,
                           String indexValue, String className, TypeElement element) {
        if (index.containsKey(componentName)) {
            String existingValue = index.get(componentName);
            // Extract class name from value (may have :key= suffix)
            String existingClass = existingValue.contains(":")
                    ? existingValue.substring(0, existingValue.indexOf(":"))
                    : existingValue;
            if (!existingClass.equals(className)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Duplicate component name '" + componentName + "' for classes: " +
                        existingClass + " and " + className, element);
            }
        } else {
            index.put(componentName, indexValue);
        }
    }

    /**
     * Gets the contextKey value from the annotation using the mirror API.
     * Returns null if contextKey is void.class (the default).
     */
    private String getContextKeyFromAnnotation(TypeElement element) {
        return getClassAttributeFromAnnotation(element, "contextKey");
    }

    /**
     * Gets the defaultFor value from the annotation using the mirror API.
     * Returns null if defaultFor is void.class (the default).
     */
    private String getDefaultForFromAnnotation(TypeElement element) {
        return getClassAttributeFromAnnotation(element, "defaultFor");
    }

    /**
     * Gets a Class-typed attribute value from the annotation using the mirror API.
     * Returns null if the attribute is void.class (the default).
     */
    private String getClassAttributeFromAnnotation(TypeElement element, String attributeName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            DeclaredType annotationType = mirror.getAnnotationType();
            if (annotationType.toString().equals(TikaComponent.class.getName())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
                        // The value is a TypeMirror for Class types
                        Object value = entry.getValue().getValue();
                        if (value instanceof TypeMirror) {
                            String typeName = value.toString();
                            // void.class is the default, meaning "not specified"
                            if (!"void".equals(typeName) && !"java.lang.Void".equals(typeName)) {
                                return typeName;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds all Tika service interfaces implemented by the given type element.
     */
    private List<String> findServiceInterfaces(TypeElement element) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        findInterfacesRecursive(element.asType(), result, visited, SERVICE_INTERFACES.keySet());
        return result;
    }

    /**
     * Finds all parse-context interfaces implemented by the given type element.
     */
    private List<String> findParseContextInterfaces(TypeElement element) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        findInterfacesRecursive(element.asType(), result, visited, PARSE_CONTEXT_INTERFACES);
        return result;
    }

    /**
     * Recursively searches for interfaces in the type hierarchy.
     *
     * @param type the type to search from
     * @param result list to add found interfaces to
     * @param visited set of already visited types (to avoid infinite loops)
     * @param targetInterfaces the set of interface names to look for
     */
    private void findInterfacesRecursive(TypeMirror type, List<String> result,
                                         Set<String> visited, Set<String> targetInterfaces) {
        if (type == null || !(type instanceof DeclaredType)) {
            return;
        }

        DeclaredType declaredType = (DeclaredType) type;
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        String typeName = typeElement.getQualifiedName().toString();

        // Avoid infinite loops
        if (!visited.add(typeName)) {
            return;
        }

        // Check if this is a target interface
        if (targetInterfaces.contains(typeName)) {
            if (!result.contains(typeName)) {
                result.add(typeName);
            }
        }

        // Check superclass
        TypeMirror superclass = typeElement.getSuperclass();
        findInterfacesRecursive(superclass, result, visited, targetInterfaces);

        // Check interfaces
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            findInterfacesRecursive(interfaceType, result, visited, targetInterfaces);
        }
    }

    /**
     * Writes META-INF/services files for Java SPI.
     */
    private void writeServiceFiles() {
        for (Map.Entry<String, Set<String>> entry : spiServices.entrySet()) {
            String serviceInterface = entry.getKey();
            Set<String> implementations = entry.getValue();

            // Sort implementations alphabetically for deterministic output
            List<String> sortedImplementations = new ArrayList<>(implementations);
            Collections.sort(sortedImplementations);

            try {
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                        "META-INF/services/" + serviceInterface);

                try (Writer writer = file.openWriter()) {
                    writeApacheLicenseHeader(writer);
                    writer.write("\n\n");
                    writer.write("# Generated by TikaComponentProcessor\n");
                    writer.write("# Do not edit manually\n");
                    for (String impl : sortedImplementations) {
                        writer.write(impl);
                        writer.write("\n");
                    }
                }

                messager.printMessage(Diagnostic.Kind.NOTE,
                        "Generated SPI file: META-INF/services/" + serviceInterface +
                        " with " + implementations.size() + " implementations");

            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write SPI file for " + serviceInterface + ": " + e.getMessage());
            }
        }
    }

    /**
     * Writes META-INF/tika/*.idx files for name-based component lookup.
     */
    private void writeIndexFiles() {
        for (Map.Entry<String, Map<String, String>> entry : indexFiles.entrySet()) {
            String fileName = entry.getKey();
            Map<String, String> components = entry.getValue();

            try {
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                        "META-INF/tika/" + fileName + ".idx");

                try (Writer writer = file.openWriter()) {
                    writeApacheLicenseHeader(writer);
                    writer.write("# Generated by TikaComponentProcessor\n");
                    writer.write("# Do not edit manually\n");
                    writer.write("# Format: component-name=fully.qualified.ClassName[:key=contextKeyClass]\n");
                    for (Map.Entry<String, String> component : components.entrySet()) {
                        writer.write(component.getKey());
                        writer.write("=");
                        writer.write(component.getValue());
                        writer.write("\n");
                    }
                }

                messager.printMessage(Diagnostic.Kind.NOTE,
                        "Generated index file: META-INF/tika/" + fileName + ".idx" +
                        " with " + components.size() + " components");

            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to write index file " + fileName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Writes the Apache License 2.0 header to a file.
     */
    private void writeApacheLicenseHeader(Writer writer) throws IOException {
        String header = """
                #  Licensed to the Apache Software Foundation (ASF) under one or more
                #  contributor license agreements.  See the NOTICE file distributed with
                #  this work for additional information regarding copyright ownership.
                #  The ASF licenses this file to You under the Apache License, Version 2.0
                #  (the "License"); you may not use this file except in compliance with
                #  the License.  You may obtain a copy of the License at
                #
                #       http://www.apache.org/licenses/LICENSE-2.0
                #
                #  Unless required by applicable law or agreed to in writing, software
                #  distributed under the License is distributed on an "AS IS" BASIS,
                #  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                #  See the License for the specific language governing permissions and
                #  limitations under the License.

                """;
        writer.write(header);
    }
}
