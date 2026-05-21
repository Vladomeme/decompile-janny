package com.vladomeme;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.vladomeme.ParsingUtils.*;

public class RetypeAnalyser {

    static String[] lines;

    public static void generateRetypingInfo(Path path) {
        prepareData(path);

        List<MethodData> list = collectMethodData();
        lines = null;
        
        try {
            System.out.println("Writing...");

            path = Path.of(path + ".retype");

            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(path.toUri())));

//            for (MethodData method : list) {
//                writer.write(method.signature);
//                writer.newLine();
//
//                if (!method.localVariables.isEmpty()) {
//                    writer.write("    Local variables:");
//                    writer.newLine();
//                    for (Map.Entry<String, String> entry : method.localVariables.entrySet()) {
//                        writer.write("        " + entry.getValue() + " " + entry.getKey());
//                        writer.newLine();
//                    }
//                }
//
//                if (!method.methodLines.isEmpty()) {
//                    writer.write("    Code:");
//                    writer.newLine();
//                    for (String line : method.methodLines) {
//                        if (line.isEmpty()) continue;
//                        writer.write("        " + line);
//                        writer.newLine();
//                    }
//                    writer.newLine();
//                }
//            }
            writer.close();
            System.out.println("Finished!");
            JOptionPane.showMessageDialog(null, "Retype information saved to: " + path,
                    "Finished!", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void prepareData(Path path) {
        System.out.println("Preparing data...");

        try {
            lines = Files.readAllLines(path).toArray(new String[0]);
            ProgressTracker.reset(lines.length);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<MethodData> collectMethodData() {
        System.out.println("Collecting method data...    ");

        List<MethodData> methodList = new ArrayList<>();

        MethodData currentMethod = null;
        boolean readingVariables = true;

        for (String line : lines) {
            ProgressTracker.progress();

            if (currentMethod != null) {
                if (line.isEmpty()) {
                    readingVariables = false;
                    continue;
                }
                if (readingVariables) {
                    int pos1 = skipWhile(line, 0, ' ');
                    int pos2 = skipUntil(line, pos1,' ', '(');

                    if (pos1 == line.length() || pos2 == line.length() || line.charAt(pos2) == '(') {
                        readingVariables = false;
                        currentMethod.methodLines.add(line.substring(skipWhile(line, 0, ' ')));
                        continue;
                    }
                    String variableType = line.substring(pos1, pos2);

                    pos1 = skipWhile(line, pos2, ' ', '*', '&');
                    pos2 = skipUntil(line, pos1, ';');

                    if (pos2 != line.length() - 1) {
                        readingVariables = false;
                        currentMethod.methodLines.add(line.substring(skipWhile(line, 0, ' ')));
                        continue;
                    }
                    String variableName = line.substring(pos1, pos2);

                    currentMethod.localVariables.put(variableName, variableType);
                    continue;
                }
                //method end
                if (line.charAt(0) == '}') {
                    methodList.add(currentMethod);
                    currentMethod = null;

                    continue;
                }
                currentMethod.methodLines.add(line.substring(skipWhile(line, 0, ' ')));
            }
            else {
                if (line.isEmpty()) continue;

                //check if current line is a function header
                if (isFunctionHeader(line)) {
                    currentMethod = new MethodData();
                    currentMethod.signature = line.substring(0, line.length() - 2);
                    currentMethod.localVariables = new LinkedHashMap<>();
                    currentMethod.methodLines = new ArrayList<>();

                    readingVariables = true;
                }
            }
        }
        return methodList;
    }

    static class MethodData {
        String signature;
        HashMap<String, String> localVariables; //name -> type
        List<String> methodLines;
    }
}
