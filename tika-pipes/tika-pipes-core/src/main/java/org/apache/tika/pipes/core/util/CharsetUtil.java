package org.apache.tika.pipes.core.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

public final class CharsetUtil {
  private static final Logger log = LoggerFactory.getLogger(CharsetUtil.class);

  private CharsetUtil() {
    // private for final class
  }

  public static String bestCharset(byte [] bytes) {
    CharsetDetector charsetDetector = new CharsetDetector();
    charsetDetector.setText(bytes);
    CharsetMatch detect = null;
    try {
      detect = charsetDetector.detect();
    } catch (Exception e) {
      log.error("Could not detect charset", e);
    }
    if (detect == null || StringUtils.isBlank(detect.getName()) || !Charset.availableCharsets().containsKey(detect.getName())) {
      return StandardCharsets.UTF_8.name();
    } else {
      return detect.getName();
    }
  }
}
