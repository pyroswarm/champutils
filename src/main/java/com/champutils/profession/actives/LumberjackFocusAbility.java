package com.champutils.profession.actives;
public class LumberjackFocusAbility extends TimedBuffAbility {
    public String id() { return "lumberjack_focus"; }
    protected String effectId() { return "lumberjack_focus"; }
    protected String displayName() { return "Lumberjack Focus"; }
    protected int defaultSeconds() { return 20; }
    protected String message(int seconds) { return "§2Lumberjack Focus active: §fforestry passive chances boosted for §a" + seconds + "s§f."; }
}
