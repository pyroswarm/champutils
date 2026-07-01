package com.champutils.tutorial;

import java.util.HashSet;
import java.util.Set;

public final class TutorialState {
    public final Set<String> completedNpcIds = new HashSet<>();
    public boolean skipped;
    public boolean completed;
    public boolean rewardClaimed;
    public boolean loaded;

    public boolean shouldShowNotifiers() {
        return loaded && !skipped && !completed;
    }
}
