package org.apache.tika.pipes.core.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Props {
    private static final Logger log = LoggerFactory.getLogger(Props.class);

    /**
     * First check for the prop in env variables.
     * Then check for the prop in system properties by using a "tika." prefix to the property.
     * Finally use the defaultVal as a last resort.
     * @param prop The property name we are fetching.
     * @param defaultVal The default value to use if you can't find it.
     * @return The value of the prop.
     */
    public static String getProp(String prop, String defaultVal) {
        log.debug("Resolving property - envProp={}, envPropValue={}, sysProp={}, sysPropValue={}, " +
                "defaultValue={}", prop, System.getenv(prop), "tika." + prop, System.getProperty("tika." + prop), defaultVal);
        String res = System.getenv(prop);
        if (StringUtils.isBlank(res)) {
            res = System.getProperty("tika." + prop, defaultVal);
        }
        return res;
    }

}
