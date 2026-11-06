package org.supermas.agent.role;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.supermas.context.InformationField;

/**
 * One specialized agent, as written in its JSON file. This is trusted configuration: it is written by whoever
 * maintains the assistant, never built from mail. Adding or changing a role means editing a file, not code.
 *
 * @param id unique identifier, lower case, letters, digits and '-'
 * @param kind which role interface the agent carries: student, teacher, research, administration, generic, or other
 * @param prompt the role's prompt file name, in the catalogue's prompt folder
 * @param roleHints MetadataAgent role hints this role answers, for example "student"
 * @param keywords lower-case words or phrases that make an email fit this role
 * @param capabilities what the agent may do, for example "draft-reply"
 * @param contextSources sources the agent may read, still subject to every retriever check
 * @param requiredFields what a reply of this role needs from the email or context
 * @param rules trusted instructions added to the system prompt, one sentence each
 * @param fallback true for the one agent that answers when no role fits
 * @param requiresVerifiedSender if true, the role only fits mail from a verified sender
 * @param maxContextCharacters budget for evidence chosen for this agent
 * @param maxInputCharacters total budget of one model request, system prompt included
 */
public record RoleDefinition(String id, String kind, String displayName, String prompt, Set<String> roleHints,
                             List<String> keywords, Set<String> capabilities, Set<String> contextSources,
                             List<InformationField> requiredFields, List<String> rules, boolean fallback,
                             boolean requiresVerifiedSender, int maxContextCharacters, int maxInputCharacters) {
    public RoleDefinition {
        roleHints = Set.copyOf(roleHints == null ? Set.of() : roleHints);
        keywords = (keywords == null ? List.<String>of() : keywords).stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT)).toList();
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        contextSources = Set.copyOf(contextSources == null ? Set.of() : contextSources);
        requiredFields = List.copyOf(requiredFields == null ? List.of() : requiredFields);
        rules = List.copyOf(rules == null ? List.of() : rules);
    }
}
