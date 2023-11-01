package com.minenash.customhud.HudElements.list;

import com.minenash.customhud.HudElements.HudElement;
import com.minenash.customhud.HudElements.icon.PlayerHeadIconElement;
import com.minenash.customhud.HudElements.icon.StatusEffectIconElement;
import com.minenash.customhud.HudElements.supplier.BooleanSupplierElement;
import com.minenash.customhud.HudElements.supplier.NumberSupplierElement;
import com.minenash.customhud.HudElements.supplier.SpecialSupplierElement;
import com.minenash.customhud.HudElements.supplier.StringSupplierElement;
import com.minenash.customhud.complex.ListManager;
import com.minenash.customhud.data.Flags;
import net.minecraft.client.gui.hud.SubtitlesHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.stat.StatFormatter;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.text.WordUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.minenash.customhud.CustomHud.CLIENT;
import static com.minenash.customhud.HudElements.supplier.NumberSupplierElement.of;

public abstract class ListAttributeSuppliers {

    private static StatusEffectInstance status() { return (StatusEffectInstance) ListManager.getValue(); }
    private static PlayerListEntry playerEntry() { return (PlayerListEntry) ListManager.getValue(); }
    private static SubtitlesHud.SubtitleEntry subtitle() { return (SubtitlesHud.SubtitleEntry) ListManager.getValue(); }

    private static final StatFormatter HMS = ticks -> {
        int rawSeconds = ticks / 20;
        int seconds = rawSeconds % 60;
        int minutes = (rawSeconds / 60) % 60;
        int hours = (rawSeconds / 60 / 60);

        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%d:%02d", minutes, seconds);
    };


    public static final Map<Supplier<?>, BiFunction<String,Flags,HudElement>> ATTRIBUTE_MAP = new HashMap<>();

    // COMMON
    public static final Supplier<Number> COUNT = () -> ListManager.getCount();
    public static final Supplier<Number> INDEX = ListManager::getIndex;
    public static final Supplier<String> RAW = () -> ListManager.getValue().toString();

    // STATUS_EFFECTS TODO: ADD STATUS COLOR
    public static final Supplier<String> STATUS_NAME = () -> I18n.translate(status().getTranslationKey());
    public static final Supplier<String> STATUS_ID = () -> Registries.STATUS_EFFECT.getId(status().getEffectType()).toString();
    public static final NumberSupplierElement.Entry STATUS_DURATION = of(() -> status().getDuration(), 0, HMS);
    public static final Supplier<Number> STATUS_AMPLIFICATION = () -> status().getAmplifier();

    public static final Supplier<Boolean> STATUS_AMBIENT = () -> status().isAmbient();
    public static final Supplier<Boolean> STATUS_SHOW_PARTICLES = () -> status().shouldShowParticles();
    public static final Supplier<Boolean> STATUS_SHOW_ICON = () -> status().shouldShowIcon();

    public static final SpecialSupplierElement.Entry STATUS_CATEGORY = new SpecialSupplierElement.Entry(
            () -> WordUtils.capitalize(status().getEffectType().getCategory().name().toLowerCase()),
            () -> status().getEffectType().getCategory().ordinal(),
            () -> status().getEffectType().getCategory().ordinal() != 1);

    //PLAYERS (from PlayerList) TODO: ADD TEAM COLOR AND IS_VERIFIED
    public static final Supplier<String> PLAYER_ENTRY_NAME = () -> playerEntry().getProfile().getName();
    public static final Supplier<String> PLAYER_ENTRY_UUID = () -> playerEntry().getProfile().getId().toString();
    public static final Supplier<String> PLAYER_ENTRY_TEAM = () -> playerEntry().getScoreboardTeam().getName();

    public static final Supplier<Number> PLAYER_ENTRY_LATENCY = () -> playerEntry().getLatency();
    public static final Supplier<Number> PLAYER_ENTRY_LIST_SCORE = () -> {
        Scoreboard scoreboard = CLIENT.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        return scoreboard.getPlayerScore(playerEntry().getProfile().getName(), objective).getScore();
    };

    public static final Supplier<Boolean> PLAYER_ENTRY_SURVIVAL = () -> playerEntry().getGameMode() == GameMode.SURVIVAL;
    public static final Supplier<Boolean> PLAYER_ENTRY_CREATIVE = () -> playerEntry().getGameMode() == GameMode.CREATIVE;
    public static final Supplier<Boolean> PLAYER_ENTRY_ADVENTURE = () -> playerEntry().getGameMode() == GameMode.ADVENTURE;
    public static final Supplier<Boolean> PLAYER_ENTRY_SPECTATOR = () -> playerEntry().getGameMode() == GameMode.SPECTATOR;
    public static final SpecialSupplierElement.Entry PLAYER_ENTRY_GAMEMODE = new SpecialSupplierElement.Entry (
            () -> playerEntry().getGameMode().getName(),
            () -> playerEntry().getGameMode().getId(),
            () -> true);


