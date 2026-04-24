package com.champutils.badge;

public enum BadgeType {

    BOULDER(
            "Boulder Badge",
            "Defeat Brock",
            "Rock-type gym badge"
    ),

    CASCADE(
            "Cascade Badge",
            "Defeat Misty",
            "Water-type gym badge"
    ),

    THUNDER(
            "Thunder Badge",
            "Defeat Lt. Surge",
            "Electric-type gym badge"
    ),

    RAINBOW(
            "Rainbow Badge",
            "Defeat Erika",
            "Grass-type gym badge"
    ),

    SOUL(
            "Soul Badge",
            "Defeat Koga",
            "Poison-type gym badge"
    ),

    MARSH(
            "Marsh Badge",
            "Defeat Sabrina",
            "Psychic-type gym badge"
    ),

    VOLCANO(
            "Volcano Badge",
            "Defeat Blaine",
            "Fire-type gym badge"
    ),

    EARTH(
            "Earth Badge",
            "Defeat Giovanni",
            "Ground-type gym badge"
    );


    private final String displayName;
    private final String leaderName;
    private final String description;


    BadgeType(
            String displayName,
            String leaderName,
            String description
    ){
        this.displayName = displayName;
        this.leaderName = leaderName;
        this.description = description;
    }


    public String getDisplayName(){
        return displayName;
    }


    public String getLeaderName(){
        return leaderName;
    }


    public String getDescription(){
        return description;
    }


    public static BadgeType fromString(
            String value
    ){

        try{
            return BadgeType.valueOf(
                    value.toUpperCase()
            );
        }
        catch(Exception e){
            return null;
        }
    }

}