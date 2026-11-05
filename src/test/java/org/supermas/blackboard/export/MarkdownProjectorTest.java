package org.supermas.blackboard.export;

import org.junit.jupiter.api.*;
import org.supermas.model.Domain.*;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.blackboard.testing.BlackboardTestSupport;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownProjectorTest extends BlackboardTestSupport {
    @Test void knowledgeEditsDoNotMutateAuthoritativeStateAndTokensAreNotExported() throws Exception {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE);
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("vault"))) {
            projector.reconcile(); long revision = board.versionedSnapshot().revision();
            Files.writeString(root.resolve("vault/knowledge/prompt.md"), "Manually edited knowledge");
            assertEquals(revision, board.versionedSnapshot().revision());
            try (var entries = Files.list(projector.boardDirectory(id).resolve("generations"))) {
                String markdown = Files.readString(entries.findFirst().orElseThrow().resolve("overview.md"));
                assertFalse(markdown.contains(lease.token())); assertTrue(markdown.contains(agent.systemPrompt()));
            }
        }
    }

    @Test void oldDocumentRemainsValidButReportsItsRevisionLag() throws Exception {
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("vault"))) {
            projector.reconcile(); long old = board.versionedSnapshot().revision();
            claim(RecoveryPolicy.RETRY_SAFE);
            MarkdownProjector.Audit audit = projector.audit(id);
            assertTrue(audit.valid()); assertEquals(old, audit.documentRevision()); assertTrue(audit.databaseRevision() > old);
            projector.reconcile(); assertEquals(board.versionedSnapshot().revision(), projector.audit(id).documentRevision());
        }
    }

    @Test void crashBeforePublicationPreservesOldEntryAndRetryConverges() throws Exception {
        Path vault = root.resolve("vault");
        try (MarkdownProjector projector = new MarkdownProjector(store, vault)) { projector.reconcile(); }
        long old = board.versionedSnapshot().revision(); claim(RecoveryPolicy.RETRY_SAFE);
        try (MarkdownProjector broken = new MarkdownProjector(store, vault, stage -> {
            if (stage == MarkdownProjector.Stage.AFTER_RENDER) throw new IOException("Injected interruption");
        })) {
            assertThrows(IOException.class, broken::reconcile); assertEquals(old, broken.audit(id).documentRevision());
        }
        try (MarkdownProjector recovered = new MarkdownProjector(store, vault)) {
            recovered.reconcile(); assertEquals(board.versionedSnapshot().revision(), recovered.audit(id).documentRevision());
        }
    }

    @Test void crashAfterPublicationBeforeAcknowledgementIsIdempotentlyRecovered() throws Exception {
        Path vault = root.resolve("vault");
        try (MarkdownProjector broken = new MarkdownProjector(store, vault, stage -> {
            if (stage == MarkdownProjector.Stage.AFTER_PUBLISH) throw new IOException("Injected interruption");
        })) {
            assertThrows(IOException.class, broken::reconcile); assertTrue(broken.audit(id).valid());
            assertEquals(0, store.projectionStatus(id).acknowledgedRevision());
        }
        try (MarkdownProjector recovered = new MarkdownProjector(store, vault)) {
            recovered.reconcile();
            assertEquals(store.projectionStatus(id).desiredRevision(), store.projectionStatus(id).acknowledgedRevision());
        }
    }

    @Test void semanticAuditDetectsCorruptionEvenWithRecomputedManifestHash() throws Exception {
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("vault"))) {
            projector.reconcile();
            Path folder;
            try (var entries = Files.list(projector.boardDirectory(id).resolve("generations"))) { folder = entries.findFirst().orElseThrow(); }
            String altered = Files.readString(folder.resolve("overview.md")).replace("PENDING", "SUCCEEDED");
            Files.writeString(folder.resolve("overview.md"), altered);
            MarkdownProjector.Manifest manifest = JsonCodec.read(Files.readString(folder.resolve("manifest.json")), MarkdownProjector.Manifest.class);
            Files.writeString(folder.resolve("manifest.json"), JsonCodec.write(new MarkdownProjector.Manifest(1, manifest.boardKey(), manifest.revision(), JsonCodec.hash(altered))));
            assertFalse(projector.audit(id).valid()); projector.reconcile(); assertTrue(projector.audit(id).valid());
            Files.delete(projector.boardDirectory(id).resolve("current.md"));
            projector.reconcile(); assertTrue(projector.audit(id).valid());
        }
    }

    @Test void publisherLockPreventsConcurrentPublication() throws Exception {
        Path vault = root.resolve("vault"); Files.createDirectories(vault);
        try (FileChannel channel = FileChannel.open(vault.resolve(".supermas-projector.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock(); MarkdownProjector projector = new MarkdownProjector(store, vault)) {
            assertThrows(IOException.class, projector::reconcile);
        }
    }

    @Test void vaultCannotSilentlySwitchAuthorities() throws Exception {
        Path vault = root.resolve("vault");
        try (MarkdownProjector first = new MarkdownProjector(store, vault)) { first.reconcile(); }
        SqliteBlackboardStore other = new SqliteBlackboardStore(root.resolve("another.db"));
        try (MarkdownProjector second = new MarkdownProjector(other, vault)) { assertThrows(IOException.class, second::reconcile); }
    }

    @Test void automaticWorkerRetriesAfterPublicationFailure() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("vault"), stage -> {
            if (fail.getAndSet(false)) throw new IOException("Transient disk failure");
        })) {
            projector.start(Duration.ofMillis(50));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && store.projectionStatus(id).acknowledgedRevision() != board.versionedSnapshot().revision())
                Thread.sleep(25);
            assertTrue(projector.audit(id).valid());
            assertEquals(board.versionedSnapshot().revision(), store.projectionStatus(id).acknowledgedRevision());
        }
    }

    @Test void actualProcessHaltAfterPublishLeavesRecoverableDiskAndOutbox() throws Exception {
        Process process = probe(MarkdownCrashProbe.class, "crash-publish", "unused");
        assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(71, process.exitValue());
        assertEquals(0, store.projectionStatus(id).acknowledgedRevision());
        try (MarkdownProjector recovered = new MarkdownProjector(store, root.resolve("vault"))) {
            assertTrue(recovered.audit(id).valid()); recovered.reconcile();
            assertEquals(board.versionedSnapshot().revision(), store.projectionStatus(id).acknowledgedRevision());
        }
    }
}
