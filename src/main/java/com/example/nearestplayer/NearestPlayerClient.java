package com.example.nearestplayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

/**
 * Client-side only. Every tick:
 *  - finds the nearest other player the client currently knows about
 *  - makes them glow (client-side only, nobody else sees it)
 *  - pins a waypoint on their position and shows name, coordinates,
 *    direction arrow and distance above the hotbar
 */
public class NearestPlayerClient implements ClientModInitializer {

    private WeakReference<AbstractClientPlayer> glowing = new WeakReference<>(null);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            clearGlow();
            return;
        }

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

        // Waypoint = the target's current position
        Vec3 target = nearest.position();
        Vec3 me = mc.player.position();
        double dx = target.x - me.x;
        double dz = target.z - me.z;
        double distance = Math.sqrt(best);

        // Direction relative to where the player is looking
        float angleToTarget = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float relative = Mth.wrapDegrees(angleToTarget - mc.player.getYRot());
        String arrow = arrowFor(relative);

        Component text = Component.literal(String.format(
                "%s %s  |  %.1f blocks  |  Waypoint: %d, %d, %d",
                arrow,
                nearest.getGameProfile().name(),
                distance,
                Mth.floor(target.x), Mth.floor(target.y), Mth.floor(target.z)));

        mc.gui.setOverlayMessage(text, false);
    }

    private void clearGlow() {
        AbstractClientPlayer previous = glowing.get();
        if (previous != null) {
            previous.setGlowingTag(false);
        }
        glowing = new WeakReference<>(null);
    }

    /** 8-way arrow: 0 degrees = straight ahead, positive = to the right. */
    private static String arrowFor(float relativeDegrees) {
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = Math.floorMod(Math.round(relativeDegrees / 45.0F), 8);
        return arrows[index];
    }
}
