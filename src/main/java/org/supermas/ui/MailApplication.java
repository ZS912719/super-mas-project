package org.supermas.ui;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.supermas.composition.CompositionService;
import org.supermas.llm.OllamaLlm;
import org.supermas.llm.LocalLlm;
import org.supermas.model.Domain.*;
import org.supermas.ui.demo.DemoWorkspace;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/** Small presentation-only workspace. No authoritative processing, review or email transport. */
public final class MailApplication extends Application {
    private final LocalLlm demonstrationLlm;
    private final LocalLlm localLlm = new OllamaLlm();
    public MailApplication() { this(DemoWorkspace.simulatedModel()); }
    MailApplication(LocalLlm demonstrationLlm) { this.demonstrationLlm = demonstrationLlm; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "composition-worker"); thread.setDaemon(true); return thread;
    });
    private final DraftEditGuard edits = new DraftEditGuard();
    // Unsaved presentation buffers only; not authoritative draft versions or review state.
    private record EditorBuffer(String subject, String body, DraftProposal original) { }
    private final Map<String, EditorBuffer> buffers = new HashMap<>();
    private DraftProposal originalGeneration;
    private final TextField subject = new TextField();
    private final TextArea body = new TextArea();
    private final TextArea email = readOnly();
    private final TextArea activity = readOnly();
    private final TextArea contributions = readOnly();
    private final Label status = new Label("Select a sample email.");
    private final Label provenance = new Label();
    private final Button compose = new Button("Compose proposal");
    private final Button cancel = new Button("Cancel inference");
    private final TextField endpoint = new TextField("http://127.0.0.1:11434");
    private final TextField model = new TextField("llama3.2");
    private final TextField timeout = new TextField("60");
    private final CheckBox realEngine = new CheckBox("Use installed local Ollama instead of simulated responses");
    private WorkspaceProvider.Entry selected;
    private Task<GenerationResult> active;

    @Override public void start(Stage stage) {
        stage.setTitle("Super MAS — Person 3 demonstration");
        stage.setScene(new Scene(workspace(new DemoWorkspace()), 1180, 760));
        stage.show();
    }

    BorderPane workspace(WorkspaceProvider provider) {
        subject.setId("draft-subject");
        body.setId("draft-body");
        provenance.setId("draft-provenance");
        status.setId("status");
        endpoint.setId("llm-endpoint");
        model.setId("llm-model");
        realEngine.setId("real-engine");
        Label banner = new Label("DEMONSTRATION DATA — no live agents, authoritative review or email sending.");
        banner.setStyle("-fx-font-weight: bold; -fx-text-fill: #805400;");
        ListView<WorkspaceProvider.Entry> inbox = new ListView<>();
        inbox.setId("inbox");
        inbox.getItems().setAll(provider.entries());
        inbox.setPrefWidth(210);
        inbox.getSelectionModel().selectedItemProperty().addListener((o, old, entry) -> select(entry));
        subject.textProperty().addListener((o, old, text) -> editorChanged());
        body.textProperty().addListener((o, old, text) -> editorChanged());
        body.setWrapText(true);
        provenance.setWrapText(true);
        Button approve = new Button("Approve — unavailable");
        approve.setDisable(true);
        approve.setTooltip(new Tooltip("Requires Person 1's authoritative version-aware review service."));
        Button reject = new Button("Demo: reject proposal");
        reject.setOnAction(e -> {
            cancelActive();
            edits.changed();
            status.setText("Demonstration rejection only. Nothing was persisted or rescheduled.");
        });
        compose.setOnAction(e -> compose());
        cancel.setDisable(true);
        cancel.setOnAction(e -> {
            cancelActive();
            status.setText("Inference cancelled; no result will be applied.");
        });
        VBox editor = new VBox(8, new Label("Editable proposal — unapproved"), subject, body, provenance,
                new HBox(8, compose, cancel), new HBox(8, approve, reject));
        VBox.setVgrow(body, Priority.ALWAYS);
        TabPane details = new TabPane(tab("Email", email), tab("Processing information", activity), tab("Agents and contributions", contributions));
        SplitPane split = new SplitPane(inbox, details, editor);
        split.setDividerPositions(0.19, 0.56);
        GridPane settings = new GridPane();
        settings.setHgap(8); settings.setVgap(6);
        settings.addRow(0, new Label("Local endpoint"), endpoint, new Label("Installed model"), model);
        settings.addRow(1, new Label("Timeout (seconds)"), timeout, realEngine);
        Label settingsNote = new Label("Session-only settings. Ollama must already be installed with cloud disabled. Agent configuration awaits Person 2's API.");
        settingsNote.setWrapText(true);
        VBox footer = new VBox(8, new TitledPane("LLM settings", new VBox(8, settings, settingsNote)), status);
        status.setWrapText(true);
        BorderPane root = new BorderPane(split, banner, null, footer, null);
        root.setPadding(new Insets(12));
        BorderPane.setMargin(split, new Insets(12, 0, 12, 0));
        inbox.getSelectionModel().selectFirst();
        return root;
    }

    private void select(WorkspaceProvider.Entry entry) {
        if (selected != null) {
            buffers.put(selected.id(), new EditorBuffer(subject.getText(), body.getText(), originalGeneration));
        }
        cancelActive();
        edits.changed();
        selected = entry;
        EditorBuffer buffer = entry == null ? null : buffers.get(entry.id());
        originalGeneration = buffer == null ? null : buffer.original();
        subject.setText(buffer == null ? "" : buffer.subject());
        body.setText(buffer == null ? "" : buffer.body());
        refreshProvenance();
        if (entry == null) { compose.setDisable(true); return; }
        compose.setDisable(false);
        email.setText("From: " + entry.sender() + "\nSubject: " + entry.input().subject() + "\n\n" + entry.input().body());
        activity.setText(entry.processingInfo() + "\n\nNo live Blackboard is connected.");
        contributions.setText(String.join("\n\n", entry.input().contributions().stream().map(c ->
                c.agentId() + " — " + c.kind() + "\n" + c.text() + "\nSource: " + c.source() + "\nID: " + c.id()).toList()));
        status.setText("Sample selected. Edit the proposal or compose from the supplied fixtures.");
    }

    private void compose() {
        if (selected == null) return;
        final ModelConfig configuration;
        try {
            configuration = new ModelConfig(model.getText().trim(), URI.create(endpoint.getText().trim()),
                    Duration.ofSeconds(Long.parseLong(timeout.getText().trim())), 12000);
            OllamaLlm.validateConfig(configuration);
        } catch (RuntimeException e) {
            status.setText("Invalid LLM settings: use a loopback base URL, model and timeout of 1–600 seconds."); return;
        }
        var input = selected.input();
        String entryId = selected.id();
        long revision = edits.capture();
        boolean local = realEngine.isSelected();
        CompositionService service = new CompositionService(local ? localLlm : demonstrationLlm);
        Task<GenerationResult> task = new Task<>() {
            @Override protected GenerationResult call() { return service.generate(service.prepare(input), configuration); }
        };
        active = task; compose.setDisable(true); cancel.setDisable(false);
        status.setText(local ? "Calling local Ollama with selected sample data…" : "Composing with a fixed simulated response…");
        task.setOnSucceeded(e -> {
            if (active != task || selected == null || !selected.id().equals(entryId)) return;
            finish();
            if (!edits.accepts(revision)) { status.setText("Response discarded because the proposal was edited while inference ran."); return; }
            GenerationResult result = task.getValue();
            if (result.failure().isPresent()) { status.setText(result.failure().get().message()); return; }
            DraftProposal proposal = result.draft().orElseThrow();
            originalGeneration = proposal;
            subject.setText(proposal.subject());
            body.setText(proposal.body());
            refreshProvenance();
            status.setText((local ? "Local model proposal" : "Simulated proposal") + " — review required. No approval or sending occurred.");
        });
        task.setOnFailed(e -> {
            if (active != task) return;
            finish();
            status.setText(task.getException() instanceof IllegalArgumentException
                    ? task.getException().getMessage() : "Composition failed. No proposal was applied.");
        });
        worker.submit(task);
    }
    private void editorChanged() {
        edits.changed();
        refreshProvenance();
    }
    private void refreshProvenance() {
        if (originalGeneration == null) {
            provenance.setText("");
            return;
        }
        boolean modified = !subject.getText().equals(originalGeneration.subject())
                || !body.getText().equals(originalGeneration.body());
        provenance.setText((modified ? "Manually edited. Evidence below belongs to the original generation only.\n" : "")
                + originalGeneration.explanation());
    }
    private void finish() {
        active = null;
        compose.setDisable(selected == null);
        cancel.setDisable(true);
    }
    private void cancelActive() {
        if (active != null) active.cancel(true);
        finish();
    }
    private static TextArea readOnly() { TextArea text = new TextArea(); text.setEditable(false); text.setWrapText(true); return text; }
    private static Tab tab(String title, javafx.scene.Node content) { Tab tab = new Tab(title, content); tab.setClosable(false); return tab; }
    @Override public void stop() { cancelActive(); worker.shutdownNow(); }
    public static void main(String[] args) { launch(args); }
}
