package com.minenash.customhud;

import com.minenash.customhud.HudElements.*;
import com.minenash.customhud.HudElements.functional.GetValueElement;
import com.minenash.customhud.HudElements.functional.SetValueElement;
import com.minenash.customhud.HudElements.list.*;
import com.minenash.customhud.HudElements.functional.FunctionalElement;
import com.minenash.customhud.HudElements.HudElement;
import com.minenash.customhud.HudElements.icon.*;
import com.minenash.customhud.HudElements.list.Attributers.Attributer;
import com.minenash.customhud.HudElements.stats.CustomStatElement;
import com.minenash.customhud.HudElements.stats.TypedStatElement;
import com.minenash.customhud.HudElements.supplier.*;
import com.minenash.customhud.HudElements.text.ActionbarMsgElement;
import com.minenash.customhud.HudElements.text.TextSupplierElement;
import com.minenash.customhud.HudElements.text.TitleMsgElement;
import com.minenash.customhud.complex.ComplexData;
import com.minenash.customhud.complex.ListManager;
import com.minenash.customhud.conditionals.ExpressionParser;
import com.minenash.customhud.conditionals.Operation;
import com.minenash.customhud.data.*;
import com.minenash.customhud.errors.ErrorType;
import com.minenash.customhud.errors.Errors;
import com.minenash.customhud.mod_compat.CustomHudRegistry;
import com.terraformersmc.modmenu.ModMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.Pair;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.minenash.customhud.CustomHud.CLIENT;
import static com.minenash.customhud.HudElements.list.AttributeHelpers.*;
import static com.minenash.customhud.HudElements.list.Attributers.*;
import static com.minenash.customhud.HudElements.supplier.BooleanSupplierElement.*;
import static com.minenash.customhud.HudElements.supplier.EntitySuppliers.*;
import static com.minenash.customhud.HudElements.supplier.EntryNumberSuppliers.*;
import static com.minenash.customhud.HudElements.supplier.IntegerSuppliers.*;
import static com.minenash.customhud.HudElements.list.ListSuppliers.*;
import static com.minenash.customhud.HudElements.supplier.SpecialSupplierElement.*;
import static com.minenash.customhud.HudElements.supplier.StringSupplierElement.*;
import static com.minenash.customhud.HudElements.text.TextSupplierElement.*;

public class VariableParser {

    private static final Pattern TEXTURE_ICON_PATTERN = Pattern.compile("((?:[a-z0-9/._-]+:)?[a-z0-9/._ -]+)(?:,([^,]+))?(?:,([^,]+))?(?:,([^,]+))?(?:,([^,]+))?");
    private static final Pattern HEX_COLOR_VARIABLE_PATTERN = Pattern.compile("&\\{(?:0x|#)?([0-9a-fA-F]{3,8})}");
    private static final Pattern EXPRESSION_WITH_PRECISION = Pattern.compile("\\$(?:(\\d+) *,)?(.*)");
    private static final Pattern ITEM_VARIABLE_PATTERN = Pattern.compile("([\\w.-]*)(?::([\\w.:-]*))?.*");
    private static final Pattern SPACE_STR_PATTERN = Pattern.compile("\"(.*)\"");

    public static List<HudElement> addElements(String str, Profile profile, int debugLine, ComplexData.Enabled enabled, boolean line, ListProvider listProvider) {
//        System.out.println("[Line " + debugLine+ "] '" + str + "'");

        List<HudElement> elements = new ArrayList<>();

        System.out.println("PARTITION:");
        for (String part : partition(str)) {
            System.out.println("`" + part + "`");
            HudElement element = parseElement(part, profile, debugLine, enabled, listProvider);
            if (element != null)
                elements.add(element);
        }

        if (line)
            elements.add(new FunctionalElement.NewLine());
        return elements;
    }

    private static List<String> partition(String str) {
        char[] chars = str.toCharArray();
        List<String> parts = new ArrayList<>();

        int nest = 0;
        int startIndex = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = chars[i];

            switch (c) {
                case '\\' -> {
                    if (nest == 0 && i+1 < chars.length && chars[i+1] == 'n') {
                        parts.add(str.substring(startIndex, i));
                        parts.add("\n");
                        startIndex = i+2;
                        i++;
                    }
                }
                case '§' -> {
                    if (nest == 0 && i+1 < chars.length && isColorCode(chars[i+1])) {
                        parts.add(str.substring(startIndex, i));
                        parts.add(str.substring(i,i+2));
                        startIndex = i+2;
                        i++;
                    }
                    else if (nest == 0 && i+2 < chars.length && chars[i+1] == 'z' && (chars[i+1] == 'n' || chars[i+1] == 'm')) {
                        parts.add(str.substring(startIndex, i));
                        parts.add(str.substring(i,i+3));
                        startIndex = i+3;
                        i+=2;
                    }
                }
                case '&' -> {
                    if (i < chars.length-1 && chars[i+1] == '{') {
                        if (nest == 0 && i != startIndex) {
                            parts.add(str.substring(startIndex, i));
                            startIndex = i;
                        }
                        nest++;
                    }
                }
                case '{' -> {
                    if (i > 0 && (chars[i-1] == '{' || chars[i-1] == '&')) continue;
                    if (nest == 0 && i != startIndex) {
                        parts.add(str.substring(startIndex, i));
                        startIndex = i;
                    }
                    nest++;
                }
                case '}' -> {
                    if (i < chars.length-1 && chars[i+1] == '}') continue;
                    if (nest == 1) {
                        parts.add(str.substring(startIndex, i+1));
                        startIndex = i+1;
                    }
                    nest--;
                }
            }
        }
        if (startIndex < chars.length)
            parts.add(str.substring(startIndex, chars.length));

