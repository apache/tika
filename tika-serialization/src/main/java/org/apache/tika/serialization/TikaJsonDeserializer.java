package org.apache.tika.serialization;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

public class TikaJsonDeserializer {

    public static Optional deserializeObject(JsonNode root) {
        if (!root.isObject()) {
            throw new IllegalArgumentException("root needs to be an object");
        }
        if (!root.has(TikaJsonSerializer.INSTANTIATED_CLASS_KEY)) {
            throw new IllegalArgumentException("need to specify: " + TikaJsonSerializer.INSTANTIATED_CLASS_KEY);
        }
        String className = root
                .get(TikaJsonSerializer.INSTANTIATED_CLASS_KEY)
                .asText();
        String superClass = root.has(TikaJsonSerializer.SUPER_CLASS_KEY) ? root
                .get(TikaJsonSerializer.SUPER_CLASS_KEY)
                .asText() : className;

        try {
            return Optional.of(deserialize(Class.forName(className), Class.forName(superClass), root));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> T deserialize(Class<? extends T> clazz, Class<? extends T> superClazz, JsonNode root)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        T obj = clazz
                .getDeclaredConstructor()
                .newInstance();
        Map<String, List<Method>> setters = getSetters(obj);
        if (!root.isObject()) {
            throw new IllegalArgumentException("must be object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String name = e.getKey();
            JsonNode child = e.getValue();
            if (TikaJsonSerializer.INSTANTIATED_CLASS_KEY.equals(name) || TikaJsonSerializer.SUPER_CLASS_KEY.equals(name)) {
                continue;
            }
            setValue(name, child, obj, setters);
        }
        return obj;
    }

    private static Map<String, List<Method>> getSetters(Object obj) {
        Map<String, List<Method>> setters = new HashMap<>();
        for (Method m : obj
                .getClass()
                .getMethods()) {
            String n = m.getName();
            if (n.startsWith(TikaJsonSerializer.SET) && n.length() > 3 && Character.isUpperCase(n.charAt(3))) {
                if (m.getParameters().length == 1) {
                    String paramName = TikaJsonSerializer.getParam(TikaJsonSerializer.SET, n);
                    List<Method> methods = setters.get(paramName);
                    if (methods == null) {
                        methods = new ArrayList<>();
                        setters.put(paramName, methods);
                    }
                    methods.add(m);
                }
            }
        }
        return setters;
    }

    private static void setValue(String name, JsonNode node, Object obj, Map<String, List<Method>> setters) {
        List<Method> mySetters = setters.get(name);
        if (mySetters == null || mySetters.size() == 0) {
            throw new IllegalArgumentException("can't find any setter for " + name);
        }
        if (node.isNull()) {
            setNull(name, node, obj, mySetters);
        } else if (node.isNumber()) {
            setNumericValue(name, node, obj, mySetters);
        } else if (node.isTextual()) {
            setStringValue(name, node.asText(), obj, mySetters);
        } else if (node.isArray()) {
            setArray(name, node, obj, mySetters);
        } else if (node.isObject()) {
            setObject(name, node, obj, mySetters);
        } else if (node.isBoolean()) {
            setBoolean(name, node, obj, mySetters);
        }
    }

    private static void setArray(String name, JsonNode node, Object obj, List<Method> mySetters) {
        //there's much more to be done here. :(
        for (Method setter : mySetters) {
            try {
                tryArray(name, node, obj, setter);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new IllegalArgumentException("couldn't create array for " + name);
            }
        }
    }

    private static void tryArray(String name, JsonNode node, Object obj, Method setter) throws InvocationTargetException, IllegalAccessException {
        Class argClass = setter.getReturnType();
        Class componentType = argClass.getComponentType();
        if (argClass.isArray()) {
            int len = node.size();
            Object arrayObject = Array.newInstance(componentType, len);
            for (int i = 0; i < len; i++) {
                Array.set(arrayObject, i, getVal(componentType, node.get(i)));
            }
            setter.invoke(obj, arrayObject);

        } else if (List.class.isAssignableFrom(argClass)) {
            int len = node.size();
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                list.add(getVal(componentType, node.get(i)));
            }
            setter.invoke(obj, list);
        }
    }

    private static <T> T getVal(T clazz, JsonNode node) {
        if (clazz.equals(String.class)) {
            return (T) node.asText();
        } else if (clazz.equals(Integer.class) || clazz.equals(int.class)) {
            return (T) Integer.valueOf(node.intValue());
        } else if (clazz.equals(Long.class) || clazz.equals(long.class)) {
            return (T) Long.valueOf(node.longValue());
        } else if (clazz.equals(Float.class) || clazz.equals(float.class)) {
            return (T) Float.valueOf(node.floatValue());
        } else if (clazz.equals(Double.class) || clazz.equals(double.class)) {
            return (T) Double.valueOf(node.doubleValue());
        }
        //add short, boolean and full class objects?
    }

    private static void setObject(String name, JsonNode node, Object obj, List<Method> mySetters) {
        if (! node.has(TikaJsonSerializer.INSTANTIATED_CLASS_KEY)) {
            setMap(name, node, obj, mySetters);
        }

        Optional object = deserializeObject(node);
        if (object.isEmpty()) {
            //log, throw exception?!
            return;
        }
        for (Method m : mySetters) {
            Class argClass = m.getReturnType();
            if (argClass.isAssignableFrom(object.getClass())) {
                try {
                    m.invoke(obj, object.get());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    //swallow
                }
            }
        }
        throw new IllegalArgumentException("can't set object on " + name);
    }

    private static void setMap(String name, JsonNode node, Object obj, List<Method> setters) {
        //TODO this should try to match the map setters with the data types
        //TODO -- pick up here
        for (Method m : setters) {
            if (Map.class.isAssignableFrom(m))
        }
    }

    private static void setBoolean(String name, JsonNode node, Object obj, List<Method> setters) {
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if (argClass.equals(Boolean.class) || argClass.equals(boolean.class)) {
                m.invoke(obj, node.asBoolean());
            }
        }
        //TODO -- maybe check for string?
        throw new IllegalArgumentException("can't set boolean on " + name);
    }

