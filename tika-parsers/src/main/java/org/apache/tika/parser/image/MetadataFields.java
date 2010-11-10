package org.apache.tika.parser.image;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Knowns about all declared {@link Metadata} fields.
 * Didn't find this functionality anywhere so it was added for
 * ImageMetadataExtractor, but it can be generalized.
 */
public abstract class MetadataFields {
    
    private static HashSet<String> known;
    
    static {
        known = new HashSet<String>();
        Field[] fields = Metadata.class.getFields();
        for (Field f : fields) {
            int mod = f.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
                Class<?> c = f.getType();
                if (String.class.equals(c)) {
                    try {
                        String p = (String) f.get(null);
                        if (p != null) {
                            known.add(p);
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                if (Property.class.isAssignableFrom(c)) {
                    try {
                        Property p = (Property) f.get(null);
                        if (p != null) {
                            known.add(p.getName());
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    public static boolean isMetadataField(String name) {
        return known.contains(name);
    }
    
}
