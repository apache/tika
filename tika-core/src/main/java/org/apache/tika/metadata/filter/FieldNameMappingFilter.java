package org.apache.tika.metadata.filter;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldNameMappingFilter implements MetadataFilter {
    private static String MAPPING_OPERATOR = "->";

    Map<String, String> mapping = new HashMap<>();

    boolean excludeUnmapped = true;

    @Override
    public void filter(Metadata metadata) throws TikaException {
        if (excludeUnmapped) {
            for (String n : metadata.names()) {
                if (mapping.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mapping.get(n), val);
                    }
                } else {
                    mapping.remove(n);
                }
            }
        } else {
            for (String n : metadata.names()) {
                if (mapping.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mapping.get(n), val);
                    }
                }
            }
        }
    }

    /**
     * If this is <code>true</code> (default), this means that only the fields that
     * have a "from" value in the mapper will be passed through.  Otherwise,
     * this will pass through all keys/values and mutate the keys
     * that exist in the mappings.
     * @param excludeUnmapped
     */
    @Field
    public void setExcludeUnmapped(boolean excludeUnmapped) {
        this.excludeUnmapped = excludeUnmapped;
    }

    @Field
    public void setMappings(List<String> mappings) {
        for (String m : mappings) {
            String[] args = m.split(MAPPING_OPERATOR);
            if (args.length == 0 || args.length == 1) {
                throw new IllegalArgumentException(
                        "Can't find mapping operator '->' in: " + m);
            } else if (args.length > 2) {
                throw new IllegalArgumentException(
                        "Must have only one mapping operator. I found more than one: " + m
                );
            }
            String from = args[0].trim();
            if (from.length() == 0) {
                throw new IllegalArgumentException("Must contain content before the "+
                        "mapping operator '->'");
            }
            String to = args[1].trim();
            if (to.length() == 0) {
                throw new IllegalArgumentException("Must contain content after the "+
                        "mapping operator '->'");
            }
            mapping.put(from, to);
        }
    }
}