    private static void setNull(String name, JsonNode node, Object obj, List<Method> setters) {
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if (! TikaJsonSerializer.PRIMITIVES.contains(argClass)) {
                try {
                    m.invoke(obj, null);
                } catch (Exception e) {
                    //swallow
                }
            }
        }
        throw new IllegalArgumentException("can't set null on " + name);
    }

    private static void setStringValue(String name, String txt, Object obj, List<Method> setters) {

        //try for exact match first
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if (argClass.equals(String.class)) {
                m.invoke(obj, txt);
                return;
            }
        }
        Method intMethod = null;
        Method longMethod = null;
        Method doubleMethod = null;
        Method floatMethod = null;
        Method shortMethod = null;
        Method boolMethod = null;
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if (argClass.equals(Integer.class) || argClass.equals(int.class)) {
                intMethod = m;
            } else if (argClass.equals(Long.class) || argClass.equals(long.class)) {
                longMethod = m;
            } else if (argClass.equals(Float.class) || argClass.equals(float.class)) {
                floatMethod = m;
            } else if (argClass.equals(Double.class) || argClass.equals(double.class)) {
                doubleMethod = m;
            } else if (argClass.equals(Short.class) || argClass.equals(short.class)) {
                shortMethod = m;
            } else if (argClass.equals(Boolean.class) || argClass.equals(boolean.class)) {
                boolMethod = m;
            }
        }

        if (shortMethod != null) {
            try {
                short val = Short.parseShort(txt);
                shortMethod.invoke(obj, val);
                return;
            } catch(NumberFormatException e) {
                //swallow
            }
        } else if (intMethod != null) {
            try {
                int val = Integer.parseInt(txt);
                intMethod.invoke(obj, val);
                return;
            } catch(NumberFormatException e) {
                //swallow
            }
        } else if (floatMethod != null) {
            try {
                float val = Float.parseFloat(txt);
                floatMethod.invoke(obj, val);
                return;
            } catch(NumberFormatException e) {
                //swallow
            }
        } else if (longMethod != null) {
            try {
                long val = Long.parseLong(txt);
                longMethod.invoke(obj, val);
                return;
            } catch(NumberFormatException e) {
                //swallow
            }
        } else if (doubleMethod != null) {
            try {
                double val = Double.parseDouble(txt);
                doubleMethod.invoke(obj, val);
                return;
            } catch(NumberFormatException e) {
                //swallow
            }
        } else if (boolMethod != null) {
            if (txt.equalsIgnoreCase("true")) {
                boolMethod.invoke(obj, true);
            } else if (txt.equalsIgnoreCase("false")) {
                boolMethod.invoke(obj, false);
            }
        }
        throw new IllegalArgumentException("I regret I couldn't find a setter for: " + name);

    }

    private static void setNumericValue(String name, JsonNode node, Object obj, List<Method> setters) {

        //try numeric and equals first
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if ((argClass.equals(int.class) || argClass.equals(Integer.class)) && node.isInt()) {
                m.invoke(obj, node.intValue());
                return;
            } else if ((argClass.equals(long.class) || argClass.equals(Long.class)) && node.isLong()) {
                m.invoke(obj, node.asLong());
                return;
            } else if ((argClass.equals(float.class) || argClass.equals(Float.class)) && node.isFloat()) {
                m.invoke(obj, node.floatValue());
                return;
            } else if ((argClass.equals(double.class) || argClass.equals(Double.class)) && node.isDouble()) {
                m.invoke(obj, node.doubleValue());
                return;
            }
        }
        //try for higher precision setters
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if ((argClass.equals(long.class) || argClass.equals(Long.class)) && node.isInt()) {
                m.invoke(obj, node.asLong());
                return;
            } else if ((argClass.equals(double.class) || argClass.equals(Double.class)) && node.isFloat()) {
                m.invoke(obj, node.floatValue());
                return;
            }
        }
        //finally try for String
        for (Method m : setters) {
            Class argClass = m.getParameters()[0].getType();
            if (argClass.equals(String.class)) {
                m.invoke(obj, node.asText());
                return;
            }
        }
        throw new IllegalArgumentException("Couldn't find numeric setter for: " + name);

    }
}
