package com.vladomeme;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static com.vladomeme.ParsingUtils.*;

public class RetypeAnalyser {

    static String[] lines;

    static HashSet<String> targetedTypes = null;

    public static void generateRetypingInfo(Path path) {
        prepareData(path);

        if (targetedTypes == null) setTargetedTypes();

        HashMap<String, StructData> types = collectTypeData(path);
        List<MethodData> methodList = collectMethodData();
        lines = null;
        collectRetypes(types, methodList);
        
        try {
            System.out.println("Writing...");

            path = Path.of(path + ".retype");

            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(path.toUri())));

            for (MethodData method : methodList) {
                boolean namePrinted = false;

                for (Map.Entry<String, VariableType> entry : method.localVariables.entrySet()) {
                    if (entry.getValue().changed) {
                        if (!namePrinted) {
                            writer.write(method.signature);
                            writer.newLine();
                            namePrinted = true;
                        }
                        writer.write("    " + entry.getKey());
                        writer.newLine();
                        for (String type : entry.getValue().types) {
                            writer.write("        " + type);
                            writer.newLine();
                        }
                    }
                }
                if (namePrinted) writer.newLine();
            }
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
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setTargetedTypes() {
        targetedTypes = new HashSet<>();
        targetedTypes.add("Il2CppObject");
        targetedTypes.add("Il2CppClass");
        targetedTypes.add("Il2CppType");
        targetedTypes.add("undefined8");
        targetedTypes.add("longlong");
        targetedTypes.add("StringUtils_LineSplitInfo_o");
        targetedTypes.add("System_Collections_IEnumerator_o");
        targetedTypes.add("System_Configuration_ConfigurationCollectionAttribute_o");
        targetedTypes.add("System_Action_o");
        targetedTypes.add("UnityEngine_GameObject_o");
        targetedTypes.add("UnityEngine_UI_Selectable_o");
    }

    private static HashMap<String, StructData> collectTypeData(Path path) {
        System.out.print("Collecting type data...    ");
        ProgressTracker.reset(lines.length);

        HashMap<String, StructData> types = new HashMap<>(); //type, fields

        StructData currentStruct = null;
        boolean calculateSize = false;

        for (String line : lines) {
            ProgressTracker.progress();
            if (line.isEmpty()) continue;

            if (currentStruct != null) {
                //end
                if (line.equals("};")) {
                    if (calculateSize) {
                        applyPadding(currentStruct);
                        currentStruct.size = 0;
                        for (TypeField field : currentStruct.fields) currentStruct.size += field.size;
                    }
                    currentStruct = null;
                    continue;
                }
                int pos = skipWhile(line, 0, ' ');
                if (textAfterEquals(line, pos, "struct ")) pos += 7;
                else if (textAfterEquals(line, pos, "union ")) pos += 6;

                int endPos = skipUntil(line, pos, ' ');

                TypeField field = new TypeField();
                field.type = line.substring(pos, endPos);
                if (line.charAt(endPos + 1) == '*') {
                    field.isPointer = true;
                    field.size = 8;
                    endPos += 2;
                }
                else {
                    field.isPointer = false;
                    tryFindFieldSize(types, field);
                    if (field.size == -1) calculateSize = false;
                    endPos += 1;
                }
                field.name = line.substring(endPos, skipUntil(line, endPos, ';'));

                currentStruct.fields.add(field);
            }
            else {
                if ((line.startsWith("struct ") || line.startsWith("union ")) && line.endsWith("_Fields {")) {
                    currentStruct = new StructData();
                    currentStruct.size = -1;
                    currentStruct.fields = new ArrayList<>();
                    types.put(line.substring(7, line.length() - 9), currentStruct);
                    calculateSize = true;
                }
            }
        }

        boolean changed = true;
        int iteration = 1;

        while (changed) {
            System.out.print("Calculating field sizes (iteration " + iteration + ")...    ");
            ProgressTracker.reset(types.size());

            changed = false;

            for (StructData struct : types.values()) {
                ProgressTracker.progress();
                if (struct.size != -1) continue;

                calculateSize = true;
                for (TypeField field : struct.fields) {
                    if (field.size != -1) continue;

                    tryFindFieldSize(types, field);
                    if (field.size == -1) {
                        calculateSize = false;
                        break;
                    }
                }
                if (calculateSize) {
                    applyPadding(struct);
                    struct.size = 0;
                    for (TypeField field : struct.fields) struct.size += field.size;

                    changed = true;
                    System.out.println("changed");
                }
            }
        }
        //dumpTypeData(types, path);
        return types;
    }

