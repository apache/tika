package org.apache.tika.detect.zip;

import java.util.HashMap;
import java.util.Map;

class StreamingDetectContext {

    /** Serial version UID. */
    private static final long serialVersionUID = -5921436862145826534L;

    /** Map of objects in this context */
    private final Map<String, Object> context = new HashMap<String, Object>();

    /**
     * Adds the given value to the context as an implementation of the given
     * interface.
     *
     * @param key the interface implemented by the given value
     * @param value the value to be added, or <code>null</code> to remove
     */
    public <T> void set(Class<T> key, T value) {
        if (value != null) {
            context.put(key.getName(), value);
        } else {
            context.remove(key.getName());
        }
    }

    /**
     * Returns the object in this context that implements the given interface.
     *
     * @param key the interface implemented by the requested object
     * @return the object that implements the given interface,
     *         or <code>null</code> if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) context.get(key.getName());
    }

    /**
     * Returns the object in this context that implements the given interface,
     * or the given default value if such an object is not found.
     *
     * @param key the interface implemented by the requested object
     * @param defaultValue value to return if the requested object is not found
     * @return the object that implements the given interface,
     *         or the given default value if not found
     */
    public <T> T get(Class<T> key, T defaultValue) {
        T value = get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    public void remove(Class key) {
        context.remove(key);
    }
}
