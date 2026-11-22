package com.musicbox.client;

import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/**
 * The volume control shared by the music box and speaker panels.
 * <p>
 * Volume lives on the server, so the panels re-read it every frame to pick up changes made by
 * someone else. That read has to be held off in two situations or it fights the player: while
 * a hand is on the slider, and in the gap between sending a change and the server echoing it
 * back. Without the second guard the knob visibly springs back to the old value for the length
 * of a round trip, which reads as the control being broken even though the change did land.
 */
public class VolumeSlider extends AbstractSliderButton {

    /** How long to prefer our own value over the server's before giving up on the echo. */
    private static final long ECHO_TIMEOUT_MS = 1500L;

    private final int steps;
    private final IntConsumer send;

    private boolean held;
    private int sentStep;
    private int awaitingStep = -1;
    private long awaitingSince;

    /**
     * @param steps how many discrete positions the knob has, matching the menu's button ids
     * @param send  called with the new step whenever it changes, to tell the server
     */
    public VolumeSlider(int x, int y, int width, int height, int steps, float initial, IntConsumer send) {
        super(x, y, width, height, Component.empty(), snap(initial, steps));
        this.steps = steps;
        this.send = send;
        this.sentStep = step();
        updateMessage();
    }

    /** Adopts a volume set elsewhere, unless the player is mid-change. */
    public void syncTo(float target) {
        if (held) {
            return;
        }
        double snapped = snap(target, steps);
        if (awaitingStep >= 0) {
            boolean echoed = Math.abs(snapped - awaitingStep / (double) steps) < 1.0E-4D;
            if (!echoed && Util.getMillis() - awaitingSince < ECHO_TIMEOUT_MS) {
                return;
            }
            awaitingStep = -1;
        }
        if (Math.abs(snapped - value) > 1.0E-4D) {
            value = snapped;
            sentStep = step();
            updateMessage();
        }
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("gui.musicboxradio.volume", Math.round(value * 100.0D)));
    }

    @Override
    protected void applyValue() {
        value = snap((float) value, steps);
        push();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
        super.onRelease(mouseX, mouseY);
    }

    /**
     * Tells the server, but only when the knob crosses into a new step.
     * <p>
     * Sending as the drag happens rather than on release is deliberate: volume is the one
     * control where you want to hear the result while choosing it. Snapping to steps keeps
     * that to at most one packet per step across a whole drag.
     */
    private void push() {
        int step = step();
        if (step == sentStep) {
            return;
        }
        sentStep = step;
        awaitingStep = step;
        awaitingSince = Util.getMillis();
        send.accept(step);
    }

    private int step() {
        return (int) Math.round(value * steps);
    }

    private static double snap(float raw, int steps) {
        return Math.round(Mth.clamp(raw, 0.0F, 1.0F) * steps) / (double) steps;
    }
}
