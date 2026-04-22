package com.vladomeme;

public class OptionDescriptions {

    public static String removeUselessDecompilerComments = """
            Removes these comments from decompiled code:
            
                // DISPLAY WARNING: Type casts are NOT being printed
                // WARNING: Subroutine does not return
            """;

    public static String removeNullPointerTypeCasts = """
            Replaces null value casts to different types with a 'NULL'.
            Useful it you forgot to turn this option on in Ghidra.
            
            Examples:
                (UnityEngine_GameObject_o *)0 => NULL
                (UnityEngine_GameObject_o *)0x0 => NULL
            """;

    public static String removeMethodInitialization = """
            Removes method initialization blocks:
            
            Examples:
                if (DAT_1834055cb == '\\0') {
                    FUN_180281b10(6495205400);
                    FUN_180281b10(6494991304);
                    FUN_180281b10(6494821848);
                    FUN_180281b10(6495339720);
                    DAT_1834055cb = '\\x01';
                }
            
                bVar4 = DAT_183404984 == '\\0';
                ** unrelated code **
                if (bVar4) {
                    FUN_180281b10(6494986976);
                    FUN_180281b10(6495081824);
                    DAT_183404984 = '\\x01';
                }
            """;

    public static String removeStaticInitialization = """
            Removes static member initialization blocks. Rare blocks
            containing additional logic are unaffected.
            
            Examples:
                if (*&(LeanTween_TypeInfo->_2).field_0x1c == 0) {
                    il2cpp_runtime_class_init(LeanTween_TypeInfo);
                }
            
                if (*&(UnityEngine_Object_TypeInfo->_2).field_0x1c == 0) {
                    il2cpp_runtime_class_init();
                }
            """;

    public static String simplifyProcedureInterruptions = """
            Simplifies procedure interruption functions and goto label
            instructions by replacing them with direct 'return' statements.
            Additionally, removes unnecessary void return statements,
            attempting to preserve function logic.
            
            Before:
                if (pSVar2 == NULL) {
                    return;
                }
                if (pSVar1 != NULL) {
                    if (pSVar1.isRead == false) {
                        psVar1.read();
                        pSVar3 = this.listAdapter;
                        if (pSVar3 == NULL) goto LAB_18050a0e3;
                        pSVar3.updateButton(i);
                    }
                    return;
                }
            LAB_18050a0e3:
                FUN_180281d50(); <- procedure interruption
            } <- end of function
            
            After:
                if (pSVar2 == NULL) {
                    return;
                }
                if (pSVar1 != NULL) {
                    if (pSVar1.isRead == false) {
                        psVar1.read();
                        pSVar3 = this.listAdapter;
                        if (pSVar3 == NULL) return;
                        pSVar3.updateButton(i);
                    }
                }
            }
            """;

    public static String simplifyObjectReferences = """
            Improves readability for object references and method calls.
            Removes unnecessary parentheses.
            
            Examples:
                (__this->fields).listAdapter
                to
                __this.listAdapter
            
                (*(__this->klass->vtable)._23_Continue.methodPtr)(__this,(__this->klass->vtable)._23_Continue.method);
                to
                __this.Continue();
            
                (*(pSVar3->klass->vtable)._8_UpdateButton.methodPtr)(pSVar3,i,k,(pSVar3->klass->vtable)._8_UpdateButton.method);
                to
                pSVar3.UpdateButton(i,k);
            
                (__this_01->_1).name
                to
                __this_01.name
            """;

    public static String replaceNullChecks = """
            Replaces (mostly) auto-generated null checks with marker comments,
            removing block indentation. Preserves if blocks with return statements.
            
            Before:
                __this_00 = __this._noEmailsText;
                if (__this_00 != NULL) {
                    __this_01 = UnityEngine_Component__get_gameObject(__this_00,NULL);
                    if (__this_01 != NULL) {
                        UnityEngine_GameObject__SetActive(__this_01,count < 1,NULL);
                        return;
                    }
                }
            
            After:
                __this_00 = __this._noEmailsText;
           
                //__this_00 null check
            
                __this_01 = UnityEngine_Component__get_gameObject(__this_00,NULL);
                if (__this_01 != NULL) {
                   UnityEngine_GameObject__SetActive(__this_01,count < 1,NULL);
                   return;
                }
            """;

    public static String removeArrayBoundChecks = """
            Removes (mostly) auto-generated array bound checks.
            
            Targeted if block header examples:
                if (items->max_length != 0) {
                if (1 < items->max_length) {
                if (2 < items.max_length) {
            """;

    public static String simplifyArrayAccess = """
            Simplifies array initialization and element access.
            
            Before:
                pSVar1 = FUN_180280ce0(string___TypeInfo,3);
                pSVar1.m_Items[0] = String1;
                pSVar1.m_Items[1] = String2;
                pSVar1.m_Items[2] = String3;
            
            After:
                pSVar1 = new string[3];
                pSVar1[0] = String1;
                pSVar1[1] = String2;
                pSVar1[2] = String3;
            """;

    public static String formatGenericTypes = """
            Puts generic type arguments in angle brackets if they're missing.
            Result might be incomplete for rarely occurring types.
            
            Fungus_VariableBase_Vector3_ => Fungus_VariableBase<Vector3>
            System.Collections.Generic.List_Command_ => System.Collections.Generic.List<Command>
            """;

    public static String replaceUnderscoresForMethods = """
            Actual method names will be separated from the class name with a
            dot instead of underscores (in both declarations and calls).
            
            
            """;
}
