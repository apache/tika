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
import javax.lang.model.element.Element;
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
 * to avoid generating SPI files for utility interfaces like Serializable, Initializable, etc.
 */
@SupportedAnnotationTypes("org.apache.tika.config.TikaComponent")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class TikaComponentProcessor extends AbstractProcessor {

    /**
     * Known Tika service interfaces for SPI generation.
     * Only classes implementing these interfaces will have SPI files generated.
     */
    private static final Map<String, String> SERVICE_INTERFACES = new LinkedHashMap<>();

    static {
        // Map interface fully qualified name -> index file name
        SERVICE_INTERFACES.put("org.apache.tika.parser.Parser", "parsers");
        SERVICE_INTERFACES.put("org.apache.tika.detect.Detector", "detectors");
        SERVICE_INTERFACES.put("org.apache.tika.detect.EncodingDetector", "encoding-detectors");
        SERVICE_INTERFACES.put("org.apache.tika.language.translate.Translator", "translators");
        SERVICE_INTERFACES.put("org.apache.tika.renderer.Renderer", "renderers");
        SERVICE_INTERFACES.put("org.apache.tika.metadata.filter.MetadataFilter", "metadata-filters");
    }

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

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Processing @TikaComponent: " + className + " -> " + componentName +
                " (SPI: " + includeSpi + ")");

        // Find all implemented service interfaces
        List<String> serviceInterfaces = findServiceInterfaces(element);

        if (serviceInterfaces.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Class " + className + " annotated with @TikaComponent " +
                    "but does not implement any known Tika service interface", element);
            return;
        }

        // Process each service interface
        for (String serviceInterface : serviceInterfaces) {
            // Add to SPI services only if spi = true
            if (includeSpi) {
                spiServices.computeIfAbsent(serviceInterface, k -> new LinkedHashSet<>())
                        .add(className);
            }

            // Always add to index files (regardless of SPI setting)
            String indexFileName = SERVICE_INTERFACES.get(serviceInterface);
            if (indexFileName != null) {
                Map<String, String> index = indexFiles.computeIfAbsent(indexFileName,
                        k -> new LinkedHashMap<>());

                // Check for duplicate names
                if (index.containsKey(componentName)) {
                    String existingClass = index.get(componentName);
                    if (!existingClass.equals(className)) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Duplicate component name '" + componentName + "' for classes: " +
                                existingClass + " and " + className, element);
                    }
                } else {
                    index.put(componentName, className);
                }
            }
        }
    }

    /**
     * Finds all Tika service interfaces implemented by the given type element.
     */
    private List<String> findServiceInterfaces(TypeElement element) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        findServiceInterfacesRecursive(element.asType(), result, visited);
        return result;
    }

    /**
     * Recursively searches for service interfaces in the type hierarchy.
     */
    private void findServiceInterfacesRecursive(TypeMirror type, List<String> result,
                                                 Set<String> visited) {
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

        // Check if this is a service interface
        if (SERVICE_INTERFACES.containsKey(typeName)) {
            if (!result.contains(typeName)) {
                result.add(typeName);
            }
        }

        // Check superclass
        TypeMirror superclass = typeElement.getSuperclass();
        findServiceInterfacesRecursive(superclass, result, visited);

        // Check interfaces
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            findServiceInterfacesRecursive(interfaceType, result, visited);
        }
    }

    /**
     * Writes META-INF/services files for Java SPI.
     */
    private void writeServiceFiles() {
        for (Map.Entry<String, Set<String>> entry : spiServices.entrySet()) {
            String serviceInterface = entry.getKey();
            Set<String> implementations = entry.getValue();

            try {
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                        "META-INF/services/" + serviceInterface);

                try (Writer writer = file.openWriter()) {
                    writer.write("# Generated by TikaComponentProcessor\n");
                    writer.write("# Do not edit manually\n");
                    for (String impl : implementations) {
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
                    writer.write("# Generated by TikaComponentProcessor\n");
                    writer.write("# Do not edit manually\n");
                    writer.write("# Format: component-name=fully.qualified.ClassName\n");
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
}
