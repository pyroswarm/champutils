package com.champutils.profession.actives;
public class LeafstormAbility extends TimedBuffAbility {
    public String id() { return "leafstorm"; }
    protected String effectId() { return "leafstorm"; }
    protected String displayName() { return "Leafstorm"; }
    protected int defaultSeconds() { return 20; }
    protected String message(int seconds) { return "§aLeafstorm active: §fleaves clear around chopped logs for §a" + seconds + "s§f."; }
}
