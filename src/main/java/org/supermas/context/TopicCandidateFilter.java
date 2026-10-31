package org.supermas.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.supermas.blackboard.BlackboardView;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * Chooses which topic agents run on an email, because each one costs a round. Scores from different types
 * do not compare, so every matching non-subject agent runs first, then the best few subject agents.
 */
public final class TopicCandidateFilter {
    private final int maxSubjects;

    public TopicCandidateFilter(int maxSubjects) {
        this.maxSubjects = maxSubjects;
    }

    private record Scored(TopicContextAgent agent, double score) { }

    /** Non-subject agents in the given order, then subject agents by falling score, then by identifier. */
    public List<TopicContextAgent> select(List<TopicContextAgent> agents, BlackboardView board) {
        Task task = new Task("candidate-filter", board.snapshot().id(), Action.RETRIEVE_CONTEXT, Optional.empty(),
                TaskStatus.PENDING, "Candidate filtering");
        List<TopicContextAgent> selected = new ArrayList<>();
        List<Scored> subjects = new ArrayList<>();
        for (TopicContextAgent agent : agents) {
            double score = score(agent, task, board);
            if (score > 0 && agent.topic().type() == TopicType.SUBJECT) {
                subjects.add(new Scored(agent, score));
            } else if (score > 0) {
                selected.add(agent);
            }
        }
        subjects.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(scored -> scored.agent().config().id()))
                .limit(maxSubjects)
                .forEach(scored -> selected.add(scored.agent()));
        return selected;
    }

    /** One faulty agent must not stop the others, so it just does not take part. */
    private static double score(TopicContextAgent agent, Task task, BlackboardView board) {
        try {
            AgentAssessment assessment = agent.assess(task, board);
            return assessment.score();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
