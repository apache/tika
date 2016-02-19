package org.apache.tika.language.translate;

import java.io.IOException;

import org.apache.tika.langdetect.LanguageDetector;
import org.apache.tika.langdetect.LanguageResult;
import org.apache.tika.langdetect.OptimaizeLangDetector;


public abstract class AbstractTranslator implements Translator {

	protected LanguageResult detectLanguage(String text) throws IOException {
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        return detector.detect(text);
	}
}
