package org.supermas.control;

import java.util.Optional;
import org.supermas.model.Domain.BoardId;

/**
 * A coordination strategy that explains its choices: after {@code next}, the coordinator takes the decision and
 * writes it on the board before running the chosen action. Strategies without it are simply not recorded.
 */
public interface ControlRecording {
    /** The decision behind the last {@code next} for this board, handed over once. */
    Optional<ControlEntry> decision(BoardId board);
}