    private static void dumpTypeData(HashMap<String, StructData> types, Path path) {
        try {
            path = Path.of(path + ".TYPEDUMP");
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(path.toUri())));

            int countBad = 0;

            for (Map.Entry<String, StructData> entry : types.entrySet()) {
                if (entry.getValue().size == -1) countBad++;

                writer.write(entry.getKey() + " " + entry.getValue().size);
                writer.newLine();

                for (TypeField field : entry.getValue().fields) {
                    writer.write("    " + field.type + " " + field.name + " " + field.size);
                    writer.newLine();
                }
                writer.newLine();
            }
            writer.close();
            System.out.println("Found " + types.size() + " types, failed to calculate sizes for " + countBad + " types");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void tryFindFieldSize(HashMap<String, StructData> types, TypeField field) {
        field.size = switch (field.type) {
            case "undefined", "bool", "byte", "sbyte", "uchar", "undefined1", "BYTE", "BOOLEAN", "UCHAR", "uint8_t", "int8_t", "CHAR"
                    -> 1;
            case "undefined2", "ushort", "wchar16", "wchar_t", "word", "WCHAR", "WORD", "USHORT", "uint16_t", "int16_t", "wint_t", "wctype_t"
                    -> 2;
            case "errno_t", "undefined4", "dword", "ImageBaseOffset32", "uint", "ulong", "LONG", "__ehstate_t", "DWORD", "BOOL", "XMM_SAVE_AREA32",
                 "ULONG", "uint32_t", "int32_t", "il2cpp_array_lower_bound_t", "UINT", "NTSTATUS", "HRESULT", "LCID", "float", "FLOAT"
                    -> 4;
            case "uint5"
                    -> 5;
            case "longlong", "qword", "ulonglong", "undefined8", "__uint64", "LPVOID", "ULONG_PTR", "DWORD_PTR", "HANDLE", "PVOID",
                 "DWORD64", "LONGLONG", "ULONGLONG", "PEXCEPTION_RECORD", "LPWSTR", "LPBYTE", "PSRWLOCK", "LPCRITICAL_SECTION",
                 "PCRITICAL_SECTION", "va_list", "uintptr_t", "PDWORD64", "SIZE_T", "ULONG64", "INT_PTR", "LONG_PTR", "PSIZE_T",
                 "size_t", "intptr_t", "il2cpp_array_size_t", "int64_t", "uint64_t", "fpos_t", "rsize_t", "LPGUID", "LPCSTR",
                 "PLONG", "LPSTR", "PCWSTR", "LPWCH", "LPCWSTR", "HMODULE", "PBYTE", "double", "DOUBLE"
                    -> 8;
            case "GUID"
                    -> 16;
            default -> {
                int index = field.type.lastIndexOf("_");
                StructData struct = types.get(index != -1 ? field.type.substring(0, index) : field.type);
                if (struct != null) yield struct.size;
                else yield -1;
            }
        };
    }

    private static void applyPadding(StructData struct) {
        int pos = 0;
        int maxSize = 0;
        int i = 0;

        while (i < struct.fields.size()) {
            TypeField field = struct.fields.get(i);
            if (field.size == 0) {
                i++;
                continue;
            }

            int size = Math.min(field.size, 8);
            maxSize = Math.max(maxSize, field.size);

            int mod = pos % size;
            if (mod != 0) {
                TypeField padding = new TypeField();
                padding.name = "padding";
                padding.type = "char[]";
                padding.size = size - mod;
                padding.isPointer = false;
                struct.fields.add(i, padding);

                pos += padding.size;
                i++;
            }
            pos += size;
            i++;
        }
        if (maxSize == 0) return;

        maxSize = Math.min(maxSize, 8);

        int mod = pos % maxSize;
        if (mod != 0) {
            TypeField padding = new TypeField();
            padding.name = "padding";
            padding.type = "char[]";
            padding.size = maxSize - mod;
            padding.isPointer = false;
            struct.fields.add(padding);
        }
    }

