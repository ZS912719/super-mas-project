package org.supermas.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.supermas.agent.role.LlmRequestExtractor;
import org.supermas.agent.role.RequestExtractor;
import org.supermas.agent.role.RuleRequestExtractor;
import org.supermas.llm.LlmSettings;
import org.supermas.llm.LocalLlm;
import org.supermas.llm.OpenAiCompatibleLlm;
import org.supermas.llm.RecordedLlm;
import org.supermas.metadata.QuoteSignatureStripper;
import org.supermas.model.Domain.ModelConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Benchmark plan C13: does a request extractor find as many requests as the gold says? On the 12 multi-request
 * emails (gold {@code requests} of {@code multi-gold/v1}), the rule extractor always runs; with a model configured
 * ({@code SUPERMAS_LLM_*} or {@code --llm-*} flags) the LLM extractor runs too, {@code --repeats} times. The LLM
 * extractor accepts only verbatim quotes and falls back to the rules otherwise; fallbacks are counted. Writes
 * counts only.
 *
 * <p>Usage: {@code RequestExtractionBenchmark REPORT_FILE [--repeats N] [--llm-... VALUE]}
 */
public final class RequestExtractionBenchmark {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path MULTI = Path.of("benchmark/data/synthetic-multi");

    private RequestExtractionBenchmark() { }

    record Case(String name, String text, String language, int gold) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: RequestExtractionBenchmark REPORT_FILE [--repeats N] [--llm-... VALUE]");
        }
        int repeats = 1;
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (args[i].equals("--repeats")) {
                repeats = Integer.parseInt(args[i + 1]);
            } else if (LlmSettings.isFlag(args[i])) {
                flags.put(args[i], args[i + 1]);
            } else {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        LlmSettings llm = LlmSettings.from(System.getenv(), flags);
        List<Case> cases = load();
        Map<String, List<Integer>> counts = new LinkedHashMap<>();
        Map<String, Integer> fallbacks = new LinkedHashMap<>();
        RuleRequestExtractor rules = new RuleRequestExtractor();
        counts.put("rules", extract(rules, cases, null));
        if (llm.provider() != LlmSettings.Provider.TEMPLATE) {
            LocalLlm engine = switch (llm.provider()) {
                case LOCAL -> new OpenAiCompatibleLlm();
                case ONLINE -> OpenAiCompatibleLlm.online(llm.apiKey().orElseThrow());
                case REPLAY -> RecordedLlm.replay(llm.cache().orElseThrow());
                default -> throw new IllegalStateException("Unhandled provider " + llm.provider());
            };
            if (llm.provider() != LlmSettings.Provider.REPLAY && llm.cache().isPresent()) {
                engine = RecordedLlm.recording(engine, llm.cache().get());
            }
            ModelConfig config = new ModelConfig(llm.model(), llm.endpoint(), llm.provider() == LlmSettings.Provider.ONLINE
                    ? PrototypeApp.ONLINE_TIMEOUT : PrototypeApp.LLM_TIMEOUT, PrototypeApp.LLM_MAX_OUTPUT);
            LlmRequestExtractor extractor = new LlmRequestExtractor(engine, config);
            for (int r = 1; r <= repeats; r++) {
                int[] fallback = new int[1];
                counts.put("llm r" + r, extract(extractor, cases, fallback));
                fallbacks.put("llm r" + r, fallback[0]);
            }
        }
        String report = render(cases, counts, fallbacks, llm);
        Files.writeString(Path.of(args[0]), report);
        System.out.print(report);
    }

    static List<Case> load() throws Exception {
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        List<Case> cases = new ArrayList<>();
        for (ReplyQualityBenchmark.Item item : ReplyQualityBenchmark.load("multi", MULTI)) {
            JsonNode gold = JSON.readTree(MULTI.resolve(item.name() + ".gold.json").toFile());
            cases.add(new Case(item.name(), stripper.currentText(item.email().body()), item.gold().language(),
                    gold.path("requests").asInt()));
        }
        return cases;
    }

    private static List<Integer> extract(RequestExtractor extractor, List<Case> cases, int[] fallback) {
        List<Integer> found = new ArrayList<>();
        for (Case c : cases) {
            RequestExtractor.Requests requests = extractor.extract(c.text(), c.language());
            if (fallback != null && LlmRequestExtractor.FALLBACK_SOURCE.equals(requests.source())) {
                fallback[0]++;
            }
            found.add(requests.requests().size());
        }
        return found;
    }

    static String render(List<Case> cases, Map<String, List<Integer>> counts, Map<String, Integer> fallbacks, LlmSettings llm) {
        StringBuilder out = new StringBuilder("# Request Extraction (benchmark plan C13)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z on the ").append(cases.size())
                .append(" multi-request synthetic emails (gold request counts of `multi-gold/v1`; ")
                .append(cases.stream().mapToInt(Case::gold).sum()).append(" requests). Model: ").append(llm.describe())
                .append(".\n\n| Extractor | Count exact | Too few | Too many | Requests found / gold | Fell back to rules |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        int gold = cases.stream().mapToInt(Case::gold).sum();
        for (Map.Entry<String, List<Integer>> e : counts.entrySet()) {
            List<Integer> n = e.getValue();
            int exact = 0, few = 0, many = 0, total = 0;
            for (int i = 0; i < cases.size(); i++) {
                int g = cases.get(i).gold(), f = n.get(i);
                exact += f == g ? 1 : 0;
                few += f < g ? 1 : 0;
                many += f > g ? 1 : 0;
                total += f;
            }
            out.append("| ").append(e.getKey()).append(" | ").append(exact).append(" / ").append(cases.size()).append(" | ")
                    .append(few).append(" | ").append(many).append(" | ").append(total).append(" / ").append(gold).append(" | ")
                    .append(fallbacks.containsKey(e.getKey()) ? String.valueOf(fallbacks.get(e.getKey())) : "—").append(" |\n");
        }
        out.append("\n| Email | Gold |");
        counts.keySet().forEach(k -> out.append(' ').append(k).append(" |"));
        out.append("\n| --- | --- |");
        counts.keySet().forEach(k -> out.append(" --- |"));
        for (int i = 0; i < cases.size(); i++) {
            out.append("\n| ").append(cases.get(i).name()).append(" | ").append(cases.get(i).gold()).append(" |");
            for (List<Integer> n : counts.values()) {
                out.append(' ').append(n.get(i)).append(" |");
            }
        }
        return out.append("\n").toString();
    }
}
