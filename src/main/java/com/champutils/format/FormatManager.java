package com.champutils.format;

import java.util.HashMap;
import java.util.Map;

public class FormatManager {

    private static final Map<String, BattleFormat> FORMATS = new HashMap<>();

    public static void register(BattleFormat format) {
        FORMATS.put(format.id, format);
    }

    public static BattleFormat get(String id) {
        return FORMATS.get(id);
    }

    public static Map<String, BattleFormat> getAll() {
        return FORMATS;
    }
}