package com.vladomeme;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//todo eliminate methodInfo arguments
//todo move opening angle brackets to the same line
//todo ghidra decompile settings warning
//todo memorize the last used text arguments
//todo procedure order dependence warning
//todo low memory mode with disc buffer
public class Executor {

    static String[] lines;
    static StringBuilder resultBuilder;
    static int TAB_LENGTH;
    static JLabel processLabel;
    static JLabel progressLabel;
    static boolean skipArrayReset = false;

    static void execute(List<ExecutionOption> options, Path path, boolean saveToCopy) {
        getIndentation();
        if (TAB_LENGTH <= 0) {
            JOptionPane.showMessageDialog(null, "Valid indentation length is required to proceed.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        prepareData(path);

        for (ExecutionOption option : options) {
            if (option.enabled) {
                option.runnable.run();
                resetLineArray();
                ProgressTracker.reset();
            }
        }

        try {
            System.out.println("Writing...");
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(Path.of(path + (saveToCopy ? "_output" : "")).toUri())));
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            System.out.println("Finished!");
            JOptionPane.showMessageDialog(null, "All operations are completed.\nSaved to: " + path + (saveToCopy ? "_output" : ""),
                    "Finished!", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        lines = null;
        resultBuilder = null;
        TAB_LENGTH = 0;
    }

    private static void prepareData(Path path) {
        System.out.println("Preparing data...");
        File sourceFile = new File(path.toUri());
        long capacity = (long) (sourceFile.length() * 1.1);
        System.out.println("Builder capacity: " + capacity);
        if (capacity > Integer.MAX_VALUE) {
            JOptionPane.showMessageDialog(null, "Source file is too large to process.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultBuilder = new StringBuilder((int) capacity);

        try {
            lines = Files.readAllLines(path).toArray(new String[0]);
            System.out.println("Current line count: " + lines.length);
            ProgressTracker.reset();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void getIndentation() {
        String tabLengthString = JOptionPane.showInputDialog(null, "Enter indent (tab) length:");

        try {
            TAB_LENGTH = Integer.parseInt(tabLengthString);
        }
        catch (NumberFormatException e) {
            TAB_LENGTH = -1;
        }
    }

    private static void resetLineArray() {
        if (skipArrayReset) {
            System.out.println("Operation was skipped");
            skipArrayReset = false;
        }
        else {
            lines = resultBuilder.toString().split("\\R");
            System.out.println("Current line count: " + lines.length);
            resultBuilder.setLength(0);
        }
    }

    static void removeUselessDecompilerComments() {
        System.out.print("Removing compilator comments...    ");
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.contains("// DISPLAY WARNING: Type casts") || line.contains("// WARNING: Subroutine does not return")) continue;
            resultBuilder.append(line).append(System.lineSeparator());
        }
    }

    static void removeNullPointerTypeCasts() {
        System.out.print("Removing null pointer type casts...    ");
        Matcher matcher = Pattern.compile("(\\([^(]*\\)0x?0?)").matcher("");
        for (String line : lines) {
            ProgressTracker.progress();
            matcher.reset(line);
            resultBuilder.append(matcher.replaceAll("NULL")).append(System.lineSeparator());
        }
    }

    static void removeMethodInitialization() {
        System.out.print("Removing method initialization blocks...    ");

        Matcher matcher = Pattern.compile("^ *FUN_.........\\(.*?\\);").matcher("");

        boolean inBlock = false;
        String blockEnd = null;
        List<String> currentVariables = new ArrayList<>();

        int pos;
        int indentationPos;
        int sanityPos;
        int variablePos;
        String variableName;
        String header = null;

        loop:
        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                //check if block contents are actually a metadata initialization, and it's not just a fucked up null check
                /*
                if (cVar4 == '\0') {
                    if (DAT_1834059fd == '\0') {
                        FUN_180281b10(6494855752);
                        DAT_1834059fd = '\x01';
                    }
                 */
                if (header != null) {
                    matcher.reset(line);
                    if (!matcher.find()) {
                        resultBuilder.append(header).append(System.lineSeparator());
                        inBlock = false;

                        //the entire "out-of-block" code segment copy-pasted for very rare cases :(
                        if (line.isEmpty()) {
                            resultBuilder.append(line).append(System.lineSeparator());
                            continue;
                        }
                        //method end
                        if (line.charAt(0) == '}') {
                            currentVariables.clear();
                            resultBuilder.append(line).append(System.lineSeparator());
                            continue;
                        }
                        //checking if current line is a header of a null check if block
                        if (line.charAt(line.length() - 1) == '{') {
                            pos = 0;
                            //skip indentation
                            while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                            indentationPos = pos;

                            //check 'if (' segment
                            if (line.charAt(pos++) != 'i' || line.charAt(pos++) != 'f' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '(') {
                                resultBuilder.append(line).append(System.lineSeparator());
                                continue;
                            }

                            //getting variable name
                            while (line.charAt(pos) != ' ' && line.charAt(pos) != ')') pos++;
                            variablePos = pos;

                            //check the rest of the header
                            pos++;
                            if (line.charAt(pos++) != '=' || line.charAt(pos++) != '=' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '\''
                                    || line.charAt(pos++) != '\\' || line.charAt(pos++) != '0' || line.charAt(pos++) != '\'' || line.charAt(pos++) != ')'
                                    || line.charAt(pos++) != ' ' || line.charAt(pos) != '{') {

                                //Not an inlined check, try to match with found variables
                                variableName = line.substring(indentationPos + 4, variablePos);

                                for (int i = 0; i != currentVariables.size(); i++) {
                                    if (currentVariables.get(i).equals(variableName)) {
                                        currentVariables.remove(i);
                                        inBlock = true;
                                        blockEnd = line.substring(0, indentationPos) + "}";
                                        header = line;
                                        continue loop;
                                    }
                                }
                                resultBuilder.append(line).append(System.lineSeparator());
                                continue;
                            }
                            //start removing the if block
                            inBlock = true;
                            blockEnd = line.substring(0, indentationPos) + "}";
                            header = line;
                            continue;
                        }
                        else {
                            //bVar10 = DAT_18340b0bf == '\0';
                            //check for un-inlined variable initialization
                            pos = line.length() - 1;
                            if (line.charAt(pos--) == ';' && line.charAt(pos--) == '\'' && line.charAt(pos--) == '0' && line.charAt(pos--) == '\\'
                                    && line.charAt(pos--) == '\'' && line.charAt(pos--) == ' ' && line.charAt(pos--) == '=' && line.charAt(pos) == '=') {

                                pos -= 12;

                                if (line.charAt(pos--) == 'T' && line.charAt(pos--) == 'A' && line.charAt(pos--) == 'D' && line.charAt(pos--) == ' '
                                        && line.charAt(pos--) == '=' && line.charAt(pos) == ' ') {

                                    sanityPos = pos;
                                    pos = 0;

                                    //skip indentation
                                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                                    indentationPos = pos;

                                    //getting variable name
                                    while (line.charAt(pos) != ' ') pos++;
                                    if (pos == sanityPos)
                                        currentVariables.add(line.substring(indentationPos, pos));
                                    continue;
                                }
                            }
                        }
                        resultBuilder.append(line).append(System.lineSeparator());
                    }
                    else header = null;
                }
                else if (line.equals(blockEnd)) inBlock = false;
            }
            else {
                if (line.isEmpty()) {
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //method end
                if (line.charAt(0) == '}') {
                    currentVariables.clear();
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //checking if current line is a header of a null check if block
                if (line.charAt(line.length() - 1) == '{') {
                    pos = 0;
                    //skip indentation
                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                    indentationPos = pos;

                    //check 'if (' segment
                    if (line.charAt(pos++) != 'i' || line.charAt(pos++) != 'f' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '(') {
                        resultBuilder.append(line).append(System.lineSeparator());
                        continue;
                    }

                    //getting variable name
                    while (line.charAt(pos) != ' ' && line.charAt(pos) != ')') pos++;
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (line.charAt(pos++) != '=' || line.charAt(pos++) != '=' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '\''
                        || line.charAt(pos++) != '\\' || line.charAt(pos++) != '0' || line.charAt(pos++) != '\'' || line.charAt(pos++) != ')'
                        || line.charAt(pos++) != ' ' || line.charAt(pos) != '{') {

                        //Not an inlined check, try to match with found variables
                        variableName = line.substring(indentationPos + 4, variablePos);

                        for (int i = 0; i != currentVariables.size(); i++) {
                            if (currentVariables.get(i).equals(variableName)) {
                                currentVariables.remove(i);
                                inBlock = true;
                                blockEnd = line.substring(0, indentationPos) + "}";
                                header = line;
                                continue loop;
                            }
                        }
                        resultBuilder.append(line).append(System.lineSeparator());
                        continue;
                    }
                    //start removing the if block
                    inBlock = true;
                    blockEnd = line.substring(0, indentationPos) + "}";
                    header = line;
                    continue;
                }
                else {
                    //bVar10 = DAT_18340b0bf == '\0';
                    //check for un-inlined variable initialization
                    pos = line.length() - 1;
                    if (line.charAt(pos--) == ';' && line.charAt(pos--) == '\'' && line.charAt(pos--) == '0' && line.charAt(pos--) == '\\'
                        && line.charAt(pos--) == '\'' && line.charAt(pos--) == ' ' && line.charAt(pos--) == '=' && line.charAt(pos) == '=') {

                        pos -= 12;

                        if (line.charAt(pos--) == 'T' && line.charAt(pos--) == 'A' && line.charAt(pos--) == 'D' && line.charAt(pos--) == ' '
                            && line.charAt(pos--) == '=' && line.charAt(pos) == ' ') {

                            sanityPos = pos;
                            pos = 0;

                            //skip indentation
                            while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                            indentationPos = pos;

                            //getting variable name
                            while (line.charAt(pos) != ' ') pos++;
                            if (pos == sanityPos)
                                currentVariables.add(line.substring(indentationPos, pos));
                            continue;
                        }
                    }
                }
                resultBuilder.append(line).append(System.lineSeparator());
            }
        }
    }

    //todo sort out weirdness
    static void removeStaticInitialization() {
        System.out.print("Removing static initialization blocks...    ");

        String previous;
        String next;
        int i = 1;

        while (i != lines.length) {
            ProgressTracker.progress();

            if (lines[i].isEmpty()) {
                resultBuilder.append(lines[i - 1]).append(System.lineSeparator());
                i++;
                continue;
            }
            //realistically should never fall out of bounds
            if (lines[i].contains("il2cpp_runtime_class_init")) {
                previous = lines[i - 1];
                next = lines[i + 1];
                if (!previous.isEmpty() && previous.charAt(previous.length() - 1) == '{'
                        && !next.isEmpty() && next.charAt(next.length() - 1) == '}') {
                    i += 3;
                }
            }
            resultBuilder.append(lines[i - 1]).append(System.lineSeparator());
            i++;
        }
        resultBuilder.append(lines[i - 1]).append(System.lineSeparator());
        ProgressTracker.end();
    }

    static void simplifyProcedureInterruptions() {
        // REPLACING INTERRUPTION FUNCTION WITH 'return' //
        System.out.print("Simplifying procedure interruptions (step 1)...    ");
        String interruptionFunction = JOptionPane.showInputDialog(null,
                """
                        Enter the name of a targeted procedure interruption function.
                        It appears at the bottom of void functions (don't look at "FUN_"
                        or Unity/other library functions) after "Subroutine does not return"
                        warnings, usually formatted as FUN_*********.
                        """);
        if (interruptionFunction.length() < 13 || !interruptionFunction.substring(0, 13).matches("FUN_[0-9a-z]{9}")) {
            JOptionPane.showMessageDialog(null, "Invalid function name, skipping current procedure.", "Warning", JOptionPane.WARNING_MESSAGE);
            skipArrayReset = true;
            return;
        }
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.contains(interruptionFunction)) {
                resultBuilder.append(line.replaceAll(interruptionFunction + "\\(.*?\\);$", "return;")).append(System.lineSeparator());
            }
            else resultBuilder.append(line).append(System.lineSeparator());
        }
        resetLineArray();
        ProgressTracker.reset();

        // INTERRUPTION LABEL PROCESSING //
        System.out.print("Simplifying procedure interruptions (step 2)...    ");
        boolean inBlock = false;
        boolean checkLabel = false;
        int pos;
        int indentationPos;

        String label = null;
        List<String> methodLines = new ArrayList<>();
        Map<String, String> exitLabels = new HashMap<>();

        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                if (line.isEmpty()) {
                    methodLines.add("");
                    continue;
                }

                //check if label points to a return statement
                if (checkLabel) {
                    pos = 0;
                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                    indentationPos = pos;

                    if (line.charAt(pos++) == 'r' && line.charAt(pos++) == 'e' && line.charAt(pos++) == 't' && line.charAt(pos++) == 'u'
                            && line.charAt(pos++) == 'r' && line.charAt(pos) == 'n') {
                        exitLabels.put(label, line.substring(indentationPos));
                        methodLines.removeLast();
                    }
                    checkLabel = false;
                    methodLines.add(line);
                    continue;
                }
                //method end
                if (line.charAt(0) == '}') {
                    inBlock = false;
                    //remove unnecessary void return statements
                    for (int i = methodLines.size() - 1; i >= 0; i--) {
                        String methodLine = methodLines.get(i);
                        if (methodLine.isEmpty() || methodLine.charAt(0) != ' ') continue;
                        pos = 0;
                        while (pos != methodLine.length() && methodLine.charAt(pos) == ' ') pos++;
                        if (pos == methodLine.length() || methodLine.charAt(pos) == '}') continue;
                        if (methodLine.charAt(pos++) == 'r' && methodLine.charAt(pos++) == 'e' && methodLine.charAt(pos++) == 't' && methodLine.charAt(pos++) == 'u'
                                && methodLine.charAt(pos++) == 'r' && methodLine.charAt(pos++) == 'n' && methodLine.charAt(pos) == ';') {
                            methodLines.remove(i);
                        }
                        else break;
                    }
                    //goto statement replacement
                    for (String methodLine : methodLines) {
                        if (methodLine.isEmpty()) {
                            resultBuilder.append(System.lineSeparator());
                            continue;
                        }
                        if (methodLine.charAt(0) != ' ') {
                            resultBuilder.append(methodLine).append(System.lineSeparator());
                            continue;
                        }
                        pos = methodLine.indexOf("goto");
                        if (pos != -1 && methodLine.length() >= pos + 17 && methodLine.charAt(pos + 5) == 'L') {
                            label = methodLine.substring(pos + 5, pos + 18);
                            if (exitLabels.containsKey(label)) {
                                resultBuilder.append(methodLine, 0, pos).append(exitLabels.get(label)).append(System.lineSeparator());
                                continue;
                            }
                        }
                        resultBuilder.append(methodLine).append(System.lineSeparator());
                    }
                    methodLines.clear();
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //label
                if (line.charAt(0) == 'L' && line.charAt(1) == 'A' && line.charAt(2) == 'B' && line.charAt(3) == '_') {
                    checkLabel = true;
                    label = line.substring(0, 13);
                }
                methodLines.add(line);
            }
            else {
                if (line.isEmpty()) {
                    resultBuilder.append(System.lineSeparator());
                    continue;
                }
                //check if current line is a function header
                if ((line.charAt(0) != ' ' || line.charAt(0) != '/' || line.charAt(0) != '}'
                        || (line.charAt(0) != 'L' && line.charAt(1) != 'A' && line.charAt(2) != 'B' && line.charAt(3) != '_'))
                        && line.charAt(line.length() - 1) == '{') {
                    inBlock = true;
                }
                resultBuilder.append(line).append(System.lineSeparator());
            }
        }
    }

    //takes longer to run because of regex usage, but it also does a lot, so it's fine
    static void simplifyObjectReferences() {
        System.out.print("Simplifying object references...    ");
        Matcher fieldsMatcher = Pattern.compile("[*&]*\\(([^(]*)\\.fields\\)").matcher("");
        Matcher structureMatcher = Pattern.compile("[*&]*\\(([^ ()-]*)\\.(?:klass\\.vtable|fields)\\)").matcher("");
        Matcher ptr1Matcher = Pattern.compile("\\((([^.\\n\\r]*)\\._\\d*?_([a-zA-Z0-9_]*)\\.method)Ptr\\)\\((?:\\2,)?((?:.*?\\(.*?\\),|.*?,)*)\\1?\\)").matcher("");
        Matcher ptr2Matcher = Pattern.compile("\\((([^.\\n\\r]*)\\._\\d*?_([a-zA-Z0-9_]*)\\.method)Ptr\\)\\(\\2\\)").matcher("");
        Matcher klassMatcher = Pattern.compile("[*&]*\\(([^().]*)\\.(?:_\\d*|klass)?\\)(?=\\.)").matcher("");
        Matcher parenthesisMatcher = Pattern.compile("[*&]*\\(([^().]*\\.[^)]*)\\)(?=\\.)").matcher("");

        String variable;
        String method;
        String args;

        for (String line : lines) {
            ProgressTracker.progress();

            line = line.replace("->", ".");

            fieldsMatcher.reset(line);
            while (fieldsMatcher.find()) {
                line = line.substring(0, fieldsMatcher.start()) + fieldsMatcher.group(1) + line.substring(fieldsMatcher.end());
                fieldsMatcher.reset(line);
            }

            structureMatcher.reset(line);
            while (structureMatcher.find()) {
                line = line.substring(0, structureMatcher.start()) + structureMatcher.group(1) + line.substring(structureMatcher.end());
                structureMatcher.reset(line);
            }

            line = line.replace(".fields.", ".");
            line = line.replace(".static_fields.", ".");

            ptr1Matcher.reset(line);
            while (ptr1Matcher.find()) {
                variable = ptr1Matcher.group(2);
                method = ptr1Matcher.group(3);
                args = ptr1Matcher.group(4);
                if (!args.isEmpty()) args = args.substring(0, args.length() - 1);

                line = line.substring(0, ptr1Matcher.start()) + variable + '.' + method + '(' + args + ')' + line.substring(ptr1Matcher.end());
                ptr1Matcher.reset(line);
            }

            ptr2Matcher.reset(line);
            while (ptr2Matcher.find()) {
                variable = ptr2Matcher.group(2);
                method = ptr2Matcher.group(3);

                line = line.substring(0, ptr2Matcher.start()) + variable + '.' + method + "()" + line.substring(ptr2Matcher.end());
                ptr2Matcher.reset(line);
            }

            klassMatcher.reset(line);
            while (klassMatcher.find()) {
                line = line.substring(0, klassMatcher.start()) + klassMatcher.group(1) + line.substring(klassMatcher.end());
                klassMatcher.reset(line);
            }

            parenthesisMatcher.reset(line);
            while (parenthesisMatcher.find()) {
                line = line.substring(0, parenthesisMatcher.start()) + parenthesisMatcher.group(1) + line.substring(parenthesisMatcher.end());
                parenthesisMatcher.reset(line);
            }

            resultBuilder.append(line).append(System.lineSeparator());
        }
    }

    static void replaceNullChecks() {
        System.out.print("Replacing null checks...    ");
        boolean inBlock = false;
        String blockEnd = null;
        int indentationPos;
        int variablePos;
        String indentation = null;
        String variableName = null;
        List<String> blockContent = new ArrayList<>();

        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                if (line.equals(blockEnd)) {
                    blockContent = replaceNullChecksRecursion(blockContent);

                    //if block ends with a return/goto statement, cancel collapsing to preserve logic
                    if (blockContent.getLast().contains(" return") || blockContent.getLast().contains(" goto")) {
                        for (String blockLine : blockContent) {
                            resultBuilder.append(blockLine).append(System.lineSeparator());
                        }
                        resultBuilder.append(indentation).append("}").append(System.lineSeparator());
                    }
                    else {
                        //replace block header with a null check marker
                        resultBuilder.append(System.lineSeparator());
                        resultBuilder.append(indentation).append("//").append(variableName).append(" null check");
                        resultBuilder.append(System.lineSeparator());
                        resultBuilder.append(System.lineSeparator());

                        for (int i = 1; i < blockContent.size(); i++) {
                            String blockLine = blockContent.get(i);
                            if (!blockLine.isEmpty()) {
                                if (blockContent.get(i).charAt(0) == ' ') resultBuilder.append(blockContent.get(i).substring(TAB_LENGTH));
                                else resultBuilder.append(blockContent.get(i));
                            }
                            resultBuilder.append(System.lineSeparator());
                        }
                    }
                    blockContent.clear();
                    inBlock = false;
                }
                else blockContent.add(line);
            }
            else {
                if (line.isEmpty()) {
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //checking if current line is a header of a null check if block
                if (line.charAt(line.length() - 1) == '{') {
                    int pos = 0;
                    //skip indentation
                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                    indentationPos = pos;

                    //check 'if (' segment
                    if (line.charAt(pos++) != 'i' || line.charAt(pos++) != 'f' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '(') {
                        resultBuilder.append(line).append(System.lineSeparator());
                        continue;
                    }

                    //getting variable name
                    while (line.charAt(pos) != ' ') pos++;
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (line.charAt(pos++) != '!' || line.charAt(pos++) != '=' || line.charAt(pos++) != ' ' || line.charAt(pos++) != 'N'
                        || line.charAt(pos++) != 'U' || line.charAt(pos++) != 'L' || line.charAt(pos++) != 'L' || line.charAt(pos++) != ')'
                        || line.charAt(pos++) != ' ' || line.charAt(pos) != '{') {
                        resultBuilder.append(line).append(System.lineSeparator());
                        continue;
                    }
                    //start editing the if block
                    inBlock = true;
                    indentation = line.substring(0, indentationPos);
                    variableName = line.substring(indentationPos + 4, variablePos);
                    blockEnd = indentation + "}";

                    blockContent.add(line);
                }
                else {
                    resultBuilder.append(line).append(System.lineSeparator());
                }
            }
        }
    }