        return parts;
    }

    private static boolean isColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'u');
    }

    private static List<String> partitionConditional(String str) {
        char[] chars = str.toCharArray();
        List<String> parts = new ArrayList<>();

        int nest = 0;
        int qNest = 0;
        int startIndex = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = chars[i];

            switch (c) {
                case '&' -> {
                    if (i < chars.length-1 && chars[i+1] == '{')
                        nest++;
                }
                case '{' -> {
                    if (i > 0 && (chars[i-1] == '{' || chars[i-1] == '&')) continue;
                    nest++;
                }
                case '}' -> {
                    if (i < chars.length-1 && chars[i+1] == '}') continue;
                    nest--;
                }
                case '[' -> nest++;
                case ']' -> nest--;
                case ',' -> {
                    if (nest == 0 && qNest == 0) {
                        parts.add(str.substring(startIndex, i));
                        startIndex = i+1;
                    }
                }
                case '"' -> {
                    if (qNest == nest) qNest++;
                    else qNest--;
                }
            }
        }
        if (startIndex < chars.length)//-1
            parts.add(str.substring(startIndex, chars.length));

        return parts;
    }

    public static HudElement parseElement(String part, Profile profile, int debugLine, ComplexData.Enabled enabled, ListProvider listProvider) {
        if (part == null || part.isEmpty())
            return null;

        if (part.equals("\n"))
            return new FunctionalElement.NewLine();

        if (part.startsWith("§")) {
            CHFormatting formatting = HudTheme.parseColorCode(part);
            if (formatting != null)
                return new FunctionalElement.ChangeFormatting(formatting);
        }

        if (part.startsWith("&{")) {
            Matcher m = HEX_COLOR_VARIABLE_PATTERN.matcher(part);
            if (m.matches())
                return new FunctionalElement.ChangeFormatting(HudTheme.parseHexNumber(m.group(1)));
            HudElement element = parseElement2(part.substring(1), profile, debugLine, enabled, listProvider);
            if (element instanceof FunctionalElement.ChangeFormatting)
                return element;
            if (element instanceof IntElement ie)
                return new FunctionalElement.ChangeFormatting(ie.getNumber().intValue());
            if (element != null)
                return new FunctionalElement.ChangeFormattingFromElement(element);

            Errors.addError(profile.name, debugLine, part, ErrorType.UNKNOWN_COLOR, part.substring(2, part.length()-1).trim());
            return null;
        }
        return parseElement2(part, profile, debugLine, enabled,listProvider);

    }
    public static HudElement parseElement2(String part, Profile profile, int debugLine, ComplexData.Enabled enabled, ListProvider listProvider) {
        if (!part.startsWith("{") || part.length() < 2)
            return new StringElement(part);

        String original = part;
        part = part.substring(1, part.length()-1);

        if (part.isBlank()) {
            Errors.addError(profile.name, debugLine, original, ErrorType.EMPTY_VARIABLE, "");
            return null;
        }

        if (part.startsWith("{") && part.length() > 1) {
            part = part.substring(1, part.length() - 1);

            System.out.println("COND:");
            List<String> ps = partitionConditional(part);
            for (String p : ps)
                System.out.println("`" + p + "`");

            if (ps.size() < 2) {
                Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_CONDITIONAL, null);
                return null;
            }
            List<ConditionalElement.ConditionalPair> pairs = new ArrayList<>();

            for (int i = 0; i < ps.size()-1; i+=2) {
                String cond = ps.get(i);
                String result = ps.get(i+1).trim();
                if (!result.startsWith("\"") || !result.endsWith("\"")) {
                    Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_CONDITIONAL, null);
                    return null;
                }
                result = result.substring(1, result.length()-1);

                pairs.add(new ConditionalElement.ConditionalPair(
                        ExpressionParser.parseExpression(cond, original, profile, debugLine, enabled, listProvider),
                        addElements(result, profile, debugLine, enabled, false, listProvider)));
            }
            if (ps.size() % 2 == 1) {
                String result = ps.get(ps.size()-1).trim();
                if (!result.startsWith("\"") || !result.endsWith("\"")) {
                    Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_CONDITIONAL, null);
                    return null;
                }
                result = result.substring(1, result.length()-1);

                pairs.add(new ConditionalElement.ConditionalPair(new Operation.Literal(1), addElements(result, profile, debugLine, enabled, false, listProvider)));
            }

            if (pairs.isEmpty()) {
                Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_CONDITIONAL, null);
                return null;
            }
            return new ConditionalElement(pairs);
        }

        if (part.startsWith("$")) {
            try {
                Matcher matcher = EXPRESSION_WITH_PRECISION.matcher(part);
                matcher.matches();
                int precision = matcher.group(1) == null ? -1 : Integer.parseInt(matcher.group(1));
                return new ExpressionElement( ExpressionParser.parseExpression(matcher.group(2), original, profile, debugLine, enabled, listProvider), precision );
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        if (listProvider == null || !(part.startsWith("scores") && part.contains(","))) { //Fixes naming conflict
            HudElement he = getListSupplierElements(part, profile, debugLine, enabled, original, listProvider);
            if (he instanceof IgnoreErrorElement) return null;
            if (he != null) return he;
        }

        // ATTRS WERE HERE
        HudElement ae = getAttributeElement(part, profile, debugLine, enabled, original);
        if (ae != null) {
            if (ae instanceof IgnoreErrorElement)
                return null;
            if (ae instanceof FunctionalElement.CreateListElement cle)
                return listElement(cle.provider, part, part.indexOf(','), profile, debugLine, enabled, original);
            return ae;
        }

        if (part.startsWith("bar"))
            return barElement(part, profile, debugLine, enabled, original, listProvider);

        if (part.startsWith("space:")) {
            String widthStr = part.substring(6).trim();
            Operation op;

            Matcher matcher = SPACE_STR_PATTERN.matcher(widthStr);
            if (matcher.matches())
                op = new Operation.Length(addElements(matcher.group(1), profile, debugLine, enabled, false, listProvider));
            else
                op = ExpressionParser.parseExpression(widthStr.trim(), part, profile, debugLine, enabled, listProvider);
            return new SpaceElement( op );
        }

        if (part.startsWith("set:")) {
            int commaIndex = part.indexOf(",");
            if (commaIndex == -1) {
                String valueName = part.substring(4).trim();
                return new SetValueElement(valueName, new Operation.Literal(0));
            }
            Operation op = ExpressionParser.parseExpression(part.substring(commaIndex+1).trim(), part, profile, debugLine, enabled, listProvider);
            return new SetValueElement(part.substring(4,commaIndex), op);
        }

        if (part.startsWith("setmacro:") || part.startsWith("setm:")) {
            int commaIndex = part.indexOf(",");
            if (commaIndex == -1) {
                String macroName = part.substring(part.indexOf(":")+1).trim();
                profile.macros.put(macroName, new Macro(Collections.singletonList( new FunctionalElement.IgnoreNewLineIfSurroundedByNewLine() ), null));
                return new FunctionalElement.IgnoreNewLineIfSurroundedByNewLine();
            }
            String macroName = part.substring(part.indexOf(":")+1, commaIndex).trim();
            String macroStr = part.substring(commaIndex+1).trim();
            Macro macro;

            Matcher matcher = SPACE_STR_PATTERN.matcher(macroStr);
            if (matcher.matches())
                macro = new Macro(addElements(matcher.group(1), profile, debugLine, enabled, false, listProvider), null);
            else
               macro = new Macro(null, ExpressionParser.parseExpression(macroStr.trim(), part, profile, debugLine, enabled, listProvider));
            profile.macros.put(macroName, macro);

            return new FunctionalElement.IgnoreNewLineIfSurroundedByNewLine();
        }

        if (part.startsWith("real_time:")) {
            try {
                return new RealTimeElement(new SimpleDateFormat(part.substring(10)));
            }
            catch (IllegalArgumentException e) {
                Errors.addError(profile.name, debugLine, original, ErrorType.INVALID_TIME_FORMAT, e.getMessage());
            }
        }
        if (part.equals("profile_last_modified")) {
            return new LastUpdatedElement(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT));
        }
        if (part.startsWith("profile_last_modified:")) {
            try {
                return new LastUpdatedElement(DateTimeFormatter.ofPattern(part.substring(22)));
            }
            catch (IllegalArgumentException e) {
                Errors.addError(profile.name, debugLine, original, ErrorType.INVALID_TIME_FORMAT, e.getMessage());
            }
        }

        if (part.startsWith("icon:")) {
            part = part.substring(part.indexOf(':')+1);
            String[] flagParts = part.split(" ");
            String main = flagParts[0];

            Item item = Registries.ITEM.get(Identifier.tryParse(main));
            if (item != Items.AIR) {
                Flags flags = Flags.parse(profile.name, debugLine, flagParts);
                return Flags.wrap(new ItemIconElement(new ItemStack(item), flags), flags);
            }

            if (!part.contains(",")) {
                Identifier id = new Identifier(main.endsWith(".png") ? main : main + ".png");
                Flags flags = Flags.parse(profile.name, debugLine, flagParts);
                SimpleTextureIconElement element = new SimpleTextureIconElement(id, flags);
                if (element.isIconAvailable())
                    return Flags.wrap(element, flags);
                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ICON, id.toString());
                return null;
            }

            Matcher matcher = TEXTURE_ICON_PATTERN.matcher(part);
            if (!matcher.matches()) {
                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ICON, part);
                return null;
            }

            String[] mainParts = matcher.group(1).split(" ");
            Identifier id = new Identifier(mainParts[0].endsWith(".png") ? mainParts[0] : mainParts[0] + ".png");
            Operation u = matcher.group(2) == null ? null : ExpressionParser.parseExpression(matcher.group(2), original, profile, debugLine, enabled, listProvider);
            Operation v = matcher.group(3) == null ? null : ExpressionParser.parseExpression(matcher.group(3), original, profile, debugLine, enabled, listProvider);
            Operation w = matcher.group(4) == null ? null : ExpressionParser.parseExpression(matcher.group(4), original, profile, debugLine, enabled, listProvider);
            Operation h = matcher.group(5) == null ? null : ExpressionParser.parseExpression(matcher.group(5), original, profile, debugLine, enabled, listProvider);


            Flags flags = Flags.parse(profile.name, debugLine, mainParts);
            NewTextureIconElement element = new NewTextureIconElement(id, u, v, w, h, flags);
            if (element.isIconAvailable())
                return element;
            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ICON, id.toString());
            return null;
        }



        String[] flagParts = part.split(" ");
        part = flagParts[0];
        Flags flags = part.endsWith(",") ? new Flags() : Flags.parse(profile.name, debugLine, flagParts);

        if (listProvider != null) {
            HudElement element = getListAttributeSupplierElement(part, flags, listProvider);
            if (element instanceof FunctionalElement.CreateListElement cle) {
                String p = original.substring(1, original.length() - 1);
                return listElement(cle.provider, p, p.indexOf(','), profile, debugLine, enabled, original);
            }
            if (element != null)
                return Flags.wrap(element, flags);
        }

        if (part.startsWith("get:")) {
            String valueName = part.substring(4);
            return new GetValueElement(valueName, flags);
        }

        if (part.startsWith("macro:") || part.startsWith("m:")) {
            return new MacroElement( part.substring(part.indexOf(":")+1), flags );
        }

        if (part.startsWith("score:")) {
            String p = part.substring(6);

            int collinIndex = p.indexOf(':');
            if (collinIndex == -1) {
                int commaIndex = p.indexOf(',');
                String pp = p.substring(0, commaIndex == -1 ? p.length() : commaIndex);
                ListProvider provider = new ListProvider.ListFunctioner<>(() -> pp ,SCORES);
                ATTRIBUTER_MAP.put(provider, SCOREBOARD_SCORE);
                return listElement(provider, original.substring(1, original.length() - 1), commaIndex, profile, debugLine, enabled, original);
            }
            String player = p.substring(0, collinIndex);
            String objective = p.substring(collinIndex+1);

            return Flags.wrap(new NumberSupplierElement(() -> {
                ScoreboardObjective obj = scoreboard().getNullableObjective(objective);
                if (obj == null) return null;
                var score = scoreboard().getScore(ScoreHolder.fromName(player), obj);
                return score == null ? 0 : score.getScore();
            }, flags), flags);
        }

        if (part.startsWith("stat:")) {
            String stat = part.substring(5);

            HudElement element = stat("mined:",   Stats.MINED,   Registries.BLOCK, stat, flags, enabled);
            if (element == null) element = stat("crafted:", Stats.CRAFTED, Registries.ITEM,  stat, flags, enabled);
            if (element == null) element = stat("used:",    Stats.USED,    Registries.ITEM,  stat, flags, enabled);
            if (element == null) element = stat("broken:",  Stats.BROKEN,  Registries.ITEM,  stat, flags, enabled);
            if (element == null) element = stat("dropped:", Stats.DROPPED, Registries.ITEM,  stat, flags, enabled);
            if (element == null) element = stat("picked_up:", Stats.PICKED_UP, Registries.ITEM, stat, flags, enabled);
            if (element == null) element = stat("killed:",    Stats.KILLED,    Registries.ENTITY_TYPE, stat, flags, enabled);
            if (element == null) element = stat("killed_by:", Stats.KILLED_BY, Registries.ENTITY_TYPE, stat, flags, enabled);

            if (element != null)
                return Flags.wrap(element, flags);

            Identifier statId = Registries.CUSTOM_STAT.get(new Identifier(stat));
            if (Stats.CUSTOM.hasStat(statId)) {
                enabled.updateStats = true;
                return Flags.wrap(new CustomStatElement(Stats.CUSTOM.getOrCreateStat(statId), flags), flags);
            }
            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_STATISTIC, stat);
            return null;
        }

