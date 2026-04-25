package com.champutils.badge;

public enum BadgeType {

    BOULDER(
            "Boulder",
            "/pc"
    ),

    CASCADE(
            "Cascade",
            ""
    ),

    THUNDER(
            "Thunder",
            "/pokeheal"
    ),

    RAINBOW(
            "Rainbow",
            ""
    ),

    SOUL(
            "Soul",
            ""
    ),

    MARSH(
            "Marsh",
            ""
    ),

    VOLCANO(
            "Volcano",
            ""
    ),

    EARTH(
            "Earth",
            ""
    );


    private final String displayName;
    private final String unlockedCommand;



    BadgeType(
            String displayName,
            String unlockedCommand
    ){
        this.displayName = displayName;
        this.unlockedCommand = unlockedCommand;
    }



    public String getDisplayName(){
        return displayName;
    }



    public String getUnlockedCommand(){
        return unlockedCommand;
    }



    public boolean unlocksCommand(){
        return unlockedCommand != null
                && !unlockedCommand.isBlank();
    }



    public static BadgeType fromString(
            String value
    ){

        if(
                value == null
        ){
            return null;
        }


        for(
                BadgeType badge :
                values()
        ){

            if(
                    badge.name().equalsIgnoreCase(
                            value
                    )
            ){
                return badge;
            }

            if(
                    badge.displayName.equalsIgnoreCase(
                            value
                    )
            ){
                return badge;
            }
        }

        return null;
    }

}