package com.champutils.buff;

import com.champutils.dex.PokemonOriginManager;
import com.champutils.profession.ProfessionType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

/**
 * Anti-abuse context passed into every buff hook.
 *
 * Gameplay systems must describe the reward source here. Buff providers can then
 * safely reject crates, purchases, admin rewards, NPC rewards, WonderTrade,
 * trades, fake spawns, and any other non-legitimate source.
 */
public final class BuffContext {
    public enum Source {
        TRUE_WILD_CATCH,
        PROFESSION_XP,
        WORLD_EVENT,
        NPC_BATTLE,
        CRATE,
        PURCHASE,
        ADMIN,
        WONDERTRADE,
        TRADE,
        NPC_REWARD,
        FAKE_SPAWN,
        UNKNOWN
    }

    public final ServerPlayer player;
    public final Source source;
    public final Pokemon pokemon;
    public final ProfessionType profession;
    public final boolean wasTrueCaught;
    public final boolean wasNpcReward;

    private BuffContext(Builder builder) {
        this.player = builder.player;
        this.source = builder.source;
        this.pokemon = builder.pokemon;
        this.profession = builder.profession;
        this.wasTrueCaught = builder.wasTrueCaught;
        this.wasNpcReward = builder.wasNpcReward;
    }

    public static Builder builder(ServerPlayer player, Source source) {
        return new Builder(player, source);
    }

    public static BuffContext trueWildCatch(ServerPlayer player, Pokemon pokemon) {
        return builder(player, Source.TRUE_WILD_CATCH)
                .pokemon(pokemon)
                .wasTrueCaught(true)
                .build();
    }

    public static BuffContext professionXp(ServerPlayer player, ProfessionType profession) {
        return builder(player, Source.PROFESSION_XP)
                .profession(profession)
                .build();
    }

    public boolean allows(BuffType type) {
        if (type == null || player == null) return false;
        if (type.isCatchBuff()) return allowsPokemonCatchBuffs();
        if (type.isProfessionXp()) return allowsProfessionXpBuffs();
        if (type == BuffType.POKEMON_XP) return source == Source.PROFESSION_XP || source == Source.NPC_BATTLE || source == Source.UNKNOWN;
        if (type == BuffType.WORLD_EVENT_REWARDS) return source == Source.WORLD_EVENT;
        if (type == BuffType.NPC_MONEY) return source == Source.NPC_BATTLE;
        return false;
    }

    public boolean allowsPokemonCatchBuffs() {
        if (player == null || pokemon == null) return false;
        if (source != Source.TRUE_WILD_CATCH || !wasTrueCaught || wasNpcReward) return false;

        String origin = PokemonOriginManager.getOrigin(pokemon);
        return origin == null || origin.isBlank() || PokemonOriginManager.ORIGIN_WILD_CAPTURE.equals(origin);
    }

    public boolean allowsProfessionXpBuffs() {
        return player != null && profession != null && source == Source.PROFESSION_XP;
    }

    public static final class Builder {
        private final ServerPlayer player;
        private final Source source;
        private Pokemon pokemon;
        private ProfessionType profession;
        private boolean wasTrueCaught;
        private boolean wasNpcReward;

        private Builder(ServerPlayer player, Source source) {
            this.player = player;
            this.source = source == null ? Source.UNKNOWN : source;
        }

        public Builder pokemon(Pokemon pokemon) {
            this.pokemon = pokemon;
            return this;
        }

        public Builder profession(ProfessionType profession) {
            this.profession = profession;
            return this;
        }

        public Builder wasTrueCaught(boolean wasTrueCaught) {
            this.wasTrueCaught = wasTrueCaught;
            return this;
        }

        public Builder wasNpcReward(boolean wasNpcReward) {
            this.wasNpcReward = wasNpcReward;
            return this;
        }

        public BuffContext build() {
            return new BuffContext(this);
        }
    }
}