//        if (part.startsWith("icon:")) {
//            part = part.substring(part.indexOf(':')+1);
//
//            Item item = Registries.ITEM.get(Identifier.tryParse(part));
//            if (item != Items.AIR)
//                return Flags.wrap(new ItemIconElement(new ItemStack(item), flags), flags);
//
//            Matcher matcher = TEXTURE_ICON_PATTERN.matcher(part);
//            if (!matcher.matches()) {
//                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ICON, part);
//                return null;
//            }
//
//            Identifier id = new Identifier(matcher.group(1) + ".png");
//            int u = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
//            int v = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
//            int w = matcher.group(4) == null ? -1 : Integer.parseInt(matcher.group(4));
//            int h = matcher.group(5) == null ? -1 : Integer.parseInt(matcher.group(5));
//
//            TextureIconElement element = new TextureIconElement(id, u, v, w, h, flags);
//            if (element.isIconAvailable())
//                return Flags.wrap(element, flags);
//            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ICON, id.toString());
//            return null;
//        }

        if (part.startsWith("itemcount:")) {
            part = part.substring(part.indexOf(':')+1);

            try {
                Item item = Registries.ITEM.get(new Identifier(part));
                if (item != Items.AIR)
                    return Flags.wrap(new ItemCountElement(item), flags);
                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ITEM_ID, part);
                return null;
            }
            catch (InvalidIdentifierException e) {
                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_ITEM_ID, part);
                return null;
            }
        }

        if (part.startsWith("s:") || part.startsWith("setting:")) {
            String setting = part.substring(part.indexOf(':') + 1).toLowerCase();
            Pair<HudElement,Pair<ErrorType,String>> element = SettingsElement.create(setting, flags);

            if (element.getLeft() != null)
                return Flags.wrap(element.getLeft(), flags);
            Errors.addError(profile.name, debugLine, original, element.getRight().getLeft(), element.getRight().getRight());
            return null;
        }

        if (part.startsWith("is_pressed:")) {
            String setting = part.substring(part.indexOf(':') + 1).toLowerCase();
            String context = setting;

            if (!setting.startsWith("key_")) {
                if (!setting.startsWith("key."))
                    setting = "key." + setting;
                context = setting;
                setting = "key_" + setting;
            }

            GameOptions options = MinecraftClient.getInstance().options;
            String key = setting.substring(4);
            for (KeyBinding binding : options.allKeys)
                if (binding.getTranslationKey().equalsIgnoreCase(key))
                    return Flags.wrap(new BooleanSupplierElement(binding::isPressed), flags);

            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_KEYBIND, context);
            return null;
        }

        if (part.startsWith("toggle:")) {
            String name = part.substring(7);
            if (name.isEmpty()) {
                Errors.addError(profile.name, debugLine, original, ErrorType.EMPTY_TOGGLE, null);
                return null;
            }
            Toggle toggle = profile.toggles.get(name);
            if (toggle == null) //Replace with saved key
                toggle = new Toggle(name.replace('_', ' '), false, debugLine, new KeyBinding("customhud_toggle_" + name, GLFW.GLFW_KEY_UNKNOWN, "customhud"), true);
            else
                toggle.lines.add(debugLine);

            profile.toggles.put(name, toggle);
            return new BooleanSupplierElement(toggle::getValue);
        }

        if (part.startsWith("toggle_key:")) {
            String name = part.substring(11);
            if (name.isEmpty()) {
                Errors.addError(profile.name, debugLine, original, ErrorType.EMPTY_TOGGLE, null);
                return null;
            }

            if (!name.startsWith("key.")) {
                if (!name.startsWith("keyboard.") && !name.startsWith("mouse."))
                    name = "keyboard." + name;
                name = "key." + name;
            }

            InputUtil.Key key;
            try { key = InputUtil.fromTranslationKey(name); }
            catch(Exception ignored) { key = null; }
            if (key == null) {
                Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_KEY, name);
                return null;
            }

            Toggle toggle = profile.toggles.get(" " + name); //Space indicates key variant
            if (toggle == null)
                toggle = new Toggle(name, true, debugLine, new KeyBinding("customhud_key_toggle_" + name, key.getCode(), "customhud"), true);
            else
                toggle.lines.add(debugLine);

            profile.toggles.put(" " + name, toggle);
            return new BooleanSupplierElement(toggle::getValue);
        }

        if (part.startsWith("timer")) {
            String argsStr = part.substring(5);
            if (!argsStr.startsWith("[") || !argsStr.endsWith("]")) {
                Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_TIMER, null);
                return null;
            }

            List<String> parts = partitionConditional(argsStr.substring(1, argsStr.length()-1));
            if (parts.isEmpty() || parts.size() > 2) {
                Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_TIMER, "Expected 1 or 2 args, found" + parts.size());
                return null;
            }

            Operation end = ExpressionParser.parseExpression(parts.get(0), original, profile, debugLine, enabled, listProvider);
            Operation interval = parts.size() != 2 ? new Operation.Literal(1) :
                ExpressionParser.parseExpression(parts.get(1), original, profile, debugLine, enabled, listProvider);
            return Flags.wrap(new TimerElement(end, interval, flags), flags);
        }

        if (part.startsWith("slime_chunk:")) {
            String seedStr = part.substring(12).trim();
            if (seedStr.isEmpty())
                return new BooleanSupplierElement(IS_SLIME_CHUNK);
            return new SeededSlimeChunkElement(seedStr);
        }

        switch (part) {
            case "gizmo": return Flags.wrap(new DebugGizmoElement(flags), flags);
            case "record_icon": enabled.music = true; return Flags.wrap(new RecordIconElement(flags), flags);
            case "target_block_icon", "target_icon", "tbicon": enabled.targetBlock = true;
                return Flags.wrap(new ItemSupplierIconElement(() -> new ItemStack(ComplexData.targetBlock.getBlock()), flags), flags);
            case "target_fluid_icon", "tficon": enabled.targetFluid = true;
                return Flags.wrap(new ItemSupplierIconElement(() -> new ItemStack(ComplexData.targetFluid.getBlockState().getBlock()), flags), flags);
            case "actionbar_msg", "actionbar": return Flags.wrap(new ActionbarMsgElement(flags), flags);
            case "title_msg", "title": return Flags.wrap(new TitleMsgElement(TITLE_MSG, flags), flags);
            case "subtitle_msg", "subtitle": return Flags.wrap(new TitleMsgElement(SUBTITLE_MSG, flags), flags);
            case "target_villager_xp_bar", "tveb": {
                enabled.targetEntity = enabled.targetVillager = true;
                return new ProgressBarIcon( new Operation.Element(new NumberSupplierElement(VILLAGER_XP, new Flags())),
                                            new Operation.Element(new NumberSupplierElement(VILLAGER_XP_NEEDED, new Flags())),
                                            ProgressBarIcon.VILLAGER_GREEN, flags);
            }
        }

        HudElement element = getSupplierElement(part, enabled, flags);
        if (element != null)
            return Flags.wrap(element, flags);

        Matcher keyMatcher = registryKey.matcher(part);
        if (keyMatcher.matches()) {
            element = CustomHudRegistry.get(keyMatcher.group(1), part);
            if (element != null)
                return element;

            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_VARIABLE, part);
        }
        else
            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_VARIABLE, part);
        return null;
    }


    private static final Pattern registryKey = Pattern.compile("(\\w+).*");

    private static HudElement stat(String prefix, StatType<?> type, Registry<?> registry, String stat, Flags flags, ComplexData.Enabled enabled) {
        if (!stat.startsWith(prefix))
            return null;

        Optional<?> entry = registry.getOrEmpty( new Identifier(stat.substring(prefix.length())) );
        if (entry.isPresent()) {
            enabled.updateStats = true;
            return new TypedStatElement(type, entry.get(), flags);
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HudElement getSupplierElement(String name, ComplexData.Enabled enabled, Flags flags) {
        Supplier supplier = getStringSupplier(name, enabled);
        if (supplier != null)
            return new StringSupplierElement(supplier);

        supplier = getBooleanSupplier(name, enabled);
        if (supplier != null)
            return new BooleanSupplierElement(supplier);

        supplier = getIntegerSupplier(name, enabled);
        if (supplier != null)
            return new NumberSupplierElement(supplier, flags);

        NumberSupplierElement.Entry entry = getDecimalSupplier(name, enabled);
        if (entry != null)
            return new NumberSupplierElement(entry, flags);

        SpecialSupplierElement.Entry entry2 = getSpecialSupplierElements(name, enabled);
        if (entry2 != null)
            return new SpecialSupplierElement(entry2);

        supplier = getTextSupplier(name, enabled);
        if (supplier != null)
            return new TextSupplierElement(supplier, flags);

        Integer color = HudTheme.parseColorName(name);
        if (color != null)
            return new IntElement(color, flags);

        CHFormatting formatting = HudTheme.parseFormattingName(name);
        if (formatting != null)
            return new FunctionalElement.ChangeFormatting(formatting);

        return null;
    }

    private static Supplier<Text> getTextSupplier(String element, ComplexData.Enabled enabled) {
        return switch (element) {
            case "display_name", "name" -> DISPLAY_NAME;
            case "target_entity_name", "ten" -> {enabled.targetEntity = true; yield TARGET_ENTITY_NAME;}
            case "last_hit_name", "lhn" -> {enabled.targetEntity = true; yield LAST_HIT_ENTITY_NAME;}
            case "hooked_entity_name", "hen" -> HOOKED_ENTITY_NAME;
            case "vehicle_entity_name", "vehicle_name", "ven" -> VEHICLE_ENTITY_NAME;
            case "team_name" -> PLAYER_TEAM_NAME;
            case "record_name" -> {enabled.music = true; yield RECORD_NAME;}
            default -> null;
        };
    }

    private static Supplier<String> getStringSupplier(String element, ComplexData.Enabled enabled) {
        return switch (element) {
            case "profile_name" -> PROFILE_NAME;
            case "version" -> VERSION;
            case "client_version" -> CLIENT_VERSION;
            case "modded_name" -> MODDED_NAME;
//            case "display_name", "name" -> DISPLAY_NAME; TODO: remove
            case "username" -> USERNAME;
            case "uuid" -> UUID;
            case "team" -> PLAYER_TEAM;
            case "dimension" -> DIMENSION;
            case "dimension_id" -> DIMENSION_ID;
            case "facing" -> FACING;
            case "facing_short" -> FACING_SHORT;
            case "facing_towards_xz" -> FACING_TOWARDS_XZ;
            case "biome" -> BIOME;
            case "biome_id" -> BIOME_ID;
            case "moon_phase_word" -> { enabled.clientChunk = true; yield MOON_PHASE_WORD; }
            case "target_entity", "te" -> {enabled.targetEntity = true; yield TARGET_ENTITY;}
            case "target_entity_id", "tei" -> {enabled.targetEntity = true; yield TARGET_ENTITY_ID;}
            case "target_entity_uuid", "teu" -> {enabled.targetEntity = true; yield TARGET_ENTITY_UUID;}
            case "target_villager_biome", "tvb" -> {enabled.targetEntity = enabled.targetVillager = true; yield VILLAGER_BIOME;}
            case "target_villager_level_word", "tvlw" -> {enabled.targetEntity = enabled.targetVillager = true; yield VILLAGER_LEVEL_WORD;}
            case "last_hit", "lh" -> {enabled.targetEntity = true; yield LAST_HIT_ENTITY;}
            case "last_hit_id", "lhi" -> {enabled.targetEntity = true; yield LAST_HIT_ENTITY_ID;}
            case "last_hit_uuid", "lhu" -> {enabled.targetEntity = true; yield LAST_HIT_ENTITY_UUID;}
            case "hooked_entity", "he" -> HOOKED_ENTITY;
            case "hooked_entity_id", "hei" -> HOOKED_ENTITY_ID;
            case "hooked_entity_uuid", "heu" -> HOOKED_ENTITY_UUID;
            case "vehicle_entity", "vehicle", "ve" -> VEHICLE_ENTITY;
            case "vehicle_entity_id", "vehicle_id", "vei" -> VEHICLE_ENTITY_ID;
            case "vehicle_entity_uuid", "vehicle_uuid", "veu" -> VEHICLE_ENTITY_UUID;
            case "vehicle_horse_armor", "horse_armor", "vha" -> VEHICLE_HORSE_ARMOR;
            case "world_name", "world" -> WORLD_NAME;
            case "server_name" -> SERVER_NAME;
            case "server_address", "address", "ip" -> SERVER_ADDRESS;
            case "java_version" -> JAVA_VERSION;
            case "cpu_name" -> CPU_NAME;
            case "gpu_name" -> GPU_NAME;
            case "server_brand" -> SERVER_BRAND;

            case "music_id" -> {enabled.music = true; yield MUSIC_ID;}
            case "music_name" -> {enabled.music = true; yield MUSIC_NAME;}
            case "record_id" -> {enabled.music = true; yield RECORD_ID;}

            case "bb_peaks","biome_builder_peaks" -> {enabled.serverWorld = true; yield BIOME_BUILDER_PEAKS;}
            case "bb_cont","biome_builder_continents" -> {enabled.serverWorld = true; yield BIOME_BUILDER_CONTINENTS;}

            case "am_pm" -> { enabled.time = true; yield TIME_AM_PM; }
            default -> null;
        };
    }

    private static Supplier<Boolean> getBooleanSupplier(String element, ComplexData.Enabled enabled) {
        return switch (element) {
            case "profile_in_cycle"-> PROFILE_IN_CYCLE;
            case "vsync" -> VSYNC;
            case "sp", "singleplayer" -> SINGLEPLAYER;
            case "mp", "multiplayer" -> MULTIPLAYER;
            case "survival" -> SURVIVAL;
            case "creative" -> CREATIVE;
            case "adventure" -> ADVENTURE;
            case "spectator" -> SPECTATOR;
            case "chunks_culling" -> CHUNK_CULLING;
            case "overworld" -> IN_OVERWORLD;
            case "nether" -> IN_NETHER;
            case "end" -> IN_END;
            case "raining" -> {enabled.world = true; yield IS_RAINING;}
            case "thundering" -> {enabled.world = true; yield IS_THUNDERING;}
            case "snowing" -> {enabled.world = true; yield IS_SNOWING;}
            case "slime_chunk" -> {enabled.world = true; yield IS_SLIME_CHUNK;}
            case "sprinting" -> SPRINTING;
            case "sneaking" -> SNEAKING;
            case "swimming" -> SWIMMING;
            case "flying" -> FLYING;
            case "on_ground" -> ON_GROUND;
            case "sprint_held" -> SPRINT_HELD;
            case "screen_open" -> SCREEN_OPEN;
            case "chat_open" -> CHAT_OPEN;
            case "player_list_open","tab_open" -> PLAYER_LIST_OPEN;
            case "item_has_durability", "item_has_dur" -> ITEM_HAS_DURABILITY;
            case "offhand_item_has_durability", "oitem_has_dur" -> OFFHAND_ITEM_HAS_DURABILITY;
            case "fishing_is_cast" -> FISHING_IS_CAST;
            case "fishing_is_hooked" -> FISHING_IS_HOOKED;
            case "fishing_has_caught" -> FISHING_HAS_CAUGHT;
            case "fishing_in_open_water" -> FISHING_IN_OPEN_WATER;

            case "music_playing" -> {enabled.music = true; yield MUSIC_PLAYING;}
            case "record_playing" -> {enabled.music = true; yield RECORD_PLAYING;}

            case "has_noise" -> {enabled.serverWorld = true; yield HAS_NOISE;}

            case "reaL_am" -> REAL_AM;
            case "reaL_pm" -> REAL_PM;

            default -> null;
        };
    }

    private static Supplier<Number> getIntegerSupplier(String element, ComplexData.Enabled enabled) {
        return switch (element) {
            case "profile_errors" -> PROFILE_ERRORS;
            case "fps" -> FPS;
            case "max_fps" -> MAX_FPS;
            case "biome_blend" -> BIOME_BLEND;
            case "simulation_distance", "sd" -> SIMULATION_DISTANCE;
            case "packets_sent", "tx" -> PACKETS_SENT;
            case "packets_received", "rx" -> PACKETS_RECEIVED;
            case "chunks_rendered" -> CHUNKS_RENDERED;
            case "chunks_loaded" -> CHUNKS_LOADED;
            case "render_distance" -> RENDER_DISTANCE;
            case "queued_tasks" -> QUEUED_TASKS;
            case "upload_queue" -> UPLOAD_QUEUE;
            case "buffer_count" -> BUFFER_COUNT;
            case "entities_rendered" -> ENTITIES_RENDERED;
            case "entities_loaded" -> ENTITIES_LOADED;
            case "force_loaded_chunks", "fc" -> { enabled.world = true; yield FORCED_LOADED_CHUNKS; }
            case "block_x", "bx" -> BLOCK_X;
            case "block_y", "by" -> BLOCK_Y;
            case "block_z", "bz" -> BLOCK_Z;
            case "target_block_x", "target_x", "tbx" -> { enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_X; }
            case "target_block_y", "target_y", "tby" -> { enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_Y; }
            case "target_block_z", "target_z", "tbz" -> { enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_Z; }
            case "target_block_distance", "target_distance", "tbd" -> { enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_DISTANCE; }
            case "target_block_color", "target_color", "tbc" -> { enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_COLOR; }
            case "target_fluid_x", "tfx" -> { enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_X; }
            case "target_fluid_y", "tfy" -> { enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_Y; }
            case "target_fluid_z", "tfz" -> { enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_Z; }
            case "target_fluid_distance", "tfd" -> { enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_DISTANCE; }
            case "target_fluid_color", "tfc" -> { enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_COLOR; }
            case "target_villager_level", "tvl" -> { enabled.targetEntity = enabled.targetVillager = true; yield VILLAGER_LEVEL; }
            case "target_villager_xp", "tve" -> { enabled.targetEntity = enabled.targetVillager = true; yield VILLAGER_XP; }
            case "target_villager_xp_needed", "tven" -> { enabled.targetEntity = enabled.targetVillager = true; yield VILLAGER_XP_NEEDED; }
            case "vehicle_entity_armor", "vehicle_armor", "vea" -> VEHICLE_ENTITY_ARMOR;
            case "in_chunk_x", "icx" -> IN_CHUNK_X;
            case "in_chunk_y", "icy" -> IN_CHUNK_Y;
            case "in_chunk_z", "icz" -> IN_CHUNK_Z;
            case "chunk_x", "cx" -> CHUNK_X;
            case "chunk_y", "cy" -> CHUNK_Y;
            case "chunk_z", "cz" -> CHUNK_Z;
            case "region_x", "rex" -> REGION_X;
            case "region_z", "rez" -> REGION_Z;
            case "region_relative_x", "rrx" -> REGION_RELATIVE_X;
            case "region_relative_z", "rrz" -> REGION_RELATIVE_Z;

            case "ccw1", "client_chunks_w1", "client_chunks_cached" -> CHUNK_CLIENT_CACHED;
            case "ccw2", "client_chunks_w2", "client_chunks_loaded" -> CHUNK_CLIENT_LOADED;
            case "cce1", "client_chunks_e1", "client_entities_loaded" -> CHUNK_CLIENT_ENTITIES_LOADED;
            case "cce2", "client_chunks_e2", "client_entities_cached_sections" -> CHUNK_CLIENT_ENTITIES_CACHED_SECTIONS;
            case "cce3", "client_chunks_e3", "client_entities_ticking_chunks" -> CHUNK_CLIENT_ENTITIES_TICKING_CHUNKS;

            case "scw1", "server_chunks_w1", "server_chunks_loaded" -> {enabled.serverWorld = true; yield CHUNK_SERVER_LOADED;}
            case "sce1", "server_chunks_e1", "server_entities_registered" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_REGISTERED;}
            case "sce2", "server_chunks_e2", "server_entities_loaded" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_LOADED;}
            case "sce3", "server_chunks_e3", "server_entities_cached_sections" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_CACHED_SECTIONS;}
            case "sce4", "server_chunks_e4", "server_entities_managed" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_MANAGED;}
            case "sce5", "server_chunks_e5", "server_entities_tracked" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_TRACKED;}
            case "sce6", "server_chunks_e6", "server_entities_loading" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_LOADING;}
            case "sce7", "server_chunks_e7", "server_entities_unloading" -> {enabled.serverWorld = true; yield CHUNK_SERVER_ENTITIES_UNLOADING;}

            case "client_light", "light" -> { enabled.clientChunk = true; yield CLIENT_LIGHT; }
            case "client_light_sky", "light_sky" -> { enabled.clientChunk = true; yield CLIENT_LIGHT_SKY; }
            case "client_light_sun", "light_sun" -> { enabled.clientChunk = true; yield CLIENT_LIGHT_SUN; }
            case "client_light_block", "light_block" -> { enabled.clientChunk = true; yield CLIENT_LIGHT_BLOCK; }
            case "server_light_sky" -> { enabled.world = enabled.serverChunk = true; yield SERVER_LIGHT_SKY; }
            case "server_light_block" -> { enabled.world = enabled.serverChunk = true; yield SERVER_LIGHT_BLOCK; }
            case "client_height_map_surface", "chs" -> { enabled.clientChunk = true; yield CLIENT_HEIGHT_MAP_SURFACE; }
            case "client_height_map_motion_blocking", "chm" -> { enabled.clientChunk = true; yield CLIENT_HEIGHT_MAP_MOTION_BLOCKING; }
            case "server_height_map_surface", "shs" -> { enabled.serverChunk = true; yield SERVER_HEIGHT_MAP_SURFACE; }
            case "server_height_map_ocean_floor", "sho" -> { enabled.serverChunk = true; yield SERVER_HEIGHT_MAP_OCEAN_FLOOR; }
            case "server_height_map_motion_blocking", "shm" -> { enabled.serverChunk = true; yield SERVER_HEIGHT_MAP_MOTION_BLOCKING; }
            case "server_height_map_motion_blocking_no_leaves", "shml" -> { enabled.serverChunk = true; yield SERVER_HEIGHT_MAP_MOTION_BLOCKING_NO_LEAVES; }
            case "moon_phase" -> { enabled.clientChunk = true; yield MOON_PHASE; }
            case "spawn_chunks", "sc" -> { enabled.serverWorld = true; yield SPAWN_CHUNKS; }
            case "monsters" -> { enabled.serverWorld = true; yield MONSTERS; }
            case "creatures" -> { enabled.serverWorld = true; yield CREATURES; }
            case "ambient_mobs" -> { enabled.serverWorld = true; yield AMBIENT_MOBS; }
            case "water_creatures" -> { enabled.serverWorld = true; yield WATER_CREATURES; }
            case "water_ambient_mobs" -> { enabled.serverWorld = true; yield WATER_AMBIENT_MOBS; }
            case "underground_water_creatures" -> { enabled.serverWorld = true; yield UNDERGROUND_WATER_CREATURE; }
            case "axolotls" -> { enabled.serverWorld = true; yield AXOLOTLS; }
            case "misc_mobs" -> { enabled.serverWorld = true; yield MISC_MOBS; }
            case "java_bit" -> JAVA_BIT;
            case "cpu_cores" -> CPU_CORES;
            case "cpu_threads" -> CPU_THREADS;
            case "display_width" -> DISPLAY_WIDTH;
            case "display_height" -> DISPLAY_HEIGHT;
            case "display_refresh_rate" -> DISPLAY_REFRESH_RATE;
