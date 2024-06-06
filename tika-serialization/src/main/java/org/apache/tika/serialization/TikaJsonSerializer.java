package org.apache.tika.serialization;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * This is a basic serializer that requires that an object:
 * a) have a no-arg constructor
 * b) have both setters and getters for the same parameters with the same names, e.g. setXYZ and getXYZ
 * c) setters and getters have to follow the pattern setX where x is a capital letter
 * d) have maps as parameters where the keys are strings
 */
public class TikaJsonSerializer {

    public static String INSTANTIATED_CLASS_KEY = "_class";
    public static String SUPER_CLASS_KEY = "_super_class";

    private static Logger LOG = LoggerFactory.getLogger(TikaJsonSerializer.class);

    static Set<Class> PRIMITIVES = Set.of(int.class, double.class, float.class, long.class, short.class, boolean.class, String.class, byte.class, char.class);
    static Set<Class> BOXED = Set.of(Integer.class, Double.class, Float.class, Long.class, Short.class, Boolean.class, Byte.class, Character.class);

    private static String GET = "get";
    static String SET = "set";
    private static String IS = "is";

    public static void serialize(Object obj, JsonGenerator jsonGenerator) throws TikaSerializationException, IOException {
        serialize(null, obj, obj.getClass(), jsonGenerator);
    }

    public static void serialize(String fieldName, Object obj, Class superClass, JsonGenerator jsonGenerator) throws TikaSerializationException, IOException {
        if (obj == null) {
            if (fieldName == null) {
                jsonGenerator.writeNull();
            } else {
                jsonGenerator.writeNullField(fieldName);
            }
        } else if (PRIMITIVES.contains(obj.getClass()) || BOXED.contains(obj.getClass())) {
            try {
                serializePrimitiveAndBoxed(fieldName, obj, jsonGenerator);
            } catch (IOException e) {
                throw new TikaSerializationException("problem serializing", e);
            }
        } else if (isCollection(obj)) {
            serializeCollection(fieldName, obj, jsonGenerator);
        } else if (obj.getClass().isEnum()) {
            jsonGenerator.writeStringField(fieldName, ((Enum)obj).name());
        } else {
            serializeObject(fieldName, obj, superClass, jsonGenerator);
        }
    }