    //helper recursive method for collapsing if blocks within other if blocks
    private static List<String> replaceNullChecksRecursion(List<String> input) {
        List<String> lines = new ArrayList<>(input.size());

        boolean inBlock = false;
        String blockEnd = null;
        int indentationPos;
        int variablePos;
        String indentation = null;
        String variableName = null;
        List<String> blockContent = new ArrayList<>();

        lines.add(input.getFirst());
        for (int k = 1; k != input.size(); k++) {
            String line = input.get(k);
            if (inBlock) {
                if (line.equals(blockEnd)) {
                    blockContent = replaceNullChecksRecursion(blockContent);

                    //if block ends with a return/goto statement, cancel collapsing to preserve logic
                    if (blockContent.getLast().contains(" return") || blockContent.getLast().contains(" goto ")) {
                        lines.addAll(blockContent);
                        lines.add(indentation + "}");
                    }
                    else {
                        //replace block header with a null check marker
                        lines.add("");
                        lines.add(indentation + "//" + variableName + " null check");
                        lines.add("");

                        for (int i = 1; i < blockContent.size(); i++) {
                            String blockLine = blockContent.get(i);
                            if (!blockLine.isEmpty()) {
                                if (blockContent.get(i).charAt(0) == ' ') lines.add(blockContent.get(i).substring(TAB_LENGTH));
                                else lines.add(blockContent.get(i));
                            }
                            else lines.add("");
                        }
                    }
                    blockContent.clear();
                    inBlock = false;
                }
                else blockContent.add(line);
            }
            else {
                if (line.isEmpty()) {
                    lines.add(line);
                    continue;
                }
                //checking if current line is a header of a null check if block
                if (line.charAt(line.length() - 1) == '{') {
                    int pos = 0;
                    //skip indentation
                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                    indentationPos = pos;

                    //check 'if (' segment
                    if (line.charAt(pos++) != 'i' || line.charAt(pos++) != 'f' || line.charAt(pos++) != ' ' || line.charAt(pos++) != '(') {
                        lines.add(line);
                        continue;
                    }

                    //getting variable name
                    while (line.charAt(pos) != ' ') pos++;
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (line.charAt(pos++) != '!' || line.charAt(pos++) != '=' || line.charAt(pos++) != ' ' || line.charAt(pos++) != 'N'
                        || line.charAt(pos++) != 'U' || line.charAt(pos++) != 'L' || line.charAt(pos++) != 'L' || line.charAt(pos++) != ')'
                        || line.charAt(pos++) != ' ' || line.charAt(pos) != '{') {
                        lines.add(line);
                        continue;
                    }
                    //start editing the if block
                    inBlock = true;
                    indentation = line.substring(0, indentationPos);
                    variableName = line.substring(indentationPos + 4, variablePos);
                    blockEnd = indentation + "}";

                    blockContent.add(line);
                }
                else lines.add(line);
            }
        }
        return lines;
    }

