package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The reply checks on the three real online drafts of en-01 (step 13), and the benchmark run on the template. */
class ReplyChecksTest {
    private static final Path EN_01 = Path.of("benchmark/data/synthetic-dev/en-01.reply.json");

    @Test
    void run1WroteToTheRecipient() throws Exception {
        ReplyChecks.Score score = ReplyChecks.score(ReplyChecks.load(EN_01), """
                Dear Prof. Martin,

                I am aware that you are the course leader for CS204. To request an extension for the project, you will need to \
                provide a medical certificate. The deadline for the project is Friday 6 November.""");
        assertFalse(score.greetingOk());
        assertTrue(score.violations().contains("wrong-greeting"));
        assertEquals(List.of("decider"), score.mentioned());
        assertEquals(List.of("medical-certificate"), score.asked(), "\"you will need to provide\" asks for it");
    }

    @Test
    void run2RepeatedTheStudentNumberAndSignedWithAPlaceholder() throws Exception {
        ReplyChecks.Score score = ReplyChecks.score(ReplyChecks.load(EN_01), """
                Dear Sarah,

                The deadline for the CS204 project is Friday 6 November. Your student number is 20231187.

                To request an extension, you must name the course and include a medical certificate. The course leader decides, \
                and extensions are at most seven days.

                Best regards,
                [Your Name]""");
        assertTrue(score.greetingOk());
        assertEquals(List.of("decider", "limit"), score.mentioned());
        assertEquals(List.of("medical-certificate"), score.notAsked(), "stating the rule is not asking her for it");
        assertEquals(List.of("own-student-number", "placeholder", "signature"), score.violations());
    }

    @Test
    void run3MissedTheFactsAndARightReplyPasses() throws Exception {
        ReplyChecks.Gold gold = ReplyChecks.load(EN_01);
        ReplyChecks.Score run3 = ReplyChecks.score(gold, """
                Dear Sarah,

                The deadline for the CS204 project is Friday 6 November. Your student number is 20231187.

                Please contact the student office to request an extension. They are open Monday to Friday, 9:00 to 16:00.

                Best regards,""");
        assertEquals(List.of("decider", "limit"), run3.notMentioned());
        assertEquals(List.of("own-student-number"), run3.violations());

        ReplyChecks.Score right = ReplyChecks.score(gold, """
                Dear Sarah,

                I am sorry to hear you have been ill. The course leader decides on extensions, which are at most seven days. \
                Could you send me a medical certificate?

                Best regards,""");
        assertTrue(right.clean(), right.violations().toString());
        assertTrue(right.notMentioned().isEmpty() && right.notAsked().isEmpty());
    }

    @Test
    void languageInternalTextAndAccentsAreChecked() throws Exception {
        ReplyChecks.Gold french = ReplyChecks.load(Path.of("benchmark/data/synthetic-dev/fr-08.reply.json"));
        ReplyChecks.Score score = ReplyChecks.score(french, "Bonjour Ines,\n\nLa salle n'est pas encore annoncée, merci de patienter.");
        assertTrue(score.greetingOk(), "Ines matches Inès");
        assertTrue(score.languageOk());
        assertEquals(List.of("room-not-announced"), score.mentioned());
        ReplyChecks.Score english = ReplyChecks.score(french, "Dear Ines,\n\nThe room is not yet announced, please wait.");
        assertTrue(english.violations().contains("wrong-language"));

        ReplyChecks.Gold student = ReplyChecks.load(Path.of("benchmark/data/synthetic-multi/en-m07.reply.json"));
        assertTrue(ReplyChecks.score(student, "Dear Lea,\n\nMarks are entered within fifteen working days.\n\nBest regards,")
                .violations().contains("internal"));
        ReplyChecks.Gold staff = ReplyChecks.load(Path.of("benchmark/data/synthetic-multi/en-m08.reply.json"));
        assertFalse(ReplyChecks.score(staff, "Dear Ines,\n\nMarks are entered within fifteen working days.\n\nBest regards,")
                .violations().contains("internal"), "the marking guide is for staff");
    }

    @Test
    void revision2AcceptsTheLastNameAndFlagsASignature() throws Exception {
        ReplyChecks.Gold gold = ReplyChecks.load(Path.of("benchmark/data/synthetic-multi/en-m09.reply.json"));
        assertTrue(ReplyChecks.score(gold, "Dear Mr. Ruiz,\n\nNoted.\n\nBest regards,").greetingOk());
        assertFalse(ReplyChecks.score(gold, "Dear Dr. Martin,\n\nNoted.\n\nBest regards,").greetingOk());
        assertTrue(ReplyChecks.score(gold, "Dear Mark,\n\nNoted.\n\nBest regards,\nEmma Martin").violations().contains("signature"));
        assertFalse(ReplyChecks.signed("Dear Mark,\n\nNoted.\n\nBest regards,"));
        assertFalse(ReplyChecks.signed("Dear Mark,\n\nBest regards,\nwe will look at your request soon and reply."));
    }

    @Test
    void savedRepliesCanBeScoredAgainWithoutAModel(@TempDir Path out) throws Exception {
        Path first = ReplyQualityBenchmark.run(ReplyQualityBenchmark.parse(new String[] {"--label", "t", "--out", out.toString(),
                "--multi-repeats", "1"}, Map.of()));
        Path again = ReplyQualityBenchmark.run(ReplyQualityBenchmark.parse(new String[] {"--label", "t-rescored", "--out",
                out.toString(), "--rescore", first.toString()}, Map.of()));
        assertEquals(Files.readAllLines(first.resolve("scores.jsonl")), Files.readAllLines(again.resolve("scores.jsonl")),
                "the same checks give the same scores");
        assertTrue(Files.readString(again.resolve("results.md")).contains("scored again"));
    }

    @Test
    void everyEmailHasFrozenChecks() throws Exception {
        assertEquals(24, ReplyQualityBenchmark.load("dev", Path.of("benchmark/data/synthetic-dev")).size());
        assertEquals(12, ReplyQualityBenchmark.load("multi", Path.of("benchmark/data/synthetic-multi")).size());
    }

    @Test
    void theBenchmarkRunsOnTheTemplateAndNeverMixesRuns(@TempDir Path out) throws Exception {
        ReplyQualityBenchmark.Options options = ReplyQualityBenchmark.parse(new String[] {"--label", "template",
                "--out", out.toString(), "--dev-repeats", "1", "--multi-repeats", "1"}, Map.of());
        Path folder = ReplyQualityBenchmark.run(options);
        String results = Files.readString(folder.resolve("results.md"));
        assertTrue(results.contains("template (no model)"), results);
        assertTrue(results.contains("| multi r1, reply expected | 12 | 12 |"), results);
        assertEquals(36, Files.readAllLines(folder.resolve("scores.jsonl")).size());
        assertTrue(Files.readString(folder.resolve("drafts.md")).contains("en-m11"));
        assertThrows(IllegalArgumentException.class, () -> ReplyQualityBenchmark.run(options), "the label folder exists");
        assertThrows(IllegalArgumentException.class, () -> ReplyQualityBenchmark.parse(new String[] {"--label", "../x"}, Map.of()));
    }
}