    /**
     * limited to array, list and map
     *
     * @param obj
     * @return
     */
    private static boolean isCollection(Object obj) {
        Class clazz = obj.getClass();
        return clazz.isArray() || List.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz);
    }


    /**
     * @param fieldName     can be null -- used only for logging and debugging
     * @param obj
     * @param superClass
     * @param jsonGenerator
     * @throws TikaSerializationException
     */
    public static void serializeObject(String fieldName, Object obj, Class superClass, JsonGenerator jsonGenerator) throws TikaSerializationException {


        try {
            Constructor constructor = obj
                    .getClass()
                    .getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("class (" +
                    obj.getClass() + ") doesn't have a no-arg constructor. Respectfully not seralizing.");
        }
        try {
            if (fieldName != null) {
                jsonGenerator.writeFieldName(fieldName);
            }
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(INSTANTIATED_CLASS_KEY, obj
                    .getClass()
                    .getName());
            if (!obj
                    .getClass()
                    .equals(superClass)) {
                jsonGenerator.writeStringField(SUPER_CLASS_KEY, superClass.getName());
            }
            Map<String, Method> matches = getGetters(obj
                    .getClass()
                    .getMethods());
            //iterate through the getters
            for (Map.Entry<String, Method> e : matches.entrySet()) {
                try {
                    Object methodVal = e
                            .getValue()
                            .invoke(obj);
                    Class valSuperClass = e
                            .getValue()
                            .getReturnType();
                    serialize(e.getKey(), methodVal, valSuperClass, jsonGenerator);
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new TikaSerializationException("couldn't write paramName=" + e.getKey(), ex);
                }
            }

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new TikaSerializationException("couldn't serialize", e);
        }
    }

    private static Map<String, Method> getGetters(Method[] methods) {
        Map<String, List<Method>> getters = new HashMap<>();
        Map<String, List<Method>> setters = new HashMap<>();

        for (Method m : methods) {
            String name = m.getName();
            if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                String param = getParam(GET, name);
                add(param, m, getters);
            } else if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
                String param = getParam(IS, name);
                add(param, m, getters);
            } else if (name.startsWith("set") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                //take only single param setters
                if (m.getParameters().length == 1) {
                    String param = getParam(SET, name);
                    add(param, m, setters);
                }
            }
        }
        //this strictly looks for classA.equals(classB)
        //this does not look for instance of, nor does it look for boxed vs. primitives
        //Also, TODO -- this should favor getters and setters with Strings over those
        //with complex types
        Map<String, Method> ret = new HashMap<>();
        for (Map.Entry<String, List<Method>> e : getters.entrySet()) {
            String paramName = e.getKey();
            List<Method> setterList = setters.get(paramName);
            if (setterList == null || setterList.size() == 0) {
                LOG.debug("Couldn't find setter for getter: " + paramName);
                continue;
            }
            for (Method getter : e.getValue()) {
                for (Method setter : setterList) {
                    Class setClass = setter.getParameters()[0].getType();
                    if (getter
                            .getReturnType()
                            .equals(setClass)) {
                        ret.put(paramName, getter);
                    }
                }
            }
        }
        return ret;
    }

    private static void serializeCollection(String fieldName, Object obj, JsonGenerator jsonGenerator) throws IOException, TikaSerializationException {
        if (fieldName != null) {
            jsonGenerator.writeFieldName(fieldName);
        }
        Class clazz = obj.getClass();
        if (clazz.isArray()) {
            jsonGenerator.writeStartArray();
            for (Object item : (Object[]) obj) {
                serialize(item, jsonGenerator);
            }
            jsonGenerator.writeEndArray();
        } else if (List.class.isAssignableFrom(clazz)) {
            jsonGenerator.writeStartArray();
            for (Object item : (List) obj) {
                serialize(item, jsonGenerator);
            }
            jsonGenerator.writeEndArray();
        } else if (Map.class.isAssignableFrom(clazz)) {
            jsonGenerator.writeStartObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) obj).entrySet()) {
                serialize(e.getKey(), e.getValue(), e.getValue().getClass(), jsonGenerator);
            }
            jsonGenerator.writeEndObject();
        } else {
            throw new UnsupportedOperationException("Should have been a collection?! " + clazz);
        }
    }

    private static void serializeItem(String field, Object obj, Class<?> generalType, JsonGenerator jsonGenerator) {

    }

    private static void serializePrimitiveAndBoxed(String paramName, Object obj, JsonGenerator jsonGenerator) throws IOException {
        Class clazz = obj.getClass();
        if (paramName != null) {
            jsonGenerator.writeFieldName(paramName);
        }
        if (clazz.equals(String.class)) {
            jsonGenerator.writeString((String) obj);
        } else if (clazz.equals(Integer.class)) {
            jsonGenerator.writeNumber((Integer) obj);
        } else if (clazz.equals(Short.class)) {
            jsonGenerator.writeNumber((Short) obj);
        } else if (clazz.equals(Long.class)) {
            jsonGenerator.writeNumber((Long) obj);
        } else if (clazz.equals(Float.class)) {
            jsonGenerator.writeNumber((Float) obj);
        } else if (clazz.equals(Double.class)) {
            jsonGenerator.writeNumber((Double) obj);
        } else if (clazz.equals(Boolean.class)) {
            jsonGenerator.writeBoolean((Boolean) obj);
        } else if (clazz.equals(short.class)) {
            jsonGenerator.writeNumber((short) obj);
        } else if (clazz.equals(int.class)) {
            jsonGenerator.writeNumber((int) obj);
        } else if (clazz.equals(long.class)) {
            jsonGenerator.writeNumber((long) obj);
        } else if (clazz.equals(float.class)) {
            jsonGenerator.writeNumber((float) obj);
        } else if (clazz.equals(double.class)) {
            jsonGenerator.writeNumber((double) obj);
        } else if (clazz.equals(boolean.class)) {
            jsonGenerator.writeBoolean((boolean) obj);
        } else {
            throw new UnsupportedOperationException("I regret that I don't yet support " + clazz);
        }

    }

    private static void add(String param, Method method, Map<String, List<Method>> map) {
        List<Method> methods = map.get(param);
        if (methods == null) {
            methods = new ArrayList<>();
            map.put(param, methods);
        }
        methods.add(method);
    }

    static String getParam(String prefix, String name) {
        String ret = name.substring(prefix.length());
        ret = ret
                .substring(0, 1)
                .toLowerCase(Locale.ROOT) + ret.substring(1);
        return ret;
    }

}
