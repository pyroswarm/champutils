package com.champutils.profession.actives;

import com.champutils.profession.ProfessionType;

public class TimberBurstAbility extends TimedBuffAbility {
    public String id() { return "timber_burst"; }
    protected String effectId() { return "timber_burst"; }
    protected String displayName() { return "Timber Burst"; }
    protected int defaultSeconds() { return 20; }
    protected ProfessionType durationScalingProfession() { return ProfessionType.FORESTRY; }
    protected String message(int seconds) { return "§2Timber Burst active: §fconnected logs break for §a" + seconds + "s§f."; }
}
