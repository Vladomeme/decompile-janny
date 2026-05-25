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

    //todo Method$SingletonLoadableMonoBehaviour<GameScript>.get_Instance() + 32
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
                        writer.write("    " + entry.getKey() + " " + entry.getValue().type);
                        writer.newLine();
                    }
                }
                if (namePrinted) writer.newLine();
            }

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
                    struct.size = 0;
                    for (TypeField field : struct.fields) struct.size += field.size;

                    changed = true;
                    System.out.println("changed");
                }
            }
        }

        try {
            path = Path.of(path + ".TYPEDUMP");
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(path.toUri())));

            int countBad = 0;

            for (Map.Entry<String, StructData> entry : types.entrySet()) {
                if (entry.getValue().size == -1) countBad++;

                writer.write(entry.getKey());
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
        return types;
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
        ProgressTracker.reset(methods.size());
        for (MethodData method : methods) {
            ProgressTracker.progress();

            for (String line : method.methodLines) {
                tryConstructorRetype(method.localVariables, line);
            }
        }
    }

    private static void tryConstructorRetype(HashMap<String, VariableType> variables, String line) {
        int index = line.indexOf("= new ");
        if (index != -1) {
            int pos = skipWhile(line, 0, ' ');
            String variable = line.substring(pos, skipUntil(line, pos, ' ', '.', '('));
            VariableType variableType = variables.get(variable);
            if (variableType == null || variableType.changed || !targetedTypes.contains(variableType.type)) return;

            int endPos = skipUntil(line, index + 6, '(', '[');
            String newType = line.substring(index + 6, endPos);
            if (line.charAt(endPos) == '[') newType = newType + "[]";

            if (!Pattern.compile(Pattern.quote(newType), Pattern.CASE_INSENSITIVE).matcher(variableType.type).find()) {
                variableType.type = newType.replace('<','_').replace('>', '_').replace(",", "__").replace(".", "__") + "_o *";
                variableType.changed = true;
            }
        }
        //todo allocation removal not used case
    }

    //Method_UnityEngine_Component_GetComponent<type> -> type to variable =
    //Method_UnityEngine_Component_GetComponents<type> -> type[] to variable =

    //Method_UnityEngine_Component_GetComponentInChildren<type> -> type to variable =
    //Method_UnityEngine_Component_GetComponentsInChildren<type> = type[] to variable =

    //Method_UnityEngine_Component_GetComponentInParent<type> - > type to variable =
    //Method_UnityEngine_Component_GetComponentsInParent<type> - > type[] to variable =

    //Method_UnityEngine_Component_TryGetComponent<type> - type to output parameter
    //Method_GameObjectExtensions_GetOrAddComponent<type> - type to variable =
    private static void tryComponentRetype() {

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
        String type;
        boolean changed;

        public VariableType(String type) {
            this.type = type;
            this.changed = false;
        }
    }
}
