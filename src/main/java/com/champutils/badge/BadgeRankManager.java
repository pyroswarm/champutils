package com.champutils.badge;

public class BadgeRankManager {

    public static String getBadgeRank(
            int badges
    ){

        if(badges >= 8){
            return "Champion";
        }

        if(badges >= 6){
            return "Master Trainer";
        }

        if(badges >= 4){
            return "Ace Trainer";
        }

        if(badges >= 2){
            return "Gym Challenger";
        }

        if(badges >= 1){
            return "Rookie Trainer";
        }

        return "Unranked";
    }

}