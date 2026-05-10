package com.champutils.profession.actives;
public class HarvestWaveAbility extends TimedBuffAbility {
    public String id() { return "harvest_wave"; }
    protected String effectId() { return "harvest_wave"; }
    protected String displayName() { return "Harvest Wave"; }
    protected int defaultSeconds() { return 20; }
    protected String message(int seconds) { return "§eHarvest Wave active: §fnearby mature crops harvest for §e" + seconds + "s§f."; }
}
