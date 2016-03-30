package org.apache.tika.language.translate;

import java.io.IOException;

import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;


public abstract class AbstractTranslator implements Translator {

	protected LanguageResult detectLanguage(String text) throws IOException {
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        return detector.detect(text);
	}
}
