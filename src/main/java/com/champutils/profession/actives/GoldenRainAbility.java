package com.champutils.profession.actives;
public class GoldenRainAbility extends TimedBuffAbility {
    public String id() { return "golden_rain"; }
    protected String effectId() { return "golden_rain"; }
    protected String displayName() { return "Golden Rain"; }
    protected int defaultSeconds() { return 20; }
    protected String message(int seconds) { return "§6Golden Rain active: §ffarming passive chances boosted for §e" + seconds + "s§f."; }
}
