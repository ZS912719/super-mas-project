package org.supermas.llm;

import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.*;

/** Model input and outward draft output are separate from internal evidence and control records. */
public interface GenerationService {
    /** Enforce the total input budget, preserve trusted instruction boundaries and select context explicitly. */
    ModelRequest prepare(BlackboardView board, AgentConfig agent, ContextSelection context, ExecutionLimits limits);

    /** Validate output as outward subject/body; never concatenate internal records into the reply. */
    GenerationResult generate(ModelRequest request, ModelConfig config);
}
