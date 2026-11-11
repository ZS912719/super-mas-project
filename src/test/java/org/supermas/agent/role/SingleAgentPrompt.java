package org.supermas.agent.role;

import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.ModelRequest;

import java.util.List;
import java.util.Optional;

/**
 * Benchmark plan W2: the model request of the single-agent baseline, built by the same {@link RolePromptBuilder} as
 * the composer's, so the email, addresses and documents reach the model in the same layout. There is no reply plan
 * ({@code {}}) and no feedback. Test scope only: the baseline is a comparison, not a part of the system.
 */
public final class SingleAgentPrompt {
    private SingleAgentPrompt() { }

    public static ModelRequest request(String systemPrompt, Email email, String currentText, List<Evidence> documents,
                                       int maxInputCharacters) {
        return RolePromptBuilder.build("single-agent", systemPrompt, email.sender(), email.recipients(), email.subject(),
                currentText, "{}", documents, Optional.empty(), maxInputCharacters).request();
    }
}
