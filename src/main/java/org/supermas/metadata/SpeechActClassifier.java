package org.supermas.metadata;

import java.util.List;

/** Labels the sender's new text. An email may have several acts or none. */
public interface SpeechActClassifier {
    List<SpeechAct> classify(String currentText) throws Exception;
}
