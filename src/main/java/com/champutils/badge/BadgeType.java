package com.champutils.badge;

public enum BadgeType {

    CASCADE("Cascade","/pc"),
    MARSH("Marsh",""),
    EARTH("Earth",""),
    BOULDER("Boulder",""),
    THUNDER("Thunder","/pokeheal"),
    RAINBOW("Rainbow",""),
    SOUL("Soul",""),
    VOLCANO("Volcano",""),

    LORELEI("Lorelei",""),
    BRUNO("Bruno",""),
    AGATHA("Agatha",""),
    LANCE("Lance",""),
    CHAMPION("Champion","");

    private final String displayName;
    private final String unlockedCommand;

    BadgeType(
            String displayName,
            String unlockedCommand
    ){
        this.displayName=displayName;
        this.unlockedCommand=unlockedCommand;
    }

    public String getDisplayName(){
        return displayName;
    }

    public String getUnlockedCommand(){
        return unlockedCommand;
    }

    public boolean unlocksCommand(){
        return unlockedCommand!=null
                && !unlockedCommand.isBlank();
    }

    public static BadgeType fromString(
            String value
    ){

        if(value==null){
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