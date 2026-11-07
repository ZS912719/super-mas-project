package org.supermas.ui;

import org.supermas.composition.CompositionService.Input;
import java.util.List;

/** UI-local read boundary. Future adapters obtain authorized data through P1/P2 public APIs. */
public interface WorkspaceProvider {
    List<Entry> entries();
    record Entry(String id, String sender, String processingInfo, Input input) {
        @Override public String toString() { return input.subject(); }
    }
}