    //SUBTITLES TODO: ADD ALPHA COLOR
    public static final Supplier<String> SUBTITLES_NAME = () -> subtitle().getText().getString();
    public static final NumberSupplierElement.Entry SUBTITLES_AGE = of( () -> (Util.getMeasuringTimeMs() - subtitle().getTime()) / 1000D, 0 );
    public static final NumberSupplierElement.Entry SUBTITLES_TIME = of( () -> 3 - (Util.getMeasuringTimeMs() - subtitle().getTime()) / 1000D, 0 );
    public static final NumberSupplierElement.Entry SUBTITLES_DISTANCE = of( () -> subtitle().getPosition().distanceTo(CLIENT.player.getEyePos()), 0 );
    public static final NumberSupplierElement.Entry SUBTITLES_X = of( () -> subtitle().getPosition().getX(), 0 );
    public static final NumberSupplierElement.Entry SUBTITLES_Y = of( () -> subtitle().getPosition().getY(), 0 );
    public static final NumberSupplierElement.Entry SUBTITLES_Z = of( () -> subtitle().getPosition().getZ(), 0 );
    public static final Supplier<Boolean> SUBTITLES_LEFT = () -> getDirection() == -1;
    public static final Supplier<Boolean> SUBTITLES_RIGHT = () -> getDirection() == 1;
    public static final Supplier<String> SUBTITLES_DIRECTION = () -> {
        int dir = getDirection();
        return dir == 0 ? "=" : dir == 1 ? ">" : "<";
    };

    public static int getDirection() {
        float xRotation = -CLIENT.player.getPitch() * ((float)Math.PI / 180);
        float yRotation = -CLIENT.player.getYaw() * ((float)Math.PI / 180);

        Vec3d vec3d2 = new Vec3d(0.0, 0.0, -1.0).rotateX(xRotation).rotateY(yRotation);
        Vec3d vec3d3 = new Vec3d(0.0, 1.0, 0.0).rotateX(xRotation).rotateY(yRotation);
        Vec3d vec3d5 = subtitle().getPosition().subtract(CLIENT.player.getEyePos()).normalize();
        double e = vec3d2.crossProduct(vec3d3).dotProduct(vec3d5);

        return -vec3d2.dotProduct(vec3d5) > 0.5 || e == 0? 0 : e < 0 ? 1 : -1;
    }

    static {

        BiFunction<String,Flags,HudElement> effects = (name, flags) -> switch (name) {
            case "name" -> new StringSupplierElement(STATUS_NAME);
            case "id" -> new StringSupplierElement(STATUS_ID);
            case "duration", "dur" -> new NumberSupplierElement(STATUS_DURATION, flags.scale, flags.precision, flags.formatted);
            case "amplification", "amp" -> new NumberSupplierElement(STATUS_AMPLIFICATION, flags.scale, flags.precision);
            case "ambient" -> new BooleanSupplierElement(STATUS_AMBIENT);
            case "show_particles", "particles" -> new BooleanSupplierElement(STATUS_SHOW_PARTICLES);
            case "show_icon" -> new BooleanSupplierElement(STATUS_SHOW_ICON);
            case "category", "cat" -> new SpecialSupplierElement(STATUS_CATEGORY);
            case "icon" -> new StatusEffectIconElement(flags, true);
            case "icon_no_bg" -> new StatusEffectIconElement(flags, false);
            default -> null;
        };

        ATTRIBUTE_MAP.put(ListSuppliers.STATUS_EFFECTS, effects);
        ATTRIBUTE_MAP.put(ListSuppliers.STATUS_EFFECTS_POSITIVE, effects);
        ATTRIBUTE_MAP.put(ListSuppliers.STATUS_EFFECTS_NEGATIVE, effects);
        ATTRIBUTE_MAP.put(ListSuppliers.STATUS_EFFECTS_NEUTRAL, effects);

        ATTRIBUTE_MAP.put(ListSuppliers.ONLINE_PLAYERS, (name, flags) -> switch (name) {
            case "name" -> new StringSupplierElement(PLAYER_ENTRY_NAME);
            case "id" -> new StringSupplierElement(PLAYER_ENTRY_UUID);
            case "team" -> new StringSupplierElement(PLAYER_ENTRY_TEAM);

            case "latency" -> new NumberSupplierElement(PLAYER_ENTRY_LATENCY, flags.scale, flags.precision);
            case "list_score" -> new NumberSupplierElement(PLAYER_ENTRY_LIST_SCORE, flags.scale, flags.precision);

            case "gamemode" -> new SpecialSupplierElement(PLAYER_ENTRY_GAMEMODE);
            case "survival" -> new BooleanSupplierElement(PLAYER_ENTRY_SURVIVAL);
            case "creative" -> new BooleanSupplierElement(PLAYER_ENTRY_CREATIVE);
            case "adventure" -> new BooleanSupplierElement(PLAYER_ENTRY_ADVENTURE);
            case "spectator" -> new BooleanSupplierElement(PLAYER_ENTRY_SPECTATOR);

            case "head" -> new PlayerHeadIconElement(flags);
            default -> null;
        });

        ATTRIBUTE_MAP.put(ListSuppliers.SUBTITLES, (name, flags) -> switch (name) {
            case "name" -> new StringSupplierElement(SUBTITLES_NAME);

            case "age" -> new NumberSupplierElement(SUBTITLES_AGE, flags.scale, flags.precision, flags.formatted);
            case "time" -> new NumberSupplierElement(SUBTITLES_TIME, flags.scale, flags.precision, flags.formatted);
            case "x" -> new NumberSupplierElement(SUBTITLES_X, flags.scale, flags.precision, flags.formatted);
            case "y" -> new NumberSupplierElement(SUBTITLES_Y, flags.scale, flags.precision, flags.formatted);
            case "z" -> new NumberSupplierElement(SUBTITLES_Z, flags.scale, flags.precision, flags.formatted);
            case "dist", "distance" -> new NumberSupplierElement(SUBTITLES_DISTANCE, flags.scale, flags.precision, flags.formatted);

            case "dir", "direction" -> new StringSupplierElement(SUBTITLES_DIRECTION);
            case "left" -> new BooleanSupplierElement(SUBTITLES_LEFT);
            case "right" -> new BooleanSupplierElement(SUBTITLES_RIGHT);
            default -> null;
        });
    }



}