package com.minenash.customhud.HudElements;

import com.minenash.customhud.HudElements.supplier.NumberSupplierElement;
import com.minenash.customhud.HudElements.text.TextElement;
import com.minenash.customhud.data.Flags;
import net.minecraft.stat.StatFormatter;
import net.minecraft.text.Text;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.minenash.customhud.CustomHud.CLIENT;

public abstract class FuncElements<T> implements HudElement {

    Supplier<T> supplier;
    protected FuncElements(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static class Bool<T> extends FuncElements<T> {
        private final Function<T,Boolean> function;
        public Bool(Supplier<T> supplier, Function<T,Boolean> func) { super(supplier); function = func;}

        @Override public String getString() { return sanitize(supplier, function, false) ? "true" : "false"; }
        @Override public Number getNumber() { return sanitize(supplier, function, false) ? 1 : 0; }
        @Override public boolean getBoolean() { return sanitize(supplier, function, false); }
    }

    public static class Str<T> extends FuncElements<T> {
        private final Function<T,String> function;
        public Str(Supplier<T> supplier, Function<T,String> func) { super(supplier); function = func;}

        @Override public String getString() { return sanitize(supplier, function, "-"); }
        @Override public boolean getBoolean() { return getNumber().doubleValue() > 0; }
        @Override public Number getNumber() {
            try { return function.apply(supplier.get()).length(); }
            catch (Exception ignored) { return 0; }
        }
    }

    public static class Tex<T> extends TextElement {
        private final Function<T, Text> function;
        private final Supplier<T> supplier;
        public Tex(Supplier<T> supplier, Function<T,Text> func) { this.supplier = supplier; function = func;}

        @Override public String getString() { return getText().getString(); }
        @Override public boolean getBoolean() { return getNumber().doubleValue() > 0; }
        @Override public Number getNumber() { return getText().getString().length(); }

        @Override public int getTextWidth() { return CLIENT.textRenderer.getWidth(getText()); }
        @Override public Text getText() { return FuncElements.sanitize(supplier, function, Text.literal("-")); }
    }

    public static class Num<T> extends FuncElements<T> {
        public record NumEntry<T>(Function<T,Number> function, int precision, StatFormatter formatter) {}
        public static <T> NumEntry<T> of(int p, Function<T,Number> func) { return new NumEntry<>(func, p, null); }
        public static <T> NumEntry<T> of(int p, StatFormatter f, Function<T,Number> func) { return new NumEntry<>(func, p, f); }
        public static <T> NumEntry<T> of(StatFormatter f, Function<T,Number> func) { return new NumEntry<>(func, 0, f); }

        private final Function<T,Number> function;
        private final int precision;
        private final double scale;
        private final StatFormatter formatter;

        public Num(Supplier<T> supplier, Function<T,Number> func, Flags flags) {
            super(supplier);
            function = func;
            precision = flags.precision == -1 ? 0 : flags.precision;
            scale = flags.scale;
            formatter = flags.hex ? NumberSupplierElement.HEX : null;
        }
        public Num(Supplier<T> supplier, NumEntry<T> entry, Flags flags) {
            super(supplier);
            function = entry.function;
            precision = flags.precision == -1 ? entry.precision : flags.precision;
            scale = flags.scale;
            formatter = flags.hex ? NumberSupplierElement.HEX : entry.formatter;
        }


        @Override public Number getNumber() { return sanitize(supplier, function, Double.NaN); }
        @Override public boolean getBoolean() { return sanitize(supplier, function, Double.NaN).doubleValue() > 0; }
        @Override public String getString() {
            double num = getNumber().doubleValue() * scale;
            return NumElement.formatString(num, formatter, precision);
        }
    }

    public static class NumBool<T> extends Num<T> {
        private final Function<T,Boolean> bool;

        public NumBool(Supplier<T> supplier, Function<T, Number> num, Function<T, Boolean> bool, Flags flags) {
            super(supplier, num, flags);
            this.bool = bool;
        }

        @Override public boolean getBoolean() { return sanitize(supplier, bool, false); }
    }

    public static class Special<T> extends FuncElements<T> {
        public record Entry<T>(Function<T,String> str, Function<T,Number> num, Function<T,Boolean> bool) {}

        private final Entry<T> entry;
        public Special(Supplier<T> supplier, Entry<T> entry) { super(supplier); this.entry = entry; }
        public Special(Supplier<T> supplier, Function<T,String> str, Function<T,Number> num, Function<T,Boolean> bool) {
            super(supplier);
            this.entry = new Entry<>(str,num,bool);
        }

        @Override public String getString() { return sanitize(supplier, entry.str, "-"); }
        @Override public Number getNumber() { return sanitize(supplier, entry.num, Double.NaN); }
        @Override public boolean getBoolean() { return sanitize(supplier, entry.bool, false); }
    }

    public static class SpecialText<T> extends TextElement {
        public record TextEntry<T>(Function<T,Text> text, Function<T,Number> num, Function<T,Boolean> bool) {}

        private final Supplier<T> supplier;
        private final TextEntry<T> entry;
        public SpecialText(Supplier<T> supplier, TextEntry<T> entry) { this.supplier = supplier; this.entry = entry; }
        public SpecialText(Supplier<T> supplier, Function<T,Text> text, Function<T,Number> num, Function<T,Boolean> bool) {
            this.supplier = supplier;
            this.entry = new TextEntry<>(text,num,bool);
        }

        @Override public String getString() { return getText().getString(); }
        @Override public Number getNumber() { return FuncElements.sanitize(supplier, entry.num, Double.NaN); }
        @Override public boolean getBoolean() { return FuncElements.sanitize(supplier, entry.bool, false); }

        @Override public int getTextWidth() { return CLIENT.textRenderer.getWidth(getText()); }
        @Override public Text getText() { return FuncElements.sanitize(supplier, entry.text, Text.literal("-")); }

    }


    public static <T,E> E sanitize(Supplier<T> supplier, Function<T,E> function, E onFail) {
        try {
            T input = supplier.get();
            if (input == null) return onFail;

            E output = function.apply(input);
            return output == null ? onFail : output;
        }
        catch(Exception _e) {
            return onFail;
        }
    }

}