    private static List<MethodData> collectMethodData() {
        System.out.print("Collecting method data...    ");
        ProgressTracker.reset(lines.length);

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

                    currentMethod.localVariables.put(variableName, new VariableType(variableType));
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

    private static void collectRetypes(HashMap<String, StructData> types, List<MethodData> methods) {
        System.out.print("Collecting retype information...    ");
        ProgressTracker.reset(methods.size());

        for (MethodData method : methods) {
            ProgressTracker.progress();

            for (String line : method.methodLines) {
                if (line.isEmpty()) continue;

                tryConstructorRetype(method.localVariables, line);
                trySingletonRetype(method.localVariables, line);
                tryComponentRetype(method.localVariables, line, types);
            }
        }
    }

    private static void tryConstructorRetype(HashMap<String, VariableType> variables, String line) {
        int index = line.indexOf("= new ");
        if (index != -1) {
            int pos = skipWhile(line, 0, ' ');
            String variable = line.substring(pos, skipUntil(line, pos, ' ', '.', '('));
            VariableType variableType = variables.get(variable);
            if (variableType == null || (!variableType.changed && !targetedTypes.contains(variableType.types.iterator().next()))) return;

            int endPos = skipUntil(line, index + 6, '(', '[');
            String newType = line.substring(index + 6, endPos);
            if (line.charAt(endPos) == '[') newType += "[] *";
            else newType += "_o *";

            variableType.tryAddType(newType);
        }
        //todo allocation removal not used case
    }

    private static void trySingletonRetype(HashMap<String, VariableType> variables, String line) {
        int index = line.indexOf("SingletonLoadableMonoBehaviour<");
        if (index != -1) {
            index += 31;

            int pos = skipUntil(line, index, '>');
            if (textAfterEquals(line, pos + 1, "_get_Instance__")) {
                int variablePos = skipWhile(line, 0, ' ');
                String variable = line.substring(variablePos, skipUntil(line, variablePos, ' '));
                VariableType variableType = variables.get(variable);

                if (variableType == null || (!variableType.changed && !targetedTypes.contains(variableType.types.iterator().next()))) return;

                String newType = line.substring(index, pos);

                if (textAfterEquals(line, pos + 16, " + 32")) newType = newType + "_c *";
                else newType = newType + "_o *";

                variableType.tryAddType(newType);
            }
        }
    }
    //type to "variable ="
    //UnityEngine_Component_GetComponent<type>
    //UnityEngine_Component_GetComponentInChildren<type>
    //UnityEngine_Component_GetComponentInParent<type>
    //GameObjectExtensions_GetOrAddComponent<type>

    //type[] to "variable ="
    //UnityEngine_Component_GetComponents<type>
    //UnityEngine_Component_GetComponentsInChildren<type>
    //UnityEngine_Component_GetComponentsInParent<type>

    //type to output parameter
    //UnityEngine_Component_TryGetComponent<type>

    //todo UnityEngine_GameObject.
    private static void tryComponentRetype(HashMap<String, VariableType> variables, String line, HashMap<String, StructData> types) {
        VariableType variableType;
        String variableName;
        boolean array = false;
        int pos;

        //Moving the pos before component type
        int index = line.indexOf("Method_UnityEngine_Component_");
        if (index != -1) {
            pos = index + 29;
            if (textAfterEquals(line, pos, "GetComponent")) {
                //get variable name
                int endPos = skipUntilReverse(line, index, ' ');
                if (endPos != -1) { //normal value assignment
                    if (textBeforeEquals(line, endPos - 1, " =")) endPos -= 2;
                    else return;

                    int startPos = skipUntilReverse(line, endPos - 1, ' ', ',', '(');
                    if (startPos == -1) startPos = 0;
                    variableName = line.substring(startPos, endPos);
                }
                else if (line.charAt(pos + 13) == 's') { //array version with a list passed as an argument
                    endPos = skipUntilReverse(line, index - 2, ',', '*', '&');
                    if (endPos != -1) variableName = line.substring(endPos + 1, index - 1);
                    else return;
                }
                else return;

                variableType = variables.get(variableName);
                if (variableType == null || (!variableType.changed && !targetedTypes.contains(variableType.types.iterator().next()))) return;

                //move pos
                pos += 12;
                if (line.charAt(pos) == 's') { //GetComponents...
                    pos += 1;
                    array = true;
                }
                if (line.charAt(pos) != '<') {
                    if (line.charAt(pos) == 'I') {
                        if (line.charAt(pos + 2) == 'P') pos += 8; //GetComponent(s)InParent
                        else if (line.charAt(pos + 2) == 'C') pos += 10; //GetComponent(s)InChildren
                    }
                }
            }
            else if (textAfterEquals(line, pos, "TryGetComponent")) {
                //get variable name
                int endPos = skipUntilReverse(line, index - 2, ',', '*', '&');
                if (endPos != -1) variableName = line.substring(endPos + 1, index - 1);
                else return;

                variableType = variables.get(variableName);
                if (variableType == null || (!variableType.changed && !targetedTypes.contains(variableType.types.iterator().next()))) return;
                //move pos
                pos += 15;
            }
            else return;
        }
        else {
            index = line.indexOf("Method_GameObjectExtensions_GetOrAddComponent");
            if (index != -1) {
                //get variable name
                int endPos = skipUntilReverse(line, index, ' ');
                if (endPos != -1) {
                    if (textBeforeEquals(line, endPos - 1, " =")) endPos -= 2;
                    else return;

                    int startPos = skipUntilReverse(line, endPos - 1, ' ', ',', '(');
                    if (startPos == -1) startPos = 0;
                    variableName = line.substring(startPos, endPos);
                }
                else return;

                variableType = variables.get(variableName);
                if (variableType == null || (!variableType.changed && !targetedTypes.contains(variableType.types.iterator().next()))) return;
                //move pos
                pos = index + 45;
            }
            else return;
        }

        if (line.charAt(pos) == '<') {
            int endPos = skipUntilMatching(line, pos + 1, '<', '>');
            String newType = tryExpandType(types, line.substring(pos + 1, endPos)) + (array ? "[] *" : "_o *");
            variableType.tryAddType(newType);
        }
    }

    private static String tryExpandType(HashMap<String, StructData> types, String type) {
        List<Map.Entry<String, StructData>> matches = new ArrayList<>();
        type = '_' + type;

        for (Map.Entry<String, StructData> entry : types.entrySet()) {
            if (entry.getKey().endsWith(type)) matches.add(entry);
        }

        if (matches.isEmpty()) return type;
        if (matches.size() == 1) return matches.getFirst().getKey();

        for (Map.Entry<String, StructData> match : matches) {
            StructData currentType = match.getValue();

            loop:
            while (currentType != null) {
                for (TypeField field : currentType.fields) {
                    if (field.name.equals("super")) {
                        String typeName = field.type.substring(0, field.type.length() - 7);
                        if (typeName.equals("UnityEngine_Component")) return match.getKey();

                        currentType = types.get(typeName);
                        continue loop;
                    }
                }
                currentType = null;
            }
        }
        return type;
    }

    static class StructData {
        int size;
        List<TypeField> fields;
    }

    static class TypeField {
        int size;
        boolean isPointer;
        String type;
        String name;
    }

    static class MethodData {
        String signature;
        HashMap<String, VariableType> localVariables; //name -> type
        List<String> methodLines;
    }

    static class VariableType {
        final Set<String> types;
        boolean changed;

        public VariableType(String type) {
            this.types = new HashSet<>();
            this.types.add(type);
            this.changed = false;
        }

        public void tryAddType(String newType) {
            boolean shouldAdd = true;
            for (String type : types) {
                if (Pattern.compile(Pattern.quote(newType), Pattern.CASE_INSENSITIVE).matcher(type).find()) shouldAdd = false;
            }
            if (shouldAdd) {
                if (!changed) types.clear();
                types.add(newType);
                changed = true;
            }
        }
    }
}
