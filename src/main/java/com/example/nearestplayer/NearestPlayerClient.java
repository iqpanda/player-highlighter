package com.example.nearestplayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

public class NearestPlayerClient implements ClientModInitializer {

    private WeakReference<AbstractClientPlayer> glowing = new WeakReference<>(null);
    private int ticks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            clearGlow();
            return;
        }
        ticks++;

        AbstractClientPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player || other.isSpectator()) continue;
            double d = other.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = other;
            }
        }

        AbstractClientPlayer previous = glowing.get();
        if (previous != null && previous != nearest) {
            previous.setGlowingTag(false);
        }

        if (nearest == null) {
            glowing = new WeakReference<>(null);
            return;
        }

        nearest.setGlowingTag(true);
        glowing = new WeakReference<>(nearest);

        Vec3 target = nearest.position();
        Vec3 me = mc.player.position();
        double dx = target.x - me.x;
        double dz = target.z - me.z;
        double distance = Math.sqrt(best);

        float angleToTarget = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float relative = Mth.wrapDegrees(angleToTarget - mc.player.getYRot());
        String arrow = arrowFor(relative);

        Component text = Component.literal(String.format(
                "%s %s  |  %.1f blocks  |  Waypoint: %d, %d, %d",
                arrow,
                nearest.getGameProfile().name(),
                distance,
                Mth.floor(target.x), Mth.floor(target.y), Mth.floor(target.z)));

        showMessage(mc, text);
    }

    /** Tries the different names this method has had; uses whichever exists. */
    private void showMessage(Minecraft mc, Component text) {
        if (invoke(mc.gui, "setOverlayMessage", text, Boolean.FALSE)) return;
        if (invoke(mc.gui, "setOverlayMessage", text)) return;
        if (invoke(mc.player, "sendOverlayMessage", text)) return;
        if (invoke(mc.player, "displayClientMessage", text, Boolean.TRUE)) return;
        // Last resort: a chat message every 2 seconds
        if (ticks % 40 == 0) {
            invoke(mc.player, "sendSystemMessage", text);
        }
    }

    private static boolean invoke(Object target, String name, Object... args) {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (!wrap(types[i]).isInstance(args[i])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            try {
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Class<?> wrap(Class<?> c) {
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == float.class) return Float.class;
        return c;
    }

    private void clearGlow() {
        AbstractClientPlayer previous = glowing.get();
        if (previous != null) {
            previous.setGlowingTag(false);
        }
        glowing = new WeakReference<>(null);
    }

    private static String arrowFor(float relativeDegrees) {
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = Math.floorMod(Math.round(relativeDegrees / 45.0F), 8);
        return arrows[index];
    }
}
