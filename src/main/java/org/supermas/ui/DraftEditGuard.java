package org.supermas.ui;

/** FX-thread-owned edit revision: a background result may only replace its unchanged input view. */
public final class DraftEditGuard {
    private long revision;
    public void changed() { revision++; }
    public long capture() { return revision; }
    public boolean accepts(long captured) { return revision == captured; }
}