    static void removeArrayBoundChecks() {
        System.out.print("Removing array bound checks...    ");
        boolean inBlock = false;
        Stack<String> blockEnds = new Stack<>();
        Matcher headerMatcher = Pattern.compile("^( *)if \\((?:\\d+ < [^-.]+(?:->|.)max_length|[^-.]+(?:->|.)max_length != 0)\\) \\{$").matcher("");

        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                if (line.isEmpty()) {
                    resultBuilder.append(System.lineSeparator());
                    continue;
                }

                //nested block header
                if (line.charAt(line.length() - 1) == '{' && line.contains("max_length")) {
                    headerMatcher.reset(line);
                    if (headerMatcher.find()) {
                        blockEnds.push(headerMatcher.group(1) + '}');
                    }
                }
                //current block end
                else if (line.equals(blockEnds.peek())) {
                    blockEnds.pop();
                    if (blockEnds.isEmpty()) inBlock = false;
                }
                //normal line
                else {
                    if (line.charAt(0) == ' ') resultBuilder.append(line.substring(TAB_LENGTH * blockEnds.size()));
                    else resultBuilder.append(line);
                    resultBuilder.append(System.lineSeparator());
                }
            }
            else {
                if (line.isEmpty()) {
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //check header
                if (line.charAt(line.length() - 1) == '{' && line.contains("max_length")) {
                    headerMatcher.reset(line);
                    if (headerMatcher.find()) {
                        inBlock = true;
                        blockEnds.push(headerMatcher.group(1) + '}');
                    }
                }
                else {
                    resultBuilder.append(line).append(System.lineSeparator());
                }
            }
        }
    }

    static void simplifyArrayAccess() {
        System.out.print("Simplifying array access...    ");
        String initializationFunction = JOptionPane.showInputDialog(null,
                """
                        Enter the name of an array initialization function.
                        Array initialization is usually structured like this:
                        
                        variableName = FUN_********(<TypeInfo>,<size>);
                        
                        Array operations can be found in code by searching for
                        "m_Items[0]"
                        """);
        if (initializationFunction.length() < 13 || !initializationFunction.substring(0, 13).matches("FUN_[0-9a-z]{9}")) {
            JOptionPane.showMessageDialog(null, "Invalid function name, skipping current procedure.", "Warning", JOptionPane.WARNING_MESSAGE);
            skipArrayReset = true;
            return;
        }

        Matcher matcher = Pattern.compile("^( *[^ ]+ = )" + initializationFunction + "\\((.+)___TypeInfo,(.*)\\);$").matcher("");
        for (String line : lines) {
            ProgressTracker.progress();

            matcher.reset(line);
            if (matcher.find()) {
                resultBuilder.append(matcher.group(1)).append("new ").append(matcher.group(2)).append('[').append(matcher.group(3)).append("];")
                        .append(System.lineSeparator());
                continue;
            }
            resultBuilder.append(line.replace(".m_Items[", "[").replace("_array", "[]")).append(System.lineSeparator());
        }
    }

    static void formatGenericTypes3() {
        System.out.print("Restoring generic type formatting...    ");
        boolean inBlock = false;
        boolean checkLabel = false;
        int pos;
        int indentationPos;

        String label = null;
        List<String> methodLines = new ArrayList<>();
        Map<String, String> exitLabels = new HashMap<>();

        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                if (line.isEmpty()) {
                    methodLines.add("");
                    continue;
                }

                //check if label points to a return statement
                if (checkLabel) {
                    pos = 0;
                    while (pos != line.length() && line.charAt(pos) == ' ') pos++;
                    indentationPos = pos;

                    if (line.charAt(pos++) == 'r' && line.charAt(pos++) == 'e' && line.charAt(pos++) == 't' && line.charAt(pos++) == 'u'
                            && line.charAt(pos++) == 'r' && line.charAt(pos) == 'n') {
                        exitLabels.put(label, line.substring(indentationPos));
                        methodLines.removeLast();
                    }
                    checkLabel = false;
                    methodLines.add(line);
                    continue;
                }
                //method end
                if (line.charAt(0) == '}') {
                    inBlock = false;
                    //remove unnecessary void return statements
                    for (int i = methodLines.size() - 1; i >= 0; i--) {
                        String methodLine = methodLines.get(i);
                        if (methodLine.isEmpty() || methodLine.charAt(0) != ' ') continue;
                        pos = 0;
                        while (pos != methodLine.length() && methodLine.charAt(pos) == ' ') pos++;
                        if (pos == methodLine.length() || methodLine.charAt(pos) == '}') continue;
                        if (methodLine.charAt(pos++) == 'r' && methodLine.charAt(pos++) == 'e' && methodLine.charAt(pos++) == 't' && methodLine.charAt(pos++) == 'u'
                                && methodLine.charAt(pos++) == 'r' && methodLine.charAt(pos++) == 'n' && methodLine.charAt(pos) == ';') {
                            methodLines.remove(i);
                        }
                        else break;
                    }
                    //goto statement replacement
                    for (String methodLine : methodLines) {
                        if (methodLine.isEmpty()) {
                            resultBuilder.append(System.lineSeparator());
                            continue;
                        }
                        if (methodLine.charAt(0) != ' ') {
                            resultBuilder.append(methodLine).append(System.lineSeparator());
                            continue;
                        }
                        pos = methodLine.indexOf("goto");
                        if (pos != -1 && methodLine.length() >= pos + 17 && methodLine.charAt(pos + 5) == 'L') {
                            label = methodLine.substring(pos + 5, pos + 18);
                            if (exitLabels.containsKey(label)) {
                                resultBuilder.append(methodLine, 0, pos).append(exitLabels.get(label)).append(System.lineSeparator());
                                continue;
                            }
                        }
                        resultBuilder.append(methodLine).append(System.lineSeparator());
                    }
                    methodLines.clear();
                    resultBuilder.append(line).append(System.lineSeparator());
                    continue;
                }
                //label
                if (line.charAt(0) == 'L' && line.charAt(1) == 'A' && line.charAt(2) == 'B' && line.charAt(3) == '_') {
                    checkLabel = true;
                    label = line.substring(0, 13);
                }
                methodLines.add(line);
            }
            else {
                if (line.isEmpty()) {
                    resultBuilder.append(System.lineSeparator());
                    continue;
                }
                //check if current line is a function header
                if ((line.charAt(0) != ' ' || line.charAt(0) != '/' || line.charAt(0) != '}'
                        || (line.charAt(0) != 'L' && line.charAt(1) != 'A' && line.charAt(2) != 'B' && line.charAt(3) != '_'))
                        && line.charAt(line.length() - 1) == '{') {
                    inBlock = true;
                }
                resultBuilder.append(line).append(System.lineSeparator());
            }
        }
    }

    //todo multiargument generic types
    static void formatGenericTypes2() {
        System.out.print("Formatting generic types (step 1)...    ");

        Map<String, Integer> types = new HashMap<>();
        Map<String, Integer> subtypes = new HashMap<>();

        Matcher typeMatcher = Pattern.compile("Method_([^< ]*?)(?<=[^_])<((?:([a-zA-Z_]+<[a-zA-Z_]*>),?|[a-zA-Z_]+,?)*)>").matcher("");
        int pos;
        int argCount;
        Integer i;
        String args;
        String name;

        //FINDING GENERIC TYPES
        for (String line : lines) {
            ProgressTracker.progress();

            if (line.indexOf('<') != -1) {
                typeMatcher.reset(line);
                if (typeMatcher.find()) {
                    //parsing main type
                    args = typeMatcher.group(2);
                    argCount = 1;
                    pos = 0;
                    while (pos != args.length()) {
                        if (args.charAt(pos) == ',') argCount++;
                        else if (args.charAt(pos) == '<') {
                            pos = args.indexOf('>', pos);
                        }
                        pos++;
                    }
                    i = types.get(typeMatcher.group(1));

                    if (i == null) types.put(typeMatcher.group(1), argCount);
                    else if (argCount > i) types.put(typeMatcher.group(1), argCount);

                    //parsing sub-type
                    args = typeMatcher.group(3);
                    if (args == null || args.isEmpty()) continue;

                    argCount = 1;
                    pos = args.indexOf('<');
                    name = args.substring(args.charAt(0) == '_' ? 1 : 0, pos);
                    pos++;

                    while (pos != args.length()) {
                        if (args.charAt(pos) == ',') argCount++;
                        else if (args.charAt(pos) == '<') {
                            pos = args.indexOf('>', pos);
                        }
                        pos++;
                    }
                    i = subtypes.get(name);

                    if (i == null) subtypes.put(name, argCount);
                    else if (argCount > i) subtypes.put(name, argCount);
                }
            }
        }
        for (Map.Entry entry : types.entrySet()) System.out.println(entry.getKey() + " - " + entry.getValue());
        System.out.println("----------------------------------------------------------------------------------");
        for (Map.Entry entry : subtypes.entrySet()) System.out.println(entry.getKey() + " - " + entry.getValue());
    }

    //todo multiargument generic types
    static void formatGenericTypes() {
        System.out.print("Formatting generic types (step 1)...    ");

        Matcher garbageMatcher = Pattern.compile("___c(?:__\\d*)?(?:DisplayClass\\d*_\\d)?$|_d__\\d*$").matcher("");

        Map<String, Integer> types = new HashMap<>();
        int index;
        int pos;

        //FINDING GENERIC TYPES
        {
            int innerPos;
            String type;

            for (String line : lines) {
                ProgressTracker.progress();

                index = 0;
                loop:
                while (index != -1) {
                    index = line.indexOf("_T_", index);
                    if (index != -1) {
                        pos = index;
                        while (pos != -1 && line.charAt(pos) != ' ') {
                            char c = line.charAt(pos);
                            if (c == ',' || c == '(' || c == '.' || c == '>' || c == '*') {
                                index++;
                                continue loop;
                            }
                            pos--;
                        }
                        type = line.substring(pos + 1, index);

                        innerPos = 0;
                        while (innerPos != type.length() && (type.charAt(innerPos) < 65 || type.charAt(innerPos) > 90)) innerPos++;
                        if (innerPos != type.length()) type = type.substring(innerPos);

                        garbageMatcher.reset(type);
                        if (garbageMatcher.find()) type = garbageMatcher.replaceAll("");

                        if (!type.isEmpty() && !type.startsWith("STR")) {
                            types.compute(type, (s, i) -> i == null ? 1 : i + 1);
                        }

                        index += 3;
                    }
                }
            }
            for (Map.Entry s : types.entrySet().stream().sorted(
                        (o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                .toArray(Map.Entry[]::new)) System.out.println(s.getValue() + " - " + s.getKey());
        }
        ProgressTracker.reset(types.size());

        System.out.print("Formatting generic types (step 2)...    ");

        //EXTRACTING SUBTYPES FROM FOUND TYPES
        //System_Collections_Generic_Stack, System_Collections_Generic_Stack_Enumerator -> System_Collections_Generic_Stack, Enumerator
        Map<String, Integer> typesFinal = new HashMap<>((int) (types.size() * 1.1));
        int minLength = Integer.MAX_VALUE;
        {
            String type;
            String subType;

            loop:
            for (Map.Entry<String, Integer> entry : types.entrySet()) {
                ProgressTracker.progress();
                type = entry.getKey();
                index = type.lastIndexOf('_');

                if (index == -1) {
                    typesFinal.merge(type, entry.getValue(), Integer::sum);
                    minLength = Math.min(type.length(), minLength);
                    continue;
                }

                subType = type.substring(index + 1);
                type = type.substring(0, index);
                for (Map.Entry<String, Integer> entry2 : types.entrySet()) {
                    if (entry2.getKey().equals(type)) {
                        typesFinal.merge(type, entry.getValue(), Integer::sum);
                        typesFinal.merge(subType,  entry.getValue(), Integer::sum);
                        minLength = Math.min(type.length(), minLength);
                        minLength = Math.min(subType.length(), minLength);
                        continue loop;
                    }
                }
                typesFinal.merge(entry.getKey(), entry.getValue(), Integer::sum);
                minLength = Math.min(entry.getKey().length(), minLength);
            }
        }
        ProgressTracker.reset();

        minLength += TAB_LENGTH + 1;
        //sorted by frequency to match faster
        String[] typesSorted = typesFinal.entrySet().stream().sorted(
                (o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
        for (Map.Entry s : typesFinal.entrySet().stream().sorted(
                        (o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                .toArray(Map.Entry[]::new)) System.out.println(s.getValue() + " - " + s.getKey());

        System.out.print("Formatting generic types (step 3)...    ");

        //FINDING TYPE USAGES AND ADDING ANGLE BRACKETS
        int bracketPos;

        for (String line : lines) {
            ProgressTracker.progress();

            //skip short lines
            if (line.length() < minLength) {
                if (!line.isEmpty()) resultBuilder.append(line);
                resultBuilder.append(System.lineSeparator());
                continue;
            }
            //fast track matching check
            if (line.indexOf('_') == -1) {
                resultBuilder.append(line).append(System.lineSeparator());
                continue;
            }

            pos = 0;
            while (pos != line.length() && line.charAt(pos) != ' ') pos++;
            loop:
            for (String type : typesSorted) {
                index = pos;
                while (true) {
                    index = line.indexOf(type, index);
                    if (index == -1) break;

                    index += type.length();
                    if (line.charAt(index) != '_') continue;

                    char[] lineChars = line.toCharArray();
                    bracketPos = index;
                    index++;

                    while (lineChars[index] != '_') {
                        if (lineChars[index] == ' ') break;
                        index++;
                        if (index == lineChars.length) break loop;
                    }

                    lineChars[bracketPos] = '<';
                    lineChars[index] = '>';
                    line = String.copyValueOf(lineChars);
                }
            }
            resultBuilder.append(line).append(System.lineSeparator());
        }
    }

    static void replaceUnderscoresForMethods() {
        System.out.print("Replacing underscores for methods...    ");

        Matcher matcher = Pattern.compile("(?<=[a-zA-Z0-9>])__(?=[^_0-9][a-zA-Z0-9_<>]*\\()").matcher("");

        for (String line : lines) {
            ProgressTracker.progress();

            matcher.reset(line);
            line = matcher.replaceAll(".");

            resultBuilder.append(line).append(System.lineSeparator());
        }
    }

    static class ProgressTracker {

        static int counter;
        static int split;
        static int current;

        static void reset() {
            split = lines.length / 100;
            current = 0;
            counter = 0;
        }

        static void reset(int steps) {
            split = steps / 100;
            current = 0;
            counter = 0;
        }

        static void progress() {
            if (++current == split) {
                current = 0;
                counter++;
                if (counter < 10) System.out.print("\b\b\b " + counter + "%");
                else if (counter == 100) System.out.println("\b\b\b\b " + counter + "%");
                else if (counter < 100) System.out.print("\b\b\b\b " + counter + "%");
            }
        }

        static void end() {
            counter = 100;
            System.out.println("\b\b\b\b " + counter + "%");
        }
    }
}
