package com.champutils.profession;

import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ProfessionBlockTracker {

    private static final Set<String> PLACED_BLOCKS =
            new HashSet<>();

    private static final Path SAVE_PATH =
            Path.of(
                    "config",
                    "champutils",
                    "placed_blocks.txt"
            );

    public static void markPlaced(
            BlockPos pos
    ) {
        PLACED_BLOCKS.add(
                serialize(pos)
        );
    }

    public static boolean isPlayerPlaced(
            BlockPos pos
    ) {
        return PLACED_BLOCKS.contains(
                serialize(pos)
        );
    }

    public static void remove(
            BlockPos pos
    ) {
        PLACED_BLOCKS.remove(
                serialize(pos)
        );
    }

    private static String serialize(
            BlockPos pos
    ) {
        return pos.getX() +
                "," +
                pos.getY() +
                "," +
                pos.getZ();
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
                        String entry :
                        PLACED_BLOCKS
                ) {
                    writer.write(entry);
                    writer.newLine();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        try {

            if (
                    !Files.exists(SAVE_PATH)
            ) {
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
                    PLACED_BLOCKS.add(line);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}