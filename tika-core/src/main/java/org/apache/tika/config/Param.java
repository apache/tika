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

import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


/**
 * This is a JAXB serializable model class for parameters from configuration file.
 *
 * @param <T> value type. Should be serializable to string and have a constructor with string param
 * @since Apache Tika 1.14
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.NONE)
public class Param<T> implements Serializable {

    private static final JAXBContext JAXB_CTX;
    private static final Marshaller MARSHALLER;
    private static final Unmarshaller UNMARSHALLER;
    private static final Map<Class<?>, String> map = new HashMap<>();
    private static final Map<String, Class<?>> reverseMap = new HashMap<>();

    static {
        map.put(Boolean.class, "bool");
        map.put(String.class, "string");
        map.put(Byte.class, "byte");
        map.put(Short.class, "short");
        map.put(Integer.class, "int");
        map.put(Long.class, "long");
        map.put(BigInteger.class, "bigint");
        map.put(Float.class, "float");
        map.put(Double.class, "double");
        map.put(File.class, "file");
        map.put(URI.class, "uri");
        map.put(URL.class, "url");
        for (Map.Entry<Class<?>, String> entry : map.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        try {
            JAXB_CTX = JAXBContext.newInstance(Param.class);
            MARSHALLER = JAXB_CTX.createMarshaller();
            MARSHALLER.setEventHandler(new DefaultValidationEventHandler());
            UNMARSHALLER = JAXB_CTX.createUnmarshaller();
            UNMARSHALLER.setEventHandler(new DefaultValidationEventHandler());
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @XmlTransient
    private Class<T> type;

    @XmlAttribute(name = "name")
    private String name;

    @XmlValue()
    private String value;

    @XmlTransient
    private T actualValue;

    public Param(){
    }

    public Param(String name, Class<T> type, T value){
        this.name = name;
        this.type = type;
        this.value = value.toString();
    }

    public Param(String name, T value){
        this(name, (Class<T>) value.getClass(), value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlTransient
    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    @XmlAttribute(name = "type")
    public String getTypeString(){
        if (type == null) {
            return null;
        }
        if (map.containsKey(type)){
            return map.get(type);
        }
        return type.getName();
    }

    public void setTypeString(String type){
        if (type == null || type.isEmpty()){
            return;
        }
        if (reverseMap.containsKey(type)){
            this.type = (Class<T>) reverseMap.get(type);
        } else try {
            this.type = (Class<T>) Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        this.actualValue = null;
    }

    @XmlTransient
    public T getValue(){
        if (actualValue == null) {
            try {
                Constructor<T> constructor = type.getConstructor(String.class);
                constructor.setAccessible(true);
                this.actualValue = constructor.newInstance(value);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(type + " doesnt have a constructor that takes String arg", e);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return actualValue;
    }

    @Override
    public String toString() {
        return "Param{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", actualValue=" + actualValue +
                '}';
    }

    public void save(OutputStream stream) throws JAXBException {
        MARSHALLER.marshal(this, stream);
    }

    public void save(Node node) throws JAXBException {
        MARSHALLER.marshal(this, node);
    }

    public static <T> Param<T> load(InputStream stream) throws JAXBException {
        return (Param<T>) UNMARSHALLER.unmarshal(stream);
    }

    public static <T> Param<T> load(Node node) throws JAXBException {
        return (Param<T>) UNMARSHALLER.unmarshal(node);
    }

}
