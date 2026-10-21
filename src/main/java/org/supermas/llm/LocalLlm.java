package org.supermas.llm;

import org.supermas.model.Domain.*;

/** Local-only inference boundary. Connection, timeout and invalid-output failures must be explicit. */
public interface LocalLlm {
    ModelResponse complete(ModelRequest request, ModelConfig config);
}
