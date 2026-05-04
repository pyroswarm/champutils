package com.champutils.profession;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfessionBlockTracker {

    /*
     key:
     dimension|x|y|z

     value:
     player uuid
     */
    private static final Map<String, UUID> PLACED_BLOCKS =
            new ConcurrentHashMap<>();

    private static final Path SAVE_PATH =
            Path.of(
                    "config",
                    "champutils",
                    "placed_blocks.txt"
            );

    public static void markPlaced(
            ServerLevel level,
            BlockPos pos,
            UUID playerId
    ) {
        String key =
                serialize(level, pos);

        PLACED_BLOCKS.put(
                key,
                playerId
        );
    }

    public static boolean isPlayerPlaced(
            ServerLevel level,
            BlockPos pos
    ) {
        return PLACED_BLOCKS.containsKey(
                serialize(level, pos)
        );
    }

    public static void remove(
            ServerLevel level,
            BlockPos pos
    ) {
        PLACED_BLOCKS.remove(
                serialize(level, pos)
        );
    }

    public static UUID getOwner(
            ServerLevel level,
            BlockPos pos
    ) {
        return PLACED_BLOCKS.get(
                serialize(level, pos)
        );
    }

    private static String serialize(
            ServerLevel level,
            BlockPos pos
    ) {
        return level.dimension()
                .location()
                .toString()
                + "|"
                + pos.getX()
                + "|"
                + pos.getY()
                + "|"
                + pos.getZ();
    }

    public static void save() {
        try {

            Files.createDirectories(
                    SAVE_PATH.getParent()
            );

            try (
                    BufferedWriter writer =
                            Files.newBufferedWriter(
                                    SAVE_PATH
                            )
            ) {
                for (
                        Map.Entry<String, UUID> entry :
                        PLACED_BLOCKS.entrySet()
                ) {
                    writer.write(
                            entry.getKey() +
                                    "=" +
                                    entry.getValue()
                    );
                    writer.newLine();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        try {

            if (!Files.exists(SAVE_PATH)) {
                return;
            }

            PLACED_BLOCKS.clear();

            try (
                    BufferedReader reader =
                            Files.newBufferedReader(
                                    SAVE_PATH
                            )
            ) {
                String line;

                while (
                        (line = reader.readLine()) != null
                ) {
                    String[] split =
                            line.split("=");

                    if (split.length != 2) {
                        continue;
                    }

                    PLACED_BLOCKS.put(
                            split[0],
                            UUID.fromString(split[1])
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}