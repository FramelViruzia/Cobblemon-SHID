package dev.cobblemonshid;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Slider between min and max that snaps to step and shows its value through a label function. */
final class StepSlider extends AbstractSliderButton {

    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer onChange;

    StepSlider(int x, int y, int width, int height, double min, double max, double step, double initial,
               DoubleFunction<Component> label, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), fraction(initial, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    private static double fraction(double v, double min, double max) {
        if (max <= min) return 0;
        return Math.max(0, Math.min(1, (v - min) / (max - min)));
    }

    double current() {
        double v = min + value * (max - min);
        v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    /** Moves the handle without calling onChange (used by Reset, which sets the value itself). */
    void setExact(double v) {
        value = fraction(v, min, max);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(current()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }
}
