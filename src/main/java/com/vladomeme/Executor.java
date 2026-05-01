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
//todo procedure order dependence warning
//todo low memory mode with disc buffer
public class Executor {

    static String[] lines;
    static StringBuilder resultBuilder;
    static boolean skipArrayReset = false;

    static int TAB_LENGTH;
    static String interruptionFunction;
    static String arrayInitFunction;
    static float methodInfoThreshold;

    static void execute(List<ExecutionOption> options, Path path, boolean saveToCopy) {
        prepareData(path);

        System.out.println("Preparing for execution...");

        getIndentation();
        if (TAB_LENGTH <= 0) return;

        for (ExecutionOption option : options) {
            if (option.enabled && option.preparation != null) {
                option.preparation.run();
                ProgressTracker.reset();
            }
        }

        for (ExecutionOption option : options) {
            if (option.enabled && option.execution != null) {
                option.execution.run();
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
        String tabLengthString = JOptionPane.showInputDialog(null, "Enter indent (tab) length.\n\nLeave empty for auto-detection.");

        if (tabLengthString.isEmpty()) {
            for (String line : lines) {
                if (!line.isEmpty() && line.charAt(0) == ' ' && !line.contains("\\")) {
                    TAB_LENGTH = skipWhile(line, 0, ' ');
                    System.out.println("Using tab length: " + TAB_LENGTH);
                    break;
                }
            }
        }
        else {
            try {
                TAB_LENGTH = Integer.parseInt(tabLengthString);
            }
            catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Valid indentation length is required to proceed.", "Error", JOptionPane.ERROR_MESSAGE);
                TAB_LENGTH = -1;
            }
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
        System.out.print("Removing decompilator comments...    ");

        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            if (line.contains("// DISPLAY WARNING: Type casts") || line.contains("// WARNING: Subroutine does not return")) continue;
            appendWithNewLine(line);
        }
    }

    static void removeNullPointerTypeCasts() {
        System.out.print("Removing null pointer type casts...    ");

        Matcher matcher = Pattern.compile("(\\([^(]*\\)0x?0?)").matcher("");
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            matcher.reset(line);
            appendWithNewLine(matcher.replaceAll("NULL"));
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
                        appendWithNewLine(header);
                        inBlock = false;

                        //the entire "out-of-block" code segment copy-pasted for very rare cases :(
                        if (line.isEmpty()) {
                            appendEmpty();
                            continue;
                        }
                        //method end
                        if (line.charAt(0) == '}') {
                            currentVariables.clear();
                            appendWithNewLine(line);
                            continue;
                        }
                        //checking if current line is a header of a null check if block
                        if (line.charAt(line.length() - 1) == '{') {
                            //skip indentation
                            pos = skipWhile(line, 0, ' ');
                            indentationPos = pos;

                            if (!textAfterEquals(line, pos, "if (")) {
                                appendWithNewLine(line);
                                continue;
                            }
                            pos += 4;

                            //getting variable name
                            pos = skipUntil(line, pos, ' ', ')');
                            variablePos = pos;

                            //check the rest of the header
                            pos++;
                            if (!textAfterEquals(line, pos, "== '\\0') {")) {

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
                                appendWithNewLine(line);
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
                            if (textBeforeEquals(line, pos, "== '\\0';")) {
                                pos -= 19;
                                if (textBeforeEquals(line, pos, " = DAT")) {
                                    sanityPos = pos - 5;

                                    //skip indentation
                                    pos = skipWhile(line, 0, ' ');
                                    indentationPos = pos;

                                    //getting variable name
                                    pos = skipUntil(line, pos, ' ');
                                    if (pos == sanityPos)
                                        currentVariables.add(line.substring(indentationPos, pos));
                                    continue;
                                }
                            }
                        }
                        appendWithNewLine(line);
                    }
                    else header = null;
                }
                else if (line.equals(blockEnd)) inBlock = false;
            }
            else {
                if (line.isEmpty()) {
                    appendEmpty();
                    continue;
                }
                //method end
                if (line.charAt(0) == '}') {
                    currentVariables.clear();
                    appendWithNewLine(line);
                    continue;
                }
                //checking if current line is a header of a null check if block
                if (line.charAt(line.length() - 1) == '{') {
                    //skip indentation
                    pos = skipWhile(line, 0, ' ');
                    indentationPos = pos;

                    //check 'if (' segment
                    if (!textAfterEquals(line, pos, "if (")) {
                        appendWithNewLine(line);
                        continue;
                    }
                    pos += 4;

                    //getting variable name
                    pos = skipUntil(line, pos, ' ', ')');
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (!textAfterEquals(line, pos, "== '\\0') {")) {

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
                        appendWithNewLine(line);
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
                    if (pos > 25 && textBeforeEquals(line, pos, "== '\\0';")) {
                        pos -= 19;
                        if (textBeforeEquals(line, pos, " = DAT")) {
                            sanityPos = pos - 5;

                            //skip indentation
                            pos = skipWhile(line, 0, ' ');
                            indentationPos = pos;

                            //getting variable name
                            pos = skipUntil(line, pos, ' ');
                            if (pos == sanityPos)
                                currentVariables.add(line.substring(indentationPos, pos));
                            continue;
                        }
                    }
                }
                appendWithNewLine(line);
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
                appendWithNewLine(lines[i - 1]);
                i++;
                continue;
            }
            //realistically should never fall out of bounds anywhere
            if (lines[i].contains("il2cpp_runtime_class_init")) {
                previous = lines[i - 1];
                next = lines[i + 1];
                if (!previous.isEmpty() && previous.charAt(previous.length() - 1) == '{'
                        && !next.isEmpty() && next.charAt(next.length() - 1) == '}') {
                    i += 3;
                }
            }
            appendWithNewLine(lines[i - 1]);
            i++;
        }
        appendWithNewLine(lines[i - 1]);
        ProgressTracker.end();
    }

    static void simplifyProcedureInterruptions$prepare() {
        interruptionFunction = JOptionPane.showInputDialog(null,
                """
                        Enter the name of a targeted procedure interruption function.
                        It appears at the bottom of void functions (don't look at "FUN_"
                        or Unity/other library functions) after "Subroutine does not return"
                        warnings, formatted as FUN_*********.
                        
                        Leave empty for auto-detection.
                        """);
        if (interruptionFunction.isEmpty()) { //auto-detect
            System.out.print("Finding procedure interruption function name...    ");

            Map<String, Integer> map = new HashMap<>();
            String previous = "";
            int pos;

            for (String line : lines) {
                ProgressTracker.progress();

                if (!line.isEmpty()) {
                    if (line.charAt(0) == '}') {
                        pos = skipWhile(previous, 0, ' ');
                        if (textAfterEquals(previous, pos, "FUN_")) {
                            map.merge(previous.substring(pos, pos + 13), 1, Integer::sum);
                        }
                    }
                }
                previous = line;
            }
            interruptionFunction = map.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse("");
            if (interruptionFunction.isEmpty()) System.out.println("Failed to auto-detect function name");
            else System.out.println("Selected function name: " + interruptionFunction);

        }
        else if (interruptionFunction.length() < 13 || !interruptionFunction.substring(0, 13).matches("FUN_[0-9a-z]{9}")) {
            JOptionPane.showMessageDialog(null, "Invalid function name, procedure will be skipped.", "Warning", JOptionPane.WARNING_MESSAGE);
            interruptionFunction = "";
        }
    }

    static void simplifyProcedureInterruptions() {
        System.out.print("Simplifying procedure interruptions (step 1)...    ");

        if (interruptionFunction.isEmpty()) {
            skipArrayReset = true;
            return;
        }

        // REPLACING INTERRUPTION FUNCTION WITH 'return' //
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            if (line.contains(interruptionFunction)) {
                appendWithNewLine(line.replaceAll(interruptionFunction + "\\(.*?\\);$", "return;"));
            }
            else appendWithNewLine(line);
        }
        resetLineArray();
        ProgressTracker.reset();

        System.out.print("Simplifying procedure interruptions (step 2)...    ");

        // INTERRUPTION LABEL PROCESSING //
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
                    pos = skipWhile(line, 0, ' ');
                    indentationPos = pos;

                    if (textAfterEquals(line, pos, "return")) {
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

                        pos = skipWhile(methodLine, 0, ' ');
                        if (pos == methodLine.length() || methodLine.charAt(pos) == '}') continue;

                        if (textAfterEquals(methodLine, pos, "return;")) {
                            methodLines.remove(i);
                        }
                        else break;
                    }
                    //goto statement replacement
                    for (String methodLine : methodLines) {
                        if (methodLine.isEmpty()) {
                            appendEmpty();
                            continue;
                        }
                        if (methodLine.charAt(0) != ' ') {
                            appendWithNewLine(methodLine);
                            continue;
                        }
                        pos = methodLine.indexOf("goto");
                        if (pos != -1 && methodLine.length() >= pos + 17 && methodLine.charAt(pos + 5) == 'L') {
                            label = methodLine.substring(pos + 5, pos + 18);
                            if (exitLabels.containsKey(label)) {
                                append(methodLine.substring(0, pos));
                                appendWithNewLine(exitLabels.get(label));
                                continue;
                            }
                        }
                        appendWithNewLine(methodLine);
                    }
                    methodLines.clear();
                    appendWithNewLine(line);
                    continue;
                }
                //label
                if (line.startsWith("LAB_")) {
                    checkLabel = true;
                    label = line.substring(0, 13);
                }
                methodLines.add(line);
            }
            else {
                if (line.isEmpty()) {
                    appendEmpty();
                    continue;
                }
                //check if current line is a function header
                if (isFunctionHeader(line)) {
                    inBlock = true;
                }
                appendWithNewLine(line);
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

            appendWithNewLine(line);
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
        int pos;
        List<String> blockContent = new ArrayList<>();

        for (String line : lines) {
            ProgressTracker.progress();
            if (inBlock) {
                if (line.equals(blockEnd)) {
                    blockContent = replaceNullChecksRecursion(blockContent);

                    //if block ends with a return/goto statement, cancel collapsing to preserve logic
                    if (blockContent.getLast().contains(" return") || blockContent.getLast().contains(" goto")) {
                        for (String blockLine : blockContent) {
                            appendWithNewLine(blockLine);
                        }
                        append(indentation);
                        appendWithNewLine("}");
                    }
                    else {
                        //replace block header with a null check marker
                        appendEmpty();
                        append(indentation);
                        append("//");
                        append(variableName);
                        append(" null check");
                        appendEmpty();
                        appendEmpty();

                        for (int i = 1; i < blockContent.size(); i++) {
                            String blockLine = blockContent.get(i);
                            if (blockLine.isEmpty()) appendEmpty();
                            else {
                                if (blockContent.get(i).charAt(0) == ' ') appendWithNewLine(blockContent.get(i).substring(TAB_LENGTH));
                                else appendWithNewLine(blockContent.get(i));
                            }
                        }
                    }
                    blockContent.clear();
                    inBlock = false;
                }
                else blockContent.add(line);
            }
            else {
                if (line.isEmpty()) {
                    appendEmpty();
                    continue;
                }
                //checking if current line is a header of a null check if block
                if (line.charAt(line.length() - 1) == '{') {
                    //skip indentation
                    pos = skipWhile(line, 0, ' ');
                    indentationPos = pos;

                    //check 'if (' segment
                    if (!textAfterEquals(line, pos, "if (")) {
                        appendWithNewLine(line);
                        continue;
                    }
                    pos += 4;

                    //getting variable name
                    pos = skipUntil(line, pos, ' ');
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (!textAfterEquals(line, pos, "!= NULL) {")) {
                        appendWithNewLine(line);
                        continue;
                    }
                    //start editing the if block
                    inBlock = true;
                    indentation = line.substring(0, indentationPos);
                    variableName = line.substring(indentationPos + 4, variablePos);
                    blockEnd = indentation + "}";

                    blockContent.add(line);
                }
                else appendWithNewLine(line);
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
        int pos;
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
                    //skip indentation
                    pos = skipWhile(line, 0, ' ');
                    indentationPos = pos;

                    //check 'if (' segment
                    if (!textAfterEquals(line, pos, "if (")) {
                        lines.add(line);
                        continue;
                    }
                    pos += 4;

                    //getting variable name
                    pos = skipUntil(line, pos, ' ');
                    variablePos = pos;

                    //check the rest of the header
                    pos++;
                    if (!textAfterEquals(line, pos, "!= NULL) {")) {
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
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            if (inBlock) {
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
                    if (line.charAt(0) == ' ') appendWithNewLine(line.substring(TAB_LENGTH * blockEnds.size()));
                    else appendWithNewLine(line);
                }
            }
            else {
                //check header
                if (line.charAt(line.length() - 1) == '{' && line.contains("max_length")) {
                    headerMatcher.reset(line);
                    if (headerMatcher.find()) {
                        inBlock = true;
                        blockEnds.push(headerMatcher.group(1) + '}');
                    }
                }
                else appendWithNewLine(line);
            }
        }
    }

    static void simplifyArrayAccess$prepare() {
        arrayInitFunction = JOptionPane.showInputDialog(null,
                """
                        Enter the name of an array initialization function.
                        Array initialization is usually structured like this:
                        
                        variableName = FUN_********(<TypeInfo>,<size>);
                        
                        Array operations can be found in code by searching for
                        "m_Items[0]"
                        
                        Leave empty for auto-detection.
                        """);
        if (arrayInitFunction.isEmpty()) { //auto-detect
            System.out.print("Finding array initialization function name...    ");

            Map<String, Integer> map = new HashMap<>();
            Matcher matcher = Pattern.compile("= (FUN_.........)\\([^,]*TypeInfo,\\d+\\);$").matcher("");

            for (String line : lines) {
                ProgressTracker.progress();

                if (!line.isEmpty()) {
                    //a cheap (?) line skip to avoid using regex?
                    if (line.indexOf("FUN", TAB_LENGTH + 4) != -1 && line.indexOf("TypeInfo", TAB_LENGTH + 17) != -1) {
                        matcher.reset(line);
                        if (matcher.find()) {
                            map.merge(matcher.group(1), 1, Integer::sum);
                        }
                    }
                }
            }
            arrayInitFunction = map.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse("");
            if (arrayInitFunction.isEmpty()) System.out.println("Failed to auto-detect function name");
            else System.out.println("Selected function name: " + arrayInitFunction);
        }
        else if (arrayInitFunction.length() < 13 || !arrayInitFunction.substring(0, 13).matches("FUN_[0-9a-z]{9}")) {
            JOptionPane.showMessageDialog(null, "Invalid function name, procedure will be skipped.", "Warning", JOptionPane.WARNING_MESSAGE);
            arrayInitFunction = "";
        }
    }

    static void simplifyArrayAccess() {
        System.out.print("Simplifying array access...    ");

        if (arrayInitFunction.isEmpty()) {
            skipArrayReset = true;
            return;
        }

        Matcher matcher = Pattern.compile("^( *[^ ]+ = )" + arrayInitFunction + "\\((.+)___TypeInfo,?(.*)\\);$").matcher("");
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            matcher.reset(line);
            if (matcher.find()) {
                append(matcher.group(1));
                append("new ");
                append(matcher.group(2));
                append("[");
                append(matcher.group(3));
                appendWithNewLine("];");
                continue;
            }
            appendWithNewLine(line.replace(".m_Items[", "[").replace("_array", "[]"));
        }
    }

    //horrifying
    static void formatGenericTypes() {
        System.out.print("Formatting generic types (step 1)...    ");

        //ATTEMPTING TO FIND GENERIC TYPES AND DETERMINING THEIR TYPE PARAMETER COUNTS (1 or more)
        Map<String, Boolean> typesWithArgs = new HashMap<>();
        Set<String> subtypes = new HashSet<>();

        Matcher typeMatcher = Pattern.compile("Method_([^< ]*?)(?<=[^_])<((?:([a-zA-Z_]+<[a-zA-Z_]*>),?|[a-zA-Z_]+,?)*)>(?=_*[A-Za-z])").matcher("");
        int pos;
        int argCount;
        Boolean b;
        String args;
        String name;

        //finding generic types with preserved formatting. Good data with accurate arg counts, but not all types are found
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
                    b = typesWithArgs.get(typeMatcher.group(1));
                    if (b == null) typesWithArgs.put(typeMatcher.group(1), argCount == 1);
                    else {
                        if (argCount != 1 && b) typesWithArgs.put(typeMatcher.group(1), Boolean.FALSE);
                    }

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
                    subtypes.add(name);
                }
            }
        }
        ProgressTracker.reset();

        System.out.print("Formatting generic types (step 2)...    ");

        Matcher garbageMatcher = Pattern.compile("___c(?:__\\d*)?(?:DisplayClass\\d*_\\d)?$|_d__\\d*$").matcher("");
        Set<String> badTypes = new HashSet<>();
        int index;

        //finding generic types through a '_T_' string. Could find additional types, but gives a lot of garbage and has to be extra processed
        int innerPos;
        String type;
        int searchIndex;

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

                    searchIndex = type.indexOf("_T_");
                    if (searchIndex != -1) type = type.substring(0, searchIndex);

                    searchIndex = type.indexOf("__");
                    if (searchIndex != -1) type = type.substring(0, searchIndex);

                    if (!type.isEmpty() && !type.startsWith("STR")) {
                        badTypes.add(type);
                    }

                    index += 3;
                }
            }
        }
        ProgressTracker.reset();

        System.out.print("Formatting generic types (step 3)...    ");

        //clear up bad type search output
        Iterator<String> iterator = badTypes.iterator();
        while (iterator.hasNext()) {
            type = iterator.next();
            //remove confirmed good types from bad types
            if (typesWithArgs.containsKey(type)) iterator.remove();
                //remove garbage coming from multi-arg generics
            else if (type.charAt(type.length() - 1) == '_') iterator.remove();
        }
        ProgressTracker.set(10);

        //sorting the good types by type length in descending order to match types like List.Enumerator before List
        List<Map.Entry<String, Boolean>> goodTypesSorted = new ArrayList<>(typesWithArgs.entrySet());
        goodTypesSorted.sort((o1, o2) -> Integer.compare(o2.getKey().length(), o1.getKey().length()));

        ProgressTracker.set(20);

        //comparing bad types with known good types to remove false positives and extract additional generic subtypes
        iterator = badTypes.iterator();
        while (iterator.hasNext()) {
            String badType = iterator.next();
            for (Map.Entry<String, Boolean> goodType : goodTypesSorted) {
                if (badType.startsWith(goodType.getKey() + "_")) {
                    //if a bad type matches a known good type with one arg, then it's argument type is a generic too
                    if (goodType.getValue()) {
                        subtypes.add(badType.substring(goodType.getKey().length() + 1));
                    }
                    //if a matched good type is multi-arg, then there's no information to extract really
                    iterator.remove();
                    break;
                }
            }
        }

        ProgressTracker.set(30);

        //remove duplicates within subtypes
        iterator = subtypes.iterator();
        while (iterator.hasNext()) {
            type = iterator.next();
            index = type.indexOf('_');
            if (index != -1) {
                type = type.substring(index + 1);
                for (String subtype : subtypes) {
                    if (subtype.equals(type)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        ProgressTracker.set(40);

        //remove duplicate subtypes by comparison
        String subTypeWithPrefix;
        iterator = subtypes.iterator();
        loop:
        while (iterator.hasNext()) {
            String subType = iterator.next();
            if (typesWithArgs.containsKey(subType) || badTypes.contains(subType)) {
                iterator.remove();
                continue;
            }
            subTypeWithPrefix = "_" + subType;
            for (String t : typesWithArgs.keySet()) {
                if (t.endsWith(subTypeWithPrefix)) {
                    iterator.remove();
                    continue loop;
                }
            }
            for (String t : badTypes) {
                if (t.endsWith(subTypeWithPrefix)) {
                    iterator.remove();
                    continue loop;
                }
            }
        }

        ProgressTracker.set(50);

        //remove bad type false positives by comparing with subtyped good types
        List<String> goodTypeTokens = typesWithArgs.keySet().stream().map(s -> s.indexOf('_') == -1 ? '_' + s : s.substring(s.lastIndexOf('_'))).toList();
        iterator = badTypes.iterator();
        while (iterator.hasNext()) {
            type = iterator.next();
            for (String token : goodTypeTokens) {
                if (type.endsWith(token)) {
                    iterator.remove();
                    break;
                }
            }
        }
        //noinspection UnusedAssignment
        goodTypeTokens = null;

        ProgressTracker.set(60);

        //remove duplicates within bad types
        iterator = badTypes.iterator();
        while (iterator.hasNext()) {
            type = iterator.next();
            index = type.indexOf('_');
            if (index != -1) {
                type = type.substring(index + 1);
                for (String subtype : subtypes) {
                    if (subtype.equals(type)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        ProgressTracker.set(70);

        //finishing up bad type processing by adding them to good types with non-single param counts
        for (String badType : badTypes) {
            typesWithArgs.put(badType, Boolean.FALSE);
        }
        //noinspection UnusedAssignment
        badTypes = null;

        ProgressTracker.set(80);

        //tokenizing all known types to be used as subtypes
        for (Map.Entry<String, Boolean> entry : typesWithArgs.entrySet()) {
            type = entry.getKey();
            index = type.lastIndexOf('_');

            if (index != -1) subtypes.add(type.substring(index + 1));
            else subtypes.add(type);
        }

        ProgressTracker.end();
        ProgressTracker.reset();

        System.out.print("Formatting generic types (step 4)...    ");

        //FORMATTING TYPES
        boolean doReplace = false;
        boolean withIndent;
        StringBuilder builder = new StringBuilder(1000);
        char[] chars;
        String lastArg;
        int backtrackPos;
        int braceCount;
        int endPos;
        char[] indentArr = new char[TAB_LENGTH];
        Arrays.fill(indentArr, ' ');

        List<String> typesSorted = new ArrayList<>(typesWithArgs.keySet());
        List<String> subtypesSorted = new ArrayList<>(subtypes);
        typesSorted.sort((o1, o2) -> Integer.compare(o2.length(), o1.length()));
        subtypesSorted.sort((o1, o2) -> Integer.compare(o2.length(), o1.length()));

        lineLoop:
        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                doReplace = false;
                continue;
            }

            if (line.charAt(0) == '}') {
                appendWithNewLine(line);
                doReplace = false;
                continue;
            }

            if (doReplace || isFunctionHeader(line)) {
                withIndent = doReplace;
                doReplace = true;
                braceCount = 0;

                pos = line.indexOf(' ', withIndent ? TAB_LENGTH : 0);
                try {
                    if (pos != -1 && line.charAt(pos - 2) == '_' && line.charAt(pos - 3) == '_') { //check for generic

                        builder.setLength(0);
                        if (withIndent) builder.append(indentArr);

                        type = line.substring(withIndent ? TAB_LENGTH : 0, pos);

                        for (String t : typesSorted) {
                            if (type.startsWith(t)) { //type is known
                                chars = type.substring(t.length() + 1).toCharArray();
                                builder.append(t);
                                builder.append('<');
                                braceCount++;
                                innerPos = 0;

                                loop:
                                while (innerPos != chars.length) {
                                    if (chars[innerPos] == '_') {
                                        //check for type end
                                        endPos = innerPos;
                                        while (endPos != chars.length && chars[endPos] == '_') endPos++;
                                        if (endPos == chars.length - 1) { //type end
                                            while (innerPos != chars.length - 2) {
                                                innerPos++;
                                                if (braceCount > 0) {
                                                    builder.append('>');
                                                    braceCount--;
                                                }
                                                else builder.append('_');
                                            }
                                            builder.append('_').append(chars[innerPos + 1]);
                                            break;
                                        }
                                        if (chars[innerPos + 1] == '_') {
                                            innerPos++;
                                            builder.append(',');
                                        }
                                        else { //generic type parameter or non-generic subclass
                                            backtrackPos = builder.length() - 1;
                                            while (backtrackPos != 0 && builder.charAt(backtrackPos) != ',' && builder.charAt(backtrackPos) != '<') backtrackPos--;
                                            lastArg = builder.substring(backtrackPos + 1);
                                            for (String st : subtypesSorted) {
                                                if (lastArg.equals(st)) {
                                                    builder.append('<');
                                                    braceCount++;
                                                    innerPos++;
                                                    continue loop;
                                                }
                                            }
                                            builder.append('_');
                                        }
                                    }
                                    else builder.append(chars[innerPos]);
                                    innerPos++;
                                }
                                appendWithNewLine(builder + line.substring(pos));
                                continue lineLoop;
                            }
                        }
                        //type is not known
                        innerPos = type.length() - 2;
                        while (innerPos != 0 && type.charAt(innerPos) == '_') innerPos--;
                        while (innerPos != 0 && type.charAt(innerPos) != '_') innerPos--;
                        builder.append(type, 0, innerPos);
                        builder.append('<');
                        innerPos++;
                        while (innerPos != type.length() - 2 && type.charAt(innerPos) != '_') {
                            builder.append(type.charAt(innerPos));
                            innerPos++;
                        }
                        builder.append('>').append('_').append(type.charAt(type.length() - 1));
                        appendWithNewLine(builder + line.substring(pos));
                    }
                    else appendWithNewLine(line);
                }
                catch (Exception e) {;
                    throw new RuntimeException(e);
                }
            }
            else appendWithNewLine(line);
        }
    }

    static void removeMethodInfoArgument$prepare() {
        String input = JOptionPane.showInputDialog(null,
                """
                        Enter the MethodInfo argument removal threshold.
                        It represents the amount of method calls that can have a
                        value that is not explicitly NULL as an argument.
                        
                        Leave empty to use the default threshold (0.005).
                        """);
        if (input.isEmpty()) methodInfoThreshold = 0.005f;
        else {
            try {
                methodInfoThreshold = Float.parseFloat(input);
            }
            catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Failed to parse threshold, procedure will be skipped.", "Warning", JOptionPane.WARNING_MESSAGE);
                methodInfoThreshold = -67;
            }
        }
    }

    //todo overloaded methods? use argument counts for relatively cheap distinction
    static void removeMethodInfoArgument() {
        System.out.print("Removing useless MethodInfo arguments (part 1)...    ");

        if (methodInfoThreshold == -67) {
            skipArrayReset = true;
            return;
        }

        // FINDING ALL METHODS WITH MethodInfo ARGUMENTS //
        int index;
        char c;
        Map<String, int[]> methods = new HashMap<>(1000);

        loop:
        for (String line : lines) {
            ProgressTracker.progress();

            if (line.isEmpty()) continue;

            if (isFunctionHeader(line)) {
                index = line.lastIndexOf("MethodInfo");
                if (index == -1) continue;

                while (index != line.length()) {
                    c = line.charAt(index);
                    if (c == ',') continue loop;
                    if (c == ')') {
                        methods.putIfAbsent(line.substring(skipWhile(line, line.indexOf(' '), ' ', '*'), line.indexOf('(')), new int[]{0, 0});
                        break;
                    }
                    index++;
                }
            }
        }
        ProgressTracker.reset();

        System.out.print("Removing useless MethodInfo arguments (part 2)...    ");

        int pos;
        int[] ints;
        int blockCount;
        String name;

        // REMOVING METHODS THAT HAVE NON-NULL MethodInfo ARGUMENTS USED FROM LIST //
        for (String line : lines) {
            ProgressTracker.progress();

            if (line.isEmpty() || line.charAt(0) != ' ') continue;

            index = line.indexOf('(');
            while (index != -1) {
                pos = index - 1;
                blockCount = 0;
                while (pos != -1) {
                    c = line.charAt(pos);
                    if (c == ' ' || c == '(' || c == ')' || c == '*' || c == '&' || c == '-' || (c == ',' && blockCount == 0)) {
                        ints = methods.get(line.substring(pos + 1, index));
                        if (ints != null) {
                            ints[0]++;
                            blockCount = 1;
                            pos = index + 1;
                            while (pos != line.length()) {
                                c = line.charAt(pos);
                                if (c == ')') {
                                    blockCount--;
                                    if (blockCount == 0 && !textBeforeEquals(line, pos - 1, "NULL")) {
                                        ints[1]++;
                                    }
                                }
                                if (c == '(') blockCount++;
                                pos++;
                            }
                        }
                        break;
                    }
                    if (c == '>') blockCount++;
                    if (c == '<') blockCount--;
                    pos--;
                }
                index = line.indexOf('(', index + 1);
            }
        }
        ProgressTracker.reset();

        //remove all methods that exceed the threshold
        methods.entrySet().removeIf(entry -> (float) entry.getValue()[1] / entry.getValue()[0] > methodInfoThreshold);

        System.out.print("Removing useless MethodInfo arguments (part 3)...    ");

        int backPos;

        // REMOVING ARGUMENTS FROM LEFTOVER METHODS & THEIR USES //
        for (String line : lines) {
            ProgressTracker.progress();

            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }

            //function header
            if (isFunctionHeader(line)) {
                if (!methods.containsKey(line.substring(skipWhile(line, line.indexOf(' '), ' ', '*'), line.indexOf('(')))) {
                    appendWithNewLine(line);
                    continue;
                }

                index = line.lastIndexOf("MethodInfo");
                if (index == -1) { //shouldn't happen but just in case
                    appendWithNewLine(line);
                    continue;
                }

                if (line.charAt(index - 1) == ',') index--;

                appendWithNewLine(line.substring(0, index) + ") {");
            }
            //other lines
            else {
                if (line.charAt(0) != ' ') {
                    appendWithNewLine(line);
                    continue;
                }

                index = line.indexOf('(');
                while (index != -1) {
                    pos = index - 1;
                    blockCount = 0;
                    while (pos != -1) {
                        c = line.charAt(pos);
                        if (c == ' ' || c == '(' || c == ')' || c == '*' || c == '&' || c == '-' || (c == ',' && blockCount == 0)) {
                            name = line.substring(pos + 1, index);
                            if (methods.containsKey(name)) {
                                blockCount = 1;
                                pos = index + 1;
                                while (pos != line.length()) {
                                    c = line.charAt(pos);
                                    if (c == ')') {
                                        blockCount--;
                                        if (blockCount == 0 && textBeforeEquals(line, pos - 1, "NULL")) {
                                            backPos = line.charAt(pos - 5) == ',' ? pos - 5 : pos - 4;
                                            line = line.substring(0, backPos) + line.substring(pos);
                                            break;
                                        }
                                    }
                                    if (c == '(') blockCount++;
                                    pos++;
                                }
                            }
                            break;
                        }
                        if (c == '>') blockCount++;
                        if (c == '<') blockCount--;
                        pos--;
                    }
                    index = line.indexOf('(', index + 1);
                }
                appendWithNewLine(line);
            }
        }
    }

    static void replaceUnderscoresForMethods() {
        System.out.print("Replacing underscores for methods...    ");

        Matcher matcher = Pattern.compile("(?<=[a-zA-Z0-9>])__(?=[^_0-9][a-zA-Z0-9_<>]*\\()").matcher("");

        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) {
                appendEmpty();
                continue;
            }
            matcher.reset(line);
            appendWithNewLine(matcher.replaceAll("."));
        }
    }

    private static boolean isFunctionHeader(String line) {
        char c = line.charAt(0);
        return line.length() > 3 && (c != ' ' && c != '/' && c != '}'
                && c != 'L' && line.charAt(1) != 'A' && line.charAt(2) != 'B' && line.charAt(3) != '_'
                && line.charAt(line.length() - 1) == '{' && line.charAt(line.length() - 3) == ')');
    }

    private static boolean textAfterEquals(String line, int pos, String text) {
        char[] chars = text.toCharArray();
        int charPos = 0;

        while (pos != line.length() && charPos != chars.length) {
            if (line.charAt(pos++) != chars[charPos++]) return false;
        }
        return charPos == chars.length;
    }

    private static boolean textBeforeEquals(String line, int pos, String text) {
        char[] chars = text.toCharArray();
        int charPos = chars.length - 1;

        while (pos != 0 && charPos != 0) {
            if (line.charAt(pos--) != chars[charPos--]) return false;
        }
        return charPos == 0;
    }

    private static int skipWhile(String line, int pos, char c) {
        while (pos != line.length() && line.charAt(pos) == c) pos++;
        return pos;
    }

    private static int skipWhile(String line, int pos, char... chars) {
        char current;
        loop:
        while (pos != line.length()) {
            current = line.charAt(pos);
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

    private static int skipUntil(String line, int pos, char c) {
        int index = line.indexOf(c, pos);
        return index != -1 ? index : line.length();
    }

    private static int skipUntil(String line, int pos, char... chars) {
        char current;
        while (pos != line.length()) {
            current = line.charAt(pos);
            for (char c : chars) {
                if (c == current) return pos;
            }
            pos++;
        }
        return pos;
    }

    private static void appendEmpty() {
        resultBuilder.append(System.lineSeparator());
    }

    private static void append(String s) {
        resultBuilder.append(s);
    }

    private static void appendWithNewLine(String s) {
        resultBuilder.append(s).append(System.lineSeparator());
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

        static void set(int progress) {
            counter = progress;
            if (counter < 10) System.out.print("\b\b\b " + counter + "%");
            else if (counter == 100) System.out.println("\b\b\b\b " + counter + "%");
            else if (counter < 100) System.out.print("\b\b\b\b " + counter + "%");
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
