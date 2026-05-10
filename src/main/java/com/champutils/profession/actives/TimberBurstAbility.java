package com.champutils.profession.actives;
public class TimberBurstAbility extends TimedBuffAbility {
    public String id() { return "timber_burst"; }
    protected String effectId() { return "timber_burst"; }
    protected String displayName() { return "Timber Burst"; }
    protected int defaultSeconds() { return 20; }
    protected String message(int seconds) { return "§2Timber Burst active: §fconnected logs break for §a" + seconds + "s§f."; }
}
