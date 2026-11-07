package org.supermas.ui.demo;

import com.google.gson.Gson;
import org.supermas.composition.CompositionService.*;
import org.supermas.llm.LocalLlm;
import org.supermas.model.Domain.ModelResponse;
import org.supermas.ui.WorkspaceProvider;
import java.util.*;

/** Fixed presentation fixtures only: no agents, scheduling, persistence or authoritative review. */
public final class DemoWorkspace implements WorkspaceProvider {
    public List<Entry> entries() {
        return List.of(
                new Entry("sample-course", "student@example.test", "DEMONSTRATION: fixed StudentAgent and TeacherAgent contributions.",
                        new Input("Course schedule", "Hello, when does the course start and where is the syllabus?",
                                List.of(new Contribution("demo-evidence-001", "sample course sheet", "StudentAgent", Kind.FACT, "The course starts on Monday."),
                                        new Contribution("demo-evidence-002", "sample staff note", "TeacherAgent", Kind.FACT, "The syllabus is available on the course portal.")),
                                false, false, "English", "friendly and professional", List.of(), 16000)),
                new Entry("sample-meeting", "researcher@example.test", "DEMONSTRATION: fixed ResearchProjectAgent contribution.",
                        new Input("Project meeting", "Hello, could you confirm the project meeting location?",
                                List.of(new Contribution("demo-evidence-003", "sample project note", "ResearchProjectAgent", Kind.FACT, "The meeting takes place in room B12.")),
                                false, false, "English", "professional", List.of(), 16000)),
                new Entry("sample-conflict", "office@example.test", "DEMONSTRATION: unresolved conflict; composition must be blocked.",
                        new Input("Conflicting deadline", "Which deadline applies?",
                                List.of(new Contribution("demo-evidence-004", "sample notice A", "AdministrationAgent", Kind.FACT, "The deadline is Monday."),
                                        new Contribution("demo-evidence-005", "sample notice B", "TeacherAgent", Kind.FACT, "The deadline is Tuesday.")),
                                false, true, "English", "professional", List.of(), 16000)));
    }

    public static LocalLlm simulatedModel() {
        Gson json = new Gson();
        return (request, config) -> {
            Input input = json.fromJson(request.untrustedData(), Input.class);
            String body = input.subject().equals("Course schedule")
                    ? "Hello,\n\nThe course starts on Monday. The syllabus is available on the course portal.\n\nBest regards"
                    : "Hello,\n\nThe project meeting takes place in room B12.\n\nBest regards";
            String output = json.toJson(Map.of("subject", "Re: " + input.subject(), "body", body,
                    "language", input.language(), "usedEvidenceIds", input.contributions().stream().map(Contribution::id).toList(),
                    "commitments", List.of()));
            return new ModelResponse(Optional.of(output), Optional.empty());
        };
    }
}