//            case "mods" -> MODS;
            case "ping" -> {enabled.pingMetrics = true; yield PING;}
            case "latency" -> LATENCY;
            case "time", "solar_time" -> SOLAR_TIME;
            case "lunar_time" -> LUNAR_TIME;

            case "particles", "p" -> PARTICLES;
            case "streaming_sounds", "sounds" -> STREAMING_SOUNDS;
            case "max_streaming_sounds", "max_sounds" -> MAX_STREAMING_SOUNDS;
            case "static_sounds" -> STATIC_SOUNDS;
            case "max_static_sounds" -> MAX_STATIC_SOUNDS;

            case "slots_used" -> {enabled.slots = true; yield SLOTS_USED;}
            case "slots_empty" -> {enabled.slots = true; yield SLOTS_EMPTY;}

            case "health","hp" -> HEALTH;
            case "max_health","max_hp" -> HEALTH_MAX;
            case "food","hunger" -> FOOD_LEVEL;
            case "food_per","food_percentage" -> FOOD_LEVEL_PERCENTAGE;
            case "saturation" -> SATURATION_LEVEL;
            case "saturation_per","saturation_percentage" -> SATURATION_LEVEL_PERCENTAGE;
            case "armor","armour" -> ARMOR_LEVEL;
            case "armor_per","armor_percentage","armour_per","armour_percentage" -> ARMOR_LEVEL_PERCENTAGE;
            case "air" -> AIR_LEVEL;
            case "xp_level" -> XP_LEVEL;
            case "xp" -> XP_POINTS;
            case "xp_needed" -> XP_POINTS_NEEDED;

            case "bb_erosion","biome_builder_erosion" -> {enabled.serverWorld = true; yield BIOME_BUILDER_EROSION;}
            case "bb_temp","biome_builder_temperature" -> {enabled.serverWorld = true; yield BIOME_BUILDER_TEMPERATURE;}
            case "bb_veg","biome_builder_vegetation" -> {enabled.serverWorld = true; yield BIOME_BUILDER_VEGETATION;}

            case "item_durability", "item_dur" -> ITEM_DURABILITY;
            case "item_max_durability", "item_max_dur" -> ITEM_MAX_DURABILITY;
            case "offhand_item_durability", "oitem_dur" -> OFFHAND_ITEM_DURABILITY;
            case "offhand_item_max_durability", "oitem_max_dur" -> OFFHAND_ITEM_MAX_DURABILITY;
            case "lcps" -> { enabled.clicksPerSeconds = true; yield LCPS; }
            case "rcps" -> { enabled.clicksPerSeconds = true; yield RCPS; }

            case "hour12", "hour", "hours12", "hours" -> { enabled.time = true; yield TIME_HOUR_12; }

            case "real_year" -> REAL_YEAR;
            case "real_month" -> REAL_MONTH;
            case "real_day" -> REAL_DAY;
            case "real_day_of_week", "real_dow" -> REAL_DAY_OF_WEEK;
            case "real_day_of_year", "real_doy" -> REAL_DAY_OF_YEAR;
            case "real_hour12", "real_hour" -> REAL_HOUR_12;
            case "real_hour24" -> REAL_HOUR_24;
            case "real_minute" -> REAL_MINUTE;
            case "real_second" -> REAL_SECOND;
            case "real_millisecond", "real_ms" -> REAL_MICROSECOND;

            case "resource_pack_version" -> RESOURCE_PACK_VERSION;
            case "data_pack_version", "datapack_version" -> DATA_PACK_VERSION;

            case "mainhand_slot" -> MAINHAND_SLOT;

            default -> null;
        };
    }

    private static NumberSupplierElement.Entry getDecimalSupplier(String element, ComplexData.Enabled enabled) {
        if (element.startsWith("velocity_"))
            enabled.velocity = true;
        return switch (element) {
            case "x" -> X;
            case "y" -> Y;
            case "z" -> Z;
            case "nether_x", "nx" -> NETHER_X;
            case "nether_z", "nz" -> NETHER_Z;
            case "target_entity_x", "tex" -> {enabled.targetEntity = true; yield TARGET_ENTITY_X;}
            case "target_entity_y", "tey" -> {enabled.targetEntity = true; yield TARGET_ENTITY_Y;}
            case "target_entity_z", "tez" -> {enabled.targetEntity = true; yield TARGET_ENTITY_Z;}
            case "target_entity_distance", "ted" -> {enabled.targetEntity = true; yield TARGET_ENTITY_DISTANCE;}
            case "last_hit_distance", "lhd" -> {enabled.targetEntity = true; yield LAST_HIT_ENTITY_DISTANCE;}
            case "hooked_entity_x", "hex" -> HOOKED_ENTITY_X;
            case "hooked_entity_y", "hey" -> HOOKED_ENTITY_Y;
            case "hooked_entity_z", "hez" -> HOOKED_ENTITY_Z;
            case "hooked_entity_distance", "hed" -> HOOKED_ENTITY_DISTANCE;
            case "hooked_entity_direction_yaw", "hedy" -> HOOKED_ENTITY_DIRECTION_YAW;
            case "hooked_entity_direction_pitch", "hedp" -> HOOKED_ENTITY_DIRECTION_PITCH;
            case "vehicle_entity_health", "vehicle_health", "veh" -> VEHICLE_ENTITY_HEALTH;
            case "vehicle_entity_max_health", "vehicle_max_health", "vemh" -> VEHICLE_ENTITY_MAX_HEALTH;
            case "vehicle_horse_jump", "horse_jump", "vhj" -> VEHICLE_HORSE_JUMP;
            case "reach_distance", "reach" -> REACH_DISTANCE;
            case "fishing_hook_distance" -> FISHING_HOOK_DISTANCE;
            case "velocity_xz" -> VELOCITY_XZ;
            case "velocity_y" -> VELOCITY_Y;
            case "velocity_xyz" -> VELOCITY_XYZ;
            case "velocity_xz_kmh" -> VELOCITY_XZ_KMH;
            case "velocity_y_kmh" -> VELOCITY_Y_KMH;
            case "velocity_xyz_kmh" -> VELOCITY_XYZ_KMH;
            case "yaw" -> YAW;
            case "pitch" -> PITCH;
            case "day" -> DAY;
            case "mood" -> MOOD;
            case "tps" -> TPS;
            case "memory_used_percentage" -> MEMORY_USED_PERCENTAGE;
            case "memory_used" -> MEMORY_USED;
            case "memory_total" -> TOTAL_MEMORY;
            case "memory_allocated_percentage" -> ALLOCATED_PERCENTAGE;
            case "memory_allocated" -> ALLOCATED;
            case "cpu_usage", "cpu" -> {enabled.cpu = true; yield CPU_USAGE;}
            case "gpu_usage", "gpu" -> {enabled.gpuMetrics = true; yield GPU_USAGE;}
            case "ms_ticks", "tick_ms" -> TICK_MS;
            case "frame_ms_min" -> { enabled.frameMetrics = true; yield FRAME_MS_MIN;}
            case "frame_ms_max" -> { enabled.frameMetrics = true; yield FRAME_MS_MAX;}
            case "frame_ms_avg" -> { enabled.frameMetrics = true; yield FRAME_MS_AVG;}
            case "fps_min" -> { enabled.frameMetrics = true; yield FPS_MIN;}
            case "fps_max" -> { enabled.frameMetrics = true; yield FPS_MAX;}
            case "fps_avg" -> { enabled.frameMetrics = true; yield FPS_AVG;}
            case "frame_ms_samples","fps_samples" -> { enabled.frameMetrics = true; yield FRAME_MS_SAMPLES;}
            case "tick_ms_min" -> { enabled.tickMetrics = true; yield TICK_MS_MIN;}
            case "tick_ms_max" -> { enabled.tickMetrics = true; yield TICK_MS_MAX;}
            case "tick_ms_avg" -> { enabled.tickMetrics = true; yield TICK_MS_AVG;}
            case "tick_ms_samples" -> { enabled.tickMetrics = true; yield TICK_MS_SAMPLES;}
            case "tps_min" -> { enabled.tpsMetrics = true; yield TPS_MIN;}
            case "tps_max" -> { enabled.tpsMetrics = true; yield TPS_MAX;}
            case "tps_avg" -> { enabled.tpsMetrics = true; yield TPS_AVG;}
            case "tps_samples" -> { enabled.tpsMetrics = true; yield TICK_MS_SAMPLES;}
            case "ping_min" -> { enabled.pingMetrics = true; yield PING_MIN;}
            case "ping_max" -> { enabled.pingMetrics = true; yield PING_MAX;}
            case "ping_avg" -> { enabled.pingMetrics = true; yield PING_AVG;}
            case "ping_samples" -> { enabled.pingMetrics = true; yield PING_SAMPLES;}
            case "packet_size_min" -> { enabled.packetMetrics = true; yield PACKET_SIZE_MIN;}
            case "packet_size_max" -> { enabled.packetMetrics = true; yield PACKET_SIZE_MAX;}
            case "packet_size_avg" -> { enabled.packetMetrics = true; yield PACKET_SIZE_AVG;}
            case "packet_size_samples" -> { enabled.packetMetrics = true; yield PACKET_SIZE_SAMPLES;}
            case "slots_percentage", "slots_per" -> {enabled.slots = true; yield SLOTS_PERCENTAGE;}
            case "record_elapsed_percentage","record_elapsed_per" -> {enabled.music = true; yield RECORD_ELAPSED_PER;}
            case "record_length" -> {enabled.music = true; yield RECORD_LENGTH;}
            case "record_elapsed" -> {enabled.music = true; yield RECORD_ELAPSED;}
            case "record_remaining" -> {enabled.music = true; yield RECORD_REMAINING;}

            case "xp_per", "xp_percentage" -> XP_POINTS_PER;
            case "air_per", "air_percentage" -> AIR_LEVEL_PERCENTAGE;
            case "health_per", "health_percentage", "hp_per" -> HEALTH_PERCENTAGE;

            case "nr_temp","noise_temperature" -> {enabled.serverWorld = true; yield NOISE_ROUTER_TEMPERATURE;}
            case "nr_veg","noise_vegetation" -> {enabled.serverWorld = true; yield NOISE_ROUTER_VEGETATION;}
            case "nr_cont","noise_continents" -> {enabled.serverWorld = true; yield NOISE_ROUTER_CONTINENTS;}
            case "nr_erosion","noise_erosion" -> {enabled.serverWorld = true; yield NOISE_ROUTER_EROSION;}
            case "nr_depth","noise_depth" -> {enabled.serverWorld = true; yield NOISE_ROUTER_DEPTH;}
            case "nr_ridges","noise_ridges" -> {enabled.serverWorld = true; yield NOISE_ROUTER_RIDGES;}
            case "nr_peaks","noise_peaks" -> {enabled.serverWorld = true; yield NOISE_ROUTER_PEAKS;}
            case "nr_init","noise_init_density" -> {enabled.serverWorld = true; yield NOISE_ROUTER_INIT_DENSITY;}
            case "nr_final","noise_final_density" -> {enabled.serverWorld = true; yield NOISE_ROUTER_FINAL_DENSITY;}

            case "item_durability_percent", "item_dur_per" -> ITEM_DURABILITY_PERCENT;
            case "offhand_item_durability_percent", "oitem_dur_per" -> OFFHAND_ITEM_DURABILITY_PERCENT;
            case "local_difficulty" -> { enabled.localDifficulty = enabled.world = true; yield LOCAL_DIFFICULTY; }
            case "clamped_local_difficulty" -> { enabled.localDifficulty = enabled.world = true; yield CLAMPED_LOCAL_DIFFICULTY; }

            case "actionbar_remaining" -> ACTIONBAR_REMAINING;
            case "title_remaining" -> TITLE_REMAINING;

            case "pi", "π" -> PI;
            case "tau", "τ" -> TAU;
            case "phi", "φ", "golden_ratio" -> PHI;
            case "e" -> E;

            default -> null;
        };
    }

    private static SpecialSupplierElement.Entry getSpecialSupplierElements(String element, ComplexData.Enabled enabled) {
        return switch (element) {
            case "profile_keybind" -> PROFILE_KEYBIND;
            case "hour24", "hours25" -> { enabled.time = true; yield TIME_HOUR_24; }
            case "minute", "minutes" -> { enabled.time = true; yield TIME_MINUTES; }
            case "second", "seconds" -> { enabled.time = true; yield TIME_SECONDS; }
            case "target_block", "tb" -> {enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK;}
            case "target_block_id", "tbi" -> {enabled.world = enabled.targetBlock = true; yield TARGET_BLOCK_ID;}
            case "target_fluid", "tf" -> {enabled.world = enabled.targetFluid = true; yield TARGET_FLUID;}
            case "target_fluid_id", "tfi" -> {enabled.world = enabled.targetFluid = true; yield TARGET_FLUID_ID;}
            case "item" -> ITEM_OLD;
            case "item_name" -> ITEM_NAME;
            case "item_id" -> ITEM_ID;
            case "offhand_item", "oitem" -> OFFHAND_ITEM;
            case "offhand_item_name" -> OFFHAND_ITEM_NAME;
            case "offhand_item_id", "oitem_id" -> OFFHAND_ITEM_ID;
            case "clouds" -> CLOUDS;
            case "graphics_mode" -> GRAPHICS_MODE;
            case "facing_towards_pn_word" -> FACING_TOWARDS_PN_WORD;
            case "facing_towards_pn_sign" -> FACING_TOWARDS_PN_SIGN;
            case "gamemode" -> GAMEMODE;
            default -> null;
        };
    }



    private static HudElement getListSupplierElements(String part, Profile profile, int debugLine, ComplexData.Enabled enabled, String original, ListProvider listProvider) {
        List<String> parts = partitionConditional(part);
        if (parts.isEmpty()) {
            Errors.addError(profile.name, debugLine, original, ErrorType.UNKNOWN_SLOT, "WHY U EMPTY");
            return null;
        }
        ListProvider provider = getListProvider(parts.get(0), profile, debugLine, enabled, original, listProvider);

        if (provider == null)
            return null;
        if (provider == ListProvider.REGUIRES_MODMENU) {
            Errors.addError(profile.name, debugLine, original, ErrorType.REQUIRES_MODMENU, "");
            return new FunctionalElement.IgnoreErrorElement();
        }
        if (parts.size() == 1)
            return new ListCountElement(provider);

        String format = parts.get(1).trim();
        if (!format.startsWith("\"") || !format.endsWith("\"")) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_LIST, null);
            return null;
        }
        format = format.substring(1, format.length()-1);
        System.out.println("Format: " + format);

        return new ListElement(provider, addElements(format, profile, debugLine, enabled, false, provider));
    }

    public static ListProvider getListProvider(String variable, Profile profile, int debugLine, ComplexData.Enabled enabled, String original, ListProvider listProvider) {
        variable = variable.trim();

        if (variable.startsWith("loop"))
            return getLoopProvider(variable, profile, debugLine, enabled, original, listProvider);

        return switch (variable) {
            case "effects" -> STATUS_EFFECTS;
            case "pos_effects", "positive_effects" -> STATUS_EFFECTS_POSITIVE;
            case "neg_effects", "negative_effects" -> STATUS_EFFECTS_NEGATIVE;
            case "neu_effects", "neutral_effects" -> STATUS_EFFECTS_NEUTRAL;
            case "players" -> ONLINE_PLAYERS;
            case "subtitles" -> {enabled.subtitles = true; yield SUBTITLES;}
            case "target_block_props", "target_block_properties", "tbp" -> {enabled.targetBlock = true; yield TARGET_BLOCK_STATES;}
            case "target_block_tags", "tbt" -> {enabled.targetBlock = true; yield TARGET_BLOCK_TAGS;}
            case "attributes" -> PLAYER_ATTRIBUTES;
            case "target_entity_attributes", "target_entity_attrs", "teas" -> {enabled.targetEntity = true; yield TARGET_ENTITY_ATTRIBUTES;}
            case "hooked_entity_attributes", "hooked_entity_attrs", "heas" -> HOOKED_ENTITY_ATTRIBUTES;
            case "target_villager_offers", "tvo" -> {enabled.targetEntity = true; enabled.targetVillager = true; yield TARGET_VILLAGER_OFFERS;}
            case "teams" -> TEAMS;
            case "items" -> ITEMS;
            case "inventory_items", "inv_items" -> INV_ITEMS;
            case "armor_items" -> ARMOR_ITEMS;
            case "hotbar_items" -> HOTBAR_ITEMS;
            case "all_items" -> ALL_ITEMS;
            case "objectives" -> SCOREBOARD_OBJECTIVES;
            case "scores" -> PLAYER_SCOREBOARD_SCORES;
            case "bossbars" -> BOSSBARS;
            case "all_bossbars" -> ALL_BOSSBARS;
            case "mods" -> CustomHud.MODMENU_INSTALLED ? MODS : ListProvider.REGUIRES_MODMENU;
            case "all_root_mods" -> CustomHud.MODMENU_INSTALLED ? ALL_ROOT_MODS : ListProvider.REGUIRES_MODMENU;
            case "all_mods" -> CustomHud.MODMENU_INSTALLED ? ALL_MODS : ListProvider.REGUIRES_MODMENU;
            case "resource_packs" -> RESOURCE_PACKS;
            case "disabled_resource_packs" -> DISABLED_RESOURCE_PACKS;
            case "data_packs", "datapacks" -> DATA_PACKS;
            case "disabled_data_packs", "disabled_datapacks" -> DISABLED_DATA_PACKS;
            case "records" -> {enabled.music = true; yield RECORDS;}

            default -> null;
        };
    }

    public static ListProvider getLoopProvider(String variable, Profile profile, int debugLine, ComplexData.Enabled enabled, String original, ListProvider listProvider) {
        String argsStr = variable.substring(4);
        if (!argsStr.startsWith("[") || !argsStr.endsWith("]")) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_LOOP, null);
            return null;
        }

        List<String> parts = partitionConditional(argsStr.substring(1, argsStr.length()-1));
        if (parts.isEmpty() || parts.size() > 3) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_LOOP, null);
            return null;
        }
        Operation startO, endO, stepO;
        if (parts.size() == 1) {
            startO = new Operation.Literal(0);
            endO = ExpressionParser.parseExpression(parts.get(0), original, profile, debugLine, enabled, listProvider);
            stepO = new Operation.Literal(1);
        }
        else if (parts.size() == 2) {
            startO = ExpressionParser.parseExpression(parts.get(0), original, profile, debugLine, enabled, listProvider);
            endO = ExpressionParser.parseExpression(parts.get(1), original, profile, debugLine, enabled, listProvider);
            stepO = new Operation.Literal(1);
        }
        else {
            startO = ExpressionParser.parseExpression(parts.get(0), original, profile, debugLine, enabled, listProvider);
            endO = ExpressionParser.parseExpression(parts.get(1), original, profile, debugLine, enabled, listProvider);
            stepO = ExpressionParser.parseExpression(parts.get(2), original, profile, debugLine, enabled, listProvider);
        }

        ListProvider provider = () -> {
            List<Number> values = new ArrayList<>();
            double end = endO.getValue();
            double step = stepO.getValue();
            for (double i = startO.getValue(); i < end; i += step)
                values.add(i);
            return values;
        };
        ATTRIBUTER_MAP.put(provider, LOOP_ITEM);
        return provider;
    }

    public static HudElement barElement(String part, Profile profile, int debugLine, ComplexData.Enabled enabled, String original, ListProvider provider) {
        if (part.indexOf(',') == -1) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_BAR, null);
            return null;
        }

        List<String> parts = partitionConditional(part);
        System.out.println("SUPPLIER");
        for (String p : parts) {
            System.out.println("`" + p + "`");
        }
        if (parts.size() < 3) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_BAR, null);
            return null;
        }

        Operation op1 = ExpressionParser.parseExpression(parts.get(1).trim(), part, profile, debugLine, enabled, provider);
        Operation op2 = ExpressionParser.parseExpression(parts.get(2).trim(), part, profile, debugLine, enabled, provider);
        Flags flags;
        if (parts.size() < 4)
            flags = new Flags();
        else {
            StringBuilder str = new StringBuilder();
            for (int i = 3; i < parts.size(); i++)
                str.append(parts.get(i)).append(",");
            flags = Flags.parse(profile.name, debugLine, str.substring(0, str.length()-1).split(" "));
        }

        ProgressBarIcon.BarStyle style = null;
        int collinIndex = parts.get(0).indexOf(':');
        if (collinIndex != -1)
            style = ProgressBarIcon.getStyle(parts.get(0).substring(collinIndex+1));

        return new ProgressBarIcon(op1, op2, style, flags);

    }


    public static HudElement listElement(ListProvider provider, String part, int commaIndex, Profile profile, int debugLine, ComplexData.Enabled enabled, String original) {
        if (commaIndex == -1)
            return new ListCountElement(provider);

        List<String> parts = partitionConditional(part);
        System.out.println("SUPPLIER");
        for (String p : parts) {
            System.out.println("`" + p + "`");
        }
        if (parts.size() < 2)
            return null;

        String format = parts.get(1).trim();
        if (!format.startsWith("\"") || !format.endsWith("\"")) {
            Errors.addError(profile.name, debugLine, original, ErrorType.MALFORMED_LIST, null);
            return null;
        }
        format = format.substring(1, format.length()-1);
        System.out.println("Format: " + format);

        return new ListElement(provider, addElements(format, profile, debugLine, enabled, false, provider));
    }

    private static final Supplier<String> RAW = () -> ListManager.getValue().toString();
    private static HudElement getListAttributeSupplierElement(String name, Flags flags, ListProvider listProvider) {
        if (name.endsWith(",")) name = name.substring(0, name.length()-1);
        return switch (name) {
            case "count", "c" -> new NumberSupplierElement(ListManager::getCount, flags);
            case "index", "i" -> new NumberSupplierElement(ListManager::getIndex, flags);
            case "raw" -> new StringSupplierElement(RAW);
            default -> Attributers.get(listProvider, ListManager.SUPPLIER, name, flags);
        };

    }

    public static HudElement getAttributeElement(String part, Profile profile, int debugLine, ComplexData.Enabled enabled, String original) {
        if (part.startsWith("item:"))
            return attrElement(part, SLOT_READER, (slot) -> () -> CLIENT.player.getStackReference(slot).get(),
                    ITEM, ErrorType.UNKNOWN_ITEM_ID, ErrorType.UNKNOWN_ITEM_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("attribute:"))
            return attrElement(part, ENTITY_ATTR_READER, (attr) -> () -> getEntityAttr(CLIENT.player, attr),
                    ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("target_entity_attribute:") || part.startsWith("target_entity_attr:") || part.startsWith("tea:"))
            return attrElement(part, ENTITY_ATTR_READER, (attr) -> () -> getEntityAttr(ComplexData.targetEntity, attr),
                    ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("hooked_entity_attribute:") || part.startsWith("hooked_entity_attr:") || part.startsWith("hea:"))
            return attrElement(part, ENTITY_ATTR_READER, (attr) -> () -> getEntityAttr(CLIENT.player.fishHook == null ? null : CLIENT.player.fishHook.getHookedEntity(), attr),
                    ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE, ErrorType.UNKNOWN_ATTRIBUTE_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("team:"))
            return attrElement(part, src -> src, (team) -> () -> CLIENT.world.getScoreboard().getTeam(team),
                    TEAM, null, ErrorType.UNKNOWN_TEAM_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("objective:"))
            return attrElement(part, src -> src, (name) -> () -> CLIENT.world.getScoreboard().getNullableObjective(name),
                    SCOREBOARD_OBJECTIVE, null, ErrorType.UNKNOWN_OBJECTIVE_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("bossbar:"))
            return attrElement(part, src -> src, (name) -> () -> AttributeHelpers.getBossBar(name),
                    BOSSBAR, null, ErrorType.UNKNOWN_BOSSBAR_PROPERTY, profile, debugLine, enabled, original);

        if (part.startsWith("mod:"))
            return attrElement(part, ModMenu.MODS::get, (mod) -> () -> mod,
                    MOD, ErrorType.UNKNOWN_MOD, ErrorType.UNKNOWN_MOD_PROPERTY, profile, debugLine, enabled, original );

        if (part.startsWith("resource_pack:"))
            return attrElement(part, (src) -> CLIENT.getResourcePackManager().getProfile(src), (pack) -> () -> pack,
                    PACK, ErrorType.UNKNOWN_RESOURCE_PACK, ErrorType.UNKNOWN_PACK_PROPERTY, profile, debugLine, enabled, original );

        if (part.startsWith("data_pack:") || part.startsWith("datapack:"))
            return attrElement(part, DATA_PACK_READER, (pack) -> () -> pack,
                    PACK, ErrorType.UNKNOWN_DATA_PACK, ErrorType.UNKNOWN_PACK_PROPERTY, profile, debugLine, enabled, original );

        return null;
    }

    public static <T> HudElement attrElement(String part, Function<String,T> reader, Function<T,Supplier<?>> supplier,
                                              Attributer attributer, ErrorType unknownX, ErrorType unknownAttribute,
                                              Profile profile, int debugLine, ComplexData.Enabled enabled, String original) {
        Matcher matcher = ITEM_VARIABLE_PATTERN.matcher(part.substring(part.indexOf(':')+1));

        if (!matcher.matches()) return null;

        String src = matcher.group(1) == null ? "" : matcher.group(1);
        String method = matcher.group(2) == null ? "" : matcher.group(2);

        T value = reader.apply(src);
        if (value == null) {
            Errors.addError(profile.name, debugLine, original, unknownX, src);
            return new IgnoreErrorElement();
        }

        //TODO: I removed `&& part.indexOf('\'') != -1`, not 100% how it worked
        boolean hasQuote = part.indexOf('"') != -1 ;
        Flags flags = hasQuote ? new Flags() : Flags.parse(profile.name, debugLine, part.split(" "));
        HudElement element = attributer.get(supplier.apply(value), method, flags);

        if (element == null) {
            Errors.addError(profile.name, debugLine, original, unknownAttribute, method);
            return new IgnoreErrorElement();
        }
        if ( !(element instanceof CreateListElement) )
            return Flags.wrap(element, flags);
        return element;

    }


}
