package com.champutils.badge;

import java.util.HashSet;
import java.util.Set;

public class BadgeData {

    private final Set<BadgeType> earnedBadges =
            new HashSet<>();


    public BadgeData() {
    }


/* =========================
   BADGE CHECKS
========================= */

    public boolean hasBadge(
            BadgeType badge
    ){
        return earnedBadges.contains(
                badge
        );
    }


    public boolean addBadge(
            BadgeType badge
    ){

        if(
                earnedBadges.contains(
                        badge
                )
        ){
            return false;
        }

        earnedBadges.add(
                badge
        );

        return true;
    }


    public boolean removeBadge(
            BadgeType badge
    ){
        return earnedBadges.remove(
                badge
        );
    }


/* =========================
   STATS
========================= */

    public int getBadgeCount(){
        return earnedBadges.size();
    }


    public boolean hasAllBadges(){

        return earnedBadges.size()
                >=
                BadgeType.values().length;
    }


/* =========================
   DATA ACCESS
========================= */

    public Set<BadgeType> getBadges(){

        return new HashSet<>(
                earnedBadges
        );
    }


    public void setBadges(
            Set<BadgeType> badges
    ){

        earnedBadges.clear();

        earnedBadges.addAll(
                badges
        );
    }


/* =========================
   UNLOCK HELPERS
========================= */

    public boolean hasAtLeast(
            int amount
    ){

        return getBadgeCount()
                >=
                amount;
    }


/* =========================
   DEBUG
========================= */

    @Override
    public String toString(){

        return "BadgeData{" +
                "badges=" +
                earnedBadges +
                '}';
    }

}