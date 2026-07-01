package com.champutils.tutorial;

import java.util.List;

public final class SpawnGuideNpc {
    public final String id;
    public final String displayName;
    public final List<String> lines;

    public SpawnGuideNpc(String id, String displayName, List<String> lines) {
        this.id = id;
        this.displayName = displayName;
        this.lines = List.copyOf(lines);
    }
}
