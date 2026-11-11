package org.supermas.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.supermas.metadata.EmailThreader;
import org.supermas.model.Domain.Email;

/**
 * How many threads each threading variant makes on real mail without reply headers (benchmark plan C02, Enron
 * part): references only, the unrestricted subject fallback of {@link EmailThreader}, and the restricted fallback
 * the prototype applies on receipt ({@link ReceiptThreading}). There is no thread gold for Enron, so this measures
 * how much each variant merges, the risk the restriction is meant to limit. Same mailboxes and duplicate removal as
 * {@link C1Benchmark} (B08). Writes counts only.
 *
 * <p>Usage: {@code EnronThreadingStats MAILDIR MIN_MESSAGES REPORT_FILE}
 */
public final class EnronThreadingStats {
    private EnronThreadingStats() { }

    public static void main(String[] args) throws Exception {
        Path maildir = Path.of(args[0]);
        int minMessages = Integer.parseInt(args[1]);
        C1Benchmark.Counts counts = new C1Benchmark.Counts();
        List<Path> mailboxes;
        try (Stream<Path> listing = Files.list(maildir)) {
            mailboxes = listing.filter(Files::isDirectory).sorted().toList();
        }
        StringBuilder out = new StringBuilder("# Threading on Enron without reply headers (benchmark plan C02)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z on `").append(maildir)
                .append("`, mailboxes in name order until ").append(minMessages).append(" unique messages (as C1, B08). ")
                .append("No thread gold: the table counts threads and merges only.\n\n")
                .append("Joined: messages put into an earlier message's thread. Quoting: of those, how many contain an ")
                .append("8-word sequence of an earlier message of that thread (quoted text, strong evidence of a real reply; ")
                .append("a lower bound of precision, since short replies often quote nothing).\n\n")
                .append("| Mailbox | Messages | Threads, references only | Threads, unrestricted fallback | Joined (unrestricted) | Quoting (unrestricted) | Threads, restricted on receipt | Joined (restricted) | Quoting (restricted) |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        int[] total = new int[8];
        int index = 0;
        for (Path mailbox : mailboxes) {
            if (counts.messages >= minMessages) {
                break;
            }
            List<C1Benchmark.Message> messages = C1Benchmark.load(mailbox, counts);
            counts.messages += messages.size();
            List<Email> emails = new ArrayList<>(messages.stream().map(C1Benchmark.Message::email).toList());
            int references = distinct(new EmailThreader(false).assignThreadIds(emails));
            Map<String, String> loose = new EmailThreader(true).assignThreadIds(emails);
            Map<String, String> receipt = ReceiptThreading.assign(emails);
            int[] looseJoins = joins(emails, loose);
            int[] receiptJoins = joins(emails, receipt);
            index++;
            // Mailboxes are numbered, not named, so the report names no Enron employee.
            int[] row = {emails.size(), references, distinct(loose), looseJoins[0], looseJoins[1], distinct(receipt),
                    receiptJoins[0], receiptJoins[1]};
            out.append("| ").append(index);
            for (int value : row) {
                out.append(" | ").append(value);
            }
            out.append(" |\n");
            for (int i = 0; i < row.length; i++) {
                total[i] += row[i];
            }
        }
        out.append("| All");
        for (int value : total) {
            out.append(" | ").append(value);
        }
        out.append(" |\n");
        Files.writeString(Path.of(args[2]), out.toString());
        System.out.print(out);
    }

    /**
     * {joined, quoting}: messages whose thread is not their own but an earlier message's, and how many of them share
     * an 8-word sequence with an earlier message of that thread.
     */
    private static int[] joins(List<Email> emails, Map<String, String> threads) {
        Map<String, List<Email>> byThread = new java.util.HashMap<>();
        emails.forEach(e -> byThread.computeIfAbsent(threads.get(e.id()), key -> new ArrayList<>()).add(e));
        int joined = 0;
        int quoting = 0;
        for (Email email : emails) {
            List<Email> thread = byThread.get(threads.get(email.id()));
            List<Email> earlier = thread.stream().filter(other -> !other.id().equals(email.id()) && other.receivedAt() != null
                    && email.receivedAt() != null && other.receivedAt().isBefore(email.receivedAt())).toList();
            if (earlier.isEmpty()) {
                continue;
            }
            joined++;
            java.util.Set<String> mine = shingles(email.body());
            if (earlier.stream().anyMatch(other -> shingles(other.body()).stream().anyMatch(mine::contains))) {
                quoting++;
            }
        }
        return new int[] {joined, quoting};
    }

    private static java.util.Set<String> shingles(String text) {
        String[] words = (text == null ? "" : text).toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}']+");
        List<String> kept = java.util.Arrays.stream(words).filter(word -> !word.isEmpty()).toList();
        java.util.Set<String> out = new HashSet<>();
        for (int i = 0; i + 8 <= kept.size(); i++) {
            out.add(String.join(" ", kept.subList(i, i + 8)));
        }
        return out;
    }

    private static int distinct(Map<String, String> threads) {
        return new HashSet<>(threads.values()).size();
    }
}
