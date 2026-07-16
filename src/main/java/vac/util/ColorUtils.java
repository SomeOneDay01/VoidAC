package vac.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = processGradients(text);
        result = processHex(result);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private static String processGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(applyGradient(startHex, endHex, content)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String applyGradient(String startHex, String endHex, String text) {
        if (text.isEmpty()) return "";
        int r1 = Integer.parseInt(startHex.substring(0, 2), 16);
        int g1 = Integer.parseInt(startHex.substring(2, 4), 16);
        int b1 = Integer.parseInt(startHex.substring(4, 6), 16);
        int r2 = Integer.parseInt(endHex.substring(0, 2), 16);
        int g2 = Integer.parseInt(endHex.substring(2, 4), 16);
        int b2 = Integer.parseInt(endHex.substring(4, 6), 16);

        StringBuilder sb = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float ratio = len == 1 ? 0.5f : (float) i / (len - 1);
            int r = (int) (r1 + (r2 - r1) * ratio);
            int g = (int) (g1 + (g2 - g1) * ratio);
            int b = (int) (b1 + (b2 - b1) * ratio);
            sb.append(ChatColor.of(String.format("#%02x%02x%02x", r, g, b)));
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private static String processHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
