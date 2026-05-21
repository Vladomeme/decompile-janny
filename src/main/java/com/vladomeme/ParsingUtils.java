package com.vladomeme;

public class ParsingUtils {

    public static boolean isFunctionHeader(String line) {
        char c = line.charAt(0);
        return line.length() > 3 && (c != ' ' && c != '/' && c != '}'
                && (c != 'L' || (line.charAt(1) != 'A' && line.charAt(2) != 'B' && line.charAt(3) != '_'))
                && line.charAt(line.length() - 1) == '{' && line.charAt(line.length() - 3) == ')');
    }

    public static boolean textAfterEquals(String line, int pos, String text) {
        char[] chars = text.toCharArray();
        int charPos = 0;

        while (pos != line.length() && charPos != chars.length) {
            if (line.charAt(pos++) != chars[charPos++]) return false;
        }
        return charPos == chars.length;
    }

    public static boolean textBeforeEquals(String line, int pos, String text) {
        char[] chars = text.toCharArray();
        int charPos = chars.length - 1;

        while (pos != 0 && charPos != 0) {
            if (line.charAt(pos--) != chars[charPos--]) return false;
        }
        return charPos == 0;
    }

    public static int skipWhile(String line, int pos, char c) {
        while (pos != line.length() && line.charAt(pos) == c) pos++;
        return pos;
    }

    public static int skipWhile(String line, int pos, char... chars) {
        loop:
        while (pos != line.length()) {
            char current = line.charAt(pos);
            for (char c : chars) {
                if (c == current) {
                    pos++;
                    continue loop;
                }
            }
            break;
        }
        return pos;
    }

    @SuppressWarnings("SameParameterValue")
    public static int skipUntil(String line, int pos, char c) {
        int index = line.indexOf(c, pos);
        return index != -1 ? index : line.length();
    }

    public static int skipUntil(String line, int pos, char... chars) {
        while (pos != line.length()) {
            char current = line.charAt(pos);
            for (char c : chars) {
                if (c == current) return pos;
            }
            pos++;
        }
        return pos;
    }

    public static int skipUntilReverse(String line, int pos, char... chars) {
        while (pos != -1) {
            char current = line.charAt(pos);
            for (char c : chars) {
                if (c == current) return pos;
            }
            pos--;
        }
        return pos;
    }
}
