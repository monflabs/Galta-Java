/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.rt.builtins.standard.regexp.jdk;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps ECMAScript Unicode property names to Java regex equivalents.
 * Supports General_Category, Script/Script_Extensions, and binary properties.
 */
public class UnicodeProperties {

    private static final Map<String, String> GENERAL_CATEGORY = new HashMap<>();
    private static final Map<String, String> BINARY_PROPERTY = new HashMap<>();
    private static final Map<String, String> SCRIPT_NAMES = new HashMap<>();

    static {
        initGeneralCategory();
        initScripts();
        initBinaryProperties();
    }

    private static void initGeneralCategory() {
        // Short forms (Java supports these directly)
        String[] shorts = {
            "L","LC","Lu","Ll","Lt","Lm","Lo",
            "M","Mn","Mc","Me",
            "N","Nd","Nl","No",
            "P","Pc","Pd","Ps","Pe","Pi","Pf","Po",
            "S","Sm","Sc","Sk","So",
            "Z","Zs","Zl","Zp",
            "C","Cc","Cf","Cs","Co","Cn"
        };
        for (String s : shorts) {
            GENERAL_CATEGORY.put(s, s);
        }

        // Long names to short form
        GENERAL_CATEGORY.put("Letter", "L");
        GENERAL_CATEGORY.put("Cased_Letter", "LC");
        GENERAL_CATEGORY.put("Uppercase_Letter", "Lu");
        GENERAL_CATEGORY.put("Lowercase_Letter", "Ll");
        GENERAL_CATEGORY.put("Titlecase_Letter", "Lt");
        GENERAL_CATEGORY.put("Modifier_Letter", "Lm");
        GENERAL_CATEGORY.put("Other_Letter", "Lo");
        GENERAL_CATEGORY.put("Mark", "M");
        GENERAL_CATEGORY.put("Nonspacing_Mark", "Mn");
        GENERAL_CATEGORY.put("Spacing_Mark", "Mc");
        GENERAL_CATEGORY.put("Enclosing_Mark", "Me");
        GENERAL_CATEGORY.put("Number", "N");
        GENERAL_CATEGORY.put("Decimal_Number", "Nd");
        GENERAL_CATEGORY.put("Letter_Number", "Nl");
        GENERAL_CATEGORY.put("Other_Number", "No");
        GENERAL_CATEGORY.put("Punctuation", "P");
        GENERAL_CATEGORY.put("punct", "P");
        GENERAL_CATEGORY.put("Connector_Punctuation", "Pc");
        GENERAL_CATEGORY.put("Dash_Punctuation", "Pd");
        GENERAL_CATEGORY.put("Open_Punctuation", "Ps");
        GENERAL_CATEGORY.put("Close_Punctuation", "Pe");
        GENERAL_CATEGORY.put("Initial_Punctuation", "Pi");
        GENERAL_CATEGORY.put("Final_Punctuation", "Pf");
        GENERAL_CATEGORY.put("Other_Punctuation", "Po");
        GENERAL_CATEGORY.put("Symbol", "S");
        GENERAL_CATEGORY.put("Math_Symbol", "Sm");
        GENERAL_CATEGORY.put("Currency_Symbol", "Sc");
        GENERAL_CATEGORY.put("Modifier_Symbol", "Sk");
        GENERAL_CATEGORY.put("Other_Symbol", "So");
        GENERAL_CATEGORY.put("Separator", "Z");
        GENERAL_CATEGORY.put("Space_Separator", "Zs");
        GENERAL_CATEGORY.put("Line_Separator", "Zl");
        GENERAL_CATEGORY.put("Paragraph_Separator", "Zp");
        GENERAL_CATEGORY.put("Other", "C");
        GENERAL_CATEGORY.put("Control", "Cc");
        GENERAL_CATEGORY.put("cntrl", "Cc");
        GENERAL_CATEGORY.put("Format", "Cf");
        GENERAL_CATEGORY.put("Surrogate", "Cs");
        GENERAL_CATEGORY.put("Private_Use", "Co");
        GENERAL_CATEGORY.put("Unassigned", "Cn");
    }

    private static void initScripts() {
        // All script names that Java 17 supports via \p{IsXxx}
        String[] scripts = {
            "Adlam", "Ahom", "Anatolian_Hieroglyphs", "Arabic", "Armenian",
            "Avestan", "Balinese", "Bamum", "Bassa_Vah", "Batak",
            "Bengali", "Bhaiksuki", "Bopomofo", "Brahmi", "Braille",
            "Buginese", "Buhid", "Canadian_Aboriginal", "Carian", "Caucasian_Albanian",
            "Chakma", "Cham", "Cherokee", "Chorasmian", "Common",
            "Coptic", "Cuneiform", "Cypriot", "Cypro_Minoan", "Cyrillic",
            "Deseret", "Devanagari", "Dives_Akuru", "Dogra", "Duployan",
            "Egyptian_Hieroglyphs", "Elbasan", "Elymaic", "Ethiopic", "Georgian",
            "Glagolitic", "Gothic", "Grantha", "Greek", "Gujarati",
            "Gunjala_Gondi", "Gurmukhi", "Han", "Hangul", "Hanifi_Rohingya",
            "Hanunoo", "Hatran", "Hebrew", "Hiragana", "Imperial_Aramaic",
            "Inherited", "Inscriptional_Pahlavi", "Inscriptional_Parthian", "Javanese", "Kaithi",
            "Kannada", "Katakana", "Kayah_Li", "Kharoshthi", "Khitan_Small_Script",
            "Khmer", "Khojki", "Khudawadi", "Lao", "Latin",
            "Lepcha", "Limbu", "Linear_A", "Linear_B", "Lisu",
            "Lycian", "Lydian", "Mahajani", "Makasar", "Malayalam",
            "Mandaic", "Manichaean", "Marchen", "Masaram_Gondi", "Medefaidrin",
            "Meetei_Mayek", "Mende_Kikakui", "Meroitic_Cursive", "Meroitic_Hieroglyphs", "Miao",
            "Modi", "Mongolian", "Mro", "Multani", "Myanmar",
            "Nabataean", "Nandinagari", "New_Tai_Lue", "Newa", "Nko",
            "Nushu", "Nyiakeng_Puachue_Hmong", "Ogham", "Ol_Chiki", "Old_Hungarian",
            "Old_Italic", "Old_North_Arabian", "Old_Permic", "Old_Persian", "Old_Sogdian",
            "Old_South_Arabian", "Old_Turkic", "Old_Uyghur", "Oriya", "Osage",
            "Osmanya", "Pahawh_Hmong", "Palmyrene", "Pau_Cin_Hau", "Phags_Pa",
            "Phoenician", "Psalter_Pahlavi", "Rejang", "Runic", "Samaritan",
            "Saurashtra", "Sharada", "Shavian", "Siddham", "SignWriting",
            "Sinhala", "Sogdian", "Sora_Sompeng", "Soyombo", "Sundanese",
            "Syloti_Nagri", "Syriac", "Tagalog", "Tagbanwa", "Tai_Le",
            "Tai_Tham", "Tai_Viet", "Takri", "Tamil", "Tangsa",
            "Tangut", "Telugu", "Thaana", "Thai", "Tibetan",
            "Tifinagh", "Tirhuta", "Toto", "Ugaritic", "Vai",
            "Vithkuqi", "Wancho", "Warang_Citi", "Yezidi", "Yi",
            "Zanabazar_Square"
        };
        for (String s : scripts) {
            SCRIPT_NAMES.put(s, s);
        }
        // Common short aliases
        SCRIPT_NAMES.put("Qaai", "Inherited");
        SCRIPT_NAMES.put("Zyyy", "Common");
        SCRIPT_NAMES.put("Zinh", "Inherited");
        SCRIPT_NAMES.put("Hira", "Hiragana");
        SCRIPT_NAMES.put("Kana", "Katakana");
        SCRIPT_NAMES.put("Hang", "Hangul");
        SCRIPT_NAMES.put("Hani", "Han");
        SCRIPT_NAMES.put("Latn", "Latin");
        SCRIPT_NAMES.put("Grek", "Greek");
        SCRIPT_NAMES.put("Cyrl", "Cyrillic");
        SCRIPT_NAMES.put("Arab", "Arabic");
        SCRIPT_NAMES.put("Hebr", "Hebrew");
        SCRIPT_NAMES.put("Deva", "Devanagari");
        SCRIPT_NAMES.put("Thai", "Thai");
        SCRIPT_NAMES.put("Geor", "Georgian");
        SCRIPT_NAMES.put("Armn", "Armenian");
        SCRIPT_NAMES.put("Beng", "Bengali");
        SCRIPT_NAMES.put("Guru", "Gurmukhi");
        SCRIPT_NAMES.put("Gujr", "Gujarati");
        SCRIPT_NAMES.put("Orya", "Oriya");
        SCRIPT_NAMES.put("Taml", "Tamil");
        SCRIPT_NAMES.put("Telu", "Telugu");
        SCRIPT_NAMES.put("Knda", "Kannada");
        SCRIPT_NAMES.put("Mlym", "Malayalam");
        SCRIPT_NAMES.put("Sinh", "Sinhala");
        SCRIPT_NAMES.put("Mymr", "Myanmar");
        SCRIPT_NAMES.put("Ethi", "Ethiopic");
        SCRIPT_NAMES.put("Khmr", "Khmer");
        SCRIPT_NAMES.put("Mong", "Mongolian");
        SCRIPT_NAMES.put("Tibt", "Tibetan");
    }

    private static void initBinaryProperties() {
        // Properties that Java 17 \p{IsXxx} supports natively
        BINARY_PROPERTY.put("Alphabetic", "\\p{IsAlphabetic}");
        BINARY_PROPERTY.put("Alpha", "\\p{IsAlphabetic}");
        BINARY_PROPERTY.put("Lowercase", "\\p{IsLowercase}");
        BINARY_PROPERTY.put("Lower", "\\p{IsLowercase}");
        BINARY_PROPERTY.put("Uppercase", "\\p{IsUppercase}");
        BINARY_PROPERTY.put("Upper", "\\p{IsUppercase}");
        BINARY_PROPERTY.put("White_Space", "\\p{IsWhite_Space}");
        BINARY_PROPERTY.put("space", "\\p{IsWhite_Space}");
        BINARY_PROPERTY.put("Ideographic", "\\p{IsIdeographic}");
        BINARY_PROPERTY.put("Hex_Digit", "\\p{IsHex_Digit}");
        BINARY_PROPERTY.put("ASCII_Hex_Digit", "\\p{IsHex_Digit}");
        BINARY_PROPERTY.put("AHex", "\\p{IsHex_Digit}");
        BINARY_PROPERTY.put("Join_Control", "\\p{IsJoin_Control}");
        BINARY_PROPERTY.put("Join_C", "\\p{IsJoin_Control}");
        BINARY_PROPERTY.put("Noncharacter_Code_Point", "\\p{IsNoncharacterCodePoint}");

        // Properties expressible as GC combinations
        BINARY_PROPERTY.put("ASCII", "[\\x00-\\x7f]");
        BINARY_PROPERTY.put("Any", "[\\x{0}-\\x{10ffff}]");
        BINARY_PROPERTY.put("Assigned", "\\P{Cn}");

        // ID_Start ≈ Lu+Ll+Lt+Lm+Lo+Nl + Other_ID_Start
        BINARY_PROPERTY.put("ID_Start", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\u2118\\u212E\\u309B\\u309C]");
        BINARY_PROPERTY.put("IDS", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\u2118\\u212E\\u309B\\u309C]");
        // ID_Continue ≈ ID_Start + Mn+Mc+Nd+Pc + ZWNJ + ZWJ
        BINARY_PROPERTY.put("ID_Continue", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\u200C\\u200D\\u2118\\u212E\\u309B\\u309C\\u00B7\\u0387\\u1369-\\u1371\\u19DA]");
        BINARY_PROPERTY.put("IDC", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\u200C\\u200D\\u2118\\u212E\\u309B\\u309C\\u00B7\\u0387\\u1369-\\u1371\\u19DA]");
        // XID_Start/XID_Continue are close approximations of ID_Start/ID_Continue
        BINARY_PROPERTY.put("XID_Start", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\u2118\\u212E\\u309B\\u309C]");
        BINARY_PROPERTY.put("XIDS", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\u2118\\u212E\\u309B\\u309C]");
        BINARY_PROPERTY.put("XID_Continue", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\u200C\\u200D\\u2118\\u212E\\u309B\\u309C\\u00B7\\u0387\\u1369-\\u1371\\u19DA]");
        BINARY_PROPERTY.put("XIDC", "[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\u200C\\u200D\\u2118\\u212E\\u309B\\u309C\\u00B7\\u0387\\u1369-\\u1371\\u19DA]");

        // Math ≈ Sm + some extra
        BINARY_PROPERTY.put("Math", "[\\p{Sm}\\u002B\\u003C-\\u003E\\u007C\\u007E\\u00AC\\u00B1\\u00D7\\u00F7]");

        // Dash — explicit characters
        BINARY_PROPERTY.put("Dash", "[\\u002D\\u058A\\u05BE\\u1400\\u1806\\u2010-\\u2015\\u2053\\u207B\\u208B\\u2212\\u2E17\\u2E1A\\u2E3A\\u2E3B\\u2E40\\u2E5D\\u301C\\u3030\\u30A0\\uFE31\\uFE32\\uFE58\\uFE63\\uFF0D\\x{10EAD}]");

        // Diacritic
        BINARY_PROPERTY.put("Diacritic", "[\\p{Mn}\\p{Me}\\u005E\\u0060\\u00A8\\u00AF\\u00B4\\u00B7\\u00B8\\u02B0-\\u034E\\u0350-\\u0357\\u035D-\\u0362\\u0374\\u0375\\u037A\\u0384\\u0385\\u0483-\\u0487\\u0559\\u0591-\\u05A1\\u05A3-\\u05BD\\u05BF\\u05C1\\u05C2\\u05C4\\u064B-\\u0652\\u0657\\u0658\\u06DF\\u06E0\\u06E5\\u06E6\\u06EA-\\u06EC\\u0730-\\u074A]");
        BINARY_PROPERTY.put("Dia", "[\\p{Mn}\\p{Me}\\u005E\\u0060\\u00A8\\u00AF\\u00B4\\u00B7\\u00B8\\u02B0-\\u034E\\u0350-\\u0357\\u035D-\\u0362\\u0374\\u0375\\u037A\\u0384\\u0385\\u0483-\\u0487\\u0559\\u0591-\\u05A1\\u05A3-\\u05BD\\u05BF\\u05C1\\u05C2\\u05C4\\u064B-\\u0652\\u0657\\u0658\\u06DF\\u06E0\\u06E5\\u06E6\\u06EA-\\u06EC\\u0730-\\u074A]");

        // Extender
        BINARY_PROPERTY.put("Extender", "[\\u00B7\\u02D0\\u02D1\\u0640\\u07FA\\u0E46\\u0EC6\\u180A\\u1843\\u1AA7\\u1C36\\u1C7B\\u3005\\u3031-\\u3035\\u309D\\u309E\\u30FC-\\u30FE\\uA015\\uA60C\\uA9CF\\uA9E6\\uAA70\\uAADD\\uAAF3\\uAAF4\\uFF70\\x{10781}\\x{10782}\\x{1135D}\\x{115C6}-\\x{115C8}\\x{11A98}\\x{16B42}\\x{16B43}\\x{16FE0}\\x{16FE1}\\x{16FE3}\\x{1E13C}\\x{1E13D}\\x{1E944}-\\x{1E946}]");
        BINARY_PROPERTY.put("Ext", "[\\u00B7\\u02D0\\u02D1\\u0640\\u07FA\\u0E46\\u0EC6\\u180A\\u1843\\u1AA7\\u1C36\\u1C7B\\u3005\\u3031-\\u3035\\u309D\\u309E\\u30FC-\\u30FE\\uA015\\uA60C\\uA9CF\\uA9E6\\uAA70\\uAADD\\uAAF3\\uAAF4\\uFF70\\x{10781}\\x{10782}\\x{1135D}\\x{115C6}-\\x{115C8}\\x{11A98}\\x{16B42}\\x{16B43}\\x{16FE0}\\x{16FE1}\\x{16FE3}\\x{1E13C}\\x{1E13D}\\x{1E944}-\\x{1E946}]");

        // Pattern_Syntax
        BINARY_PROPERTY.put("Pattern_Syntax", "[\\u0021-\\u002F\\u003A-\\u0040\\u005B-\\u005E\\u0060\\u007B-\\u007E\\u00A1-\\u00A7\\u00A9\\u00AB\\u00AC\\u00AE\\u00B0\\u00B1\\u00B6\\u00BB\\u00BF\\u00D7\\u00F7\\u2010-\\u2027\\u2030-\\u203E\\u2041-\\u2053\\u2055-\\u205E\\u2190-\\u245F\\u2500-\\u2775\\u2794-\\u2BFF\\u2E00-\\u2E7F\\u3001-\\u3003\\u3008-\\u3020\\u3030\\uFD3E\\uFD3F\\uFE45\\uFE46]");
        BINARY_PROPERTY.put("Pat_Syn", "[\\u0021-\\u002F\\u003A-\\u0040\\u005B-\\u005E\\u0060\\u007B-\\u007E\\u00A1-\\u00A7\\u00A9\\u00AB\\u00AC\\u00AE\\u00B0\\u00B1\\u00B6\\u00BB\\u00BF\\u00D7\\u00F7\\u2010-\\u2027\\u2030-\\u203E\\u2041-\\u2053\\u2055-\\u205E\\u2190-\\u245F\\u2500-\\u2775\\u2794-\\u2BFF\\u2E00-\\u2E7F\\u3001-\\u3003\\u3008-\\u3020\\u3030\\uFD3E\\uFD3F\\uFE45\\uFE46]");

        // Pattern_White_Space
        BINARY_PROPERTY.put("Pattern_White_Space", "[\\u0009-\\u000D\\u0020\\u0085\\u200E\\u200F\\u2028\\u2029]");
        BINARY_PROPERTY.put("Pat_WS", "[\\u0009-\\u000D\\u0020\\u0085\\u200E\\u200F\\u2028\\u2029]");

        // Regional_Indicator
        BINARY_PROPERTY.put("Regional_Indicator", "[\\x{1F1E6}-\\x{1F1FF}]");
        BINARY_PROPERTY.put("RI", "[\\x{1F1E6}-\\x{1F1FF}]");

        // Variation_Selector
        BINARY_PROPERTY.put("Variation_Selector", "[\\uFE00-\\uFE0F\\x{E0100}-\\x{E01EF}]");
        BINARY_PROPERTY.put("VS", "[\\uFE00-\\uFE0F\\x{E0100}-\\x{E01EF}]");

        // Sentence_Terminal
        BINARY_PROPERTY.put("Sentence_Terminal", "[\\u0021\\u002E\\u003F\\u0589\\u061D\\u061F\\u06D4\\u0700\\u0701\\u0702\\u07F9\\u0837\\u0839\\u083D\\u083E\\u0964\\u0965\\u104A\\u104B\\u1362\\u1367\\u1368\\u166E\\u1735\\u1736\\u1803\\u1809\\u1944\\u1945\\u1AA8-\\u1AAB\\u1B5A\\u1B5B\\u1B5E\\u1B5F\\u1B7D\\u1B7E\\u1C3B\\u1C3C\\u1C7E\\u1C7F\\u203C\\u203D\\u2047-\\u2049\\u2E2E\\u2E3C\\u2E53\\u2E54\\u3002\\uA4FF\\uA60E\\uA60F\\uA6F3\\uA6F7\\uA876\\uA877\\uA8CE\\uA8CF\\uA92F\\uA9C8\\uA9C9\\uAA5D-\\uAA5F\\uAAF0\\uAAF1\\uABEB\\uFE52\\uFE56\\uFE57\\uFF01\\uFF0E\\uFF1F\\uFF61\\x{10A56}\\x{10A57}\\x{10F55}-\\x{10F59}\\x{10F86}-\\x{10F89}\\x{11047}\\x{11048}\\x{110BE}-\\x{110C1}\\x{11141}-\\x{11143}\\x{111C5}\\x{111C6}\\x{111CD}\\x{111DE}\\x{111DF}\\x{11238}\\x{11239}\\x{1123B}\\x{1123C}\\x{112A9}\\x{1144B}\\x{1144C}\\x{115C2}\\x{115C3}\\x{115C9}-\\x{115D7}\\x{11641}\\x{11642}\\x{1173C}-\\x{1173E}\\x{11944}\\x{11946}\\x{11A42}\\x{11A43}\\x{11A9B}\\x{11A9C}\\x{11C41}\\x{11C42}\\x{11EF7}\\x{11EF8}\\x{11F43}\\x{11F44}\\x{16A6E}\\x{16A6F}\\x{16AF5}\\x{16B37}\\x{16B38}\\x{16B44}\\x{16E98}\\x{1BC9F}\\x{1DA88}]");
        BINARY_PROPERTY.put("STerm", "[\\u0021\\u002E\\u003F\\u0589\\u061D\\u061F\\u06D4\\u0700\\u0701\\u0702\\u07F9\\u0837\\u0839\\u083D\\u083E\\u0964\\u0965\\u104A\\u104B\\u1362\\u1367\\u1368\\u166E\\u1735\\u1736\\u1803\\u1809\\u1944\\u1945\\u1AA8-\\u1AAB\\u1B5A\\u1B5B\\u1B5E\\u1B5F\\u1B7D\\u1B7E\\u1C3B\\u1C3C\\u1C7E\\u1C7F\\u203C\\u203D\\u2047-\\u2049\\u2E2E\\u2E3C\\u2E53\\u2E54\\u3002\\uA4FF\\uA60E\\uA60F\\uA6F3\\uA6F7\\uA876\\uA877\\uA8CE\\uA8CF\\uA92F\\uA9C8\\uA9C9\\uAA5D-\\uAA5F\\uAAF0\\uAAF1\\uABEB\\uFE52\\uFE56\\uFE57\\uFF01\\uFF0E\\uFF1F\\uFF61\\x{10A56}\\x{10A57}\\x{10F55}-\\x{10F59}\\x{10F86}-\\x{10F89}\\x{11047}\\x{11048}\\x{110BE}-\\x{110C1}\\x{11141}-\\x{11143}\\x{111C5}\\x{111C6}\\x{111CD}\\x{111DE}\\x{111DF}\\x{11238}\\x{11239}\\x{1123B}\\x{1123C}\\x{112A9}\\x{1144B}\\x{1144C}\\x{115C2}\\x{115C3}\\x{115C9}-\\x{115D7}\\x{11641}\\x{11642}\\x{1173C}-\\x{1173E}\\x{11944}\\x{11946}\\x{11A42}\\x{11A43}\\x{11A9B}\\x{11A9C}\\x{11C41}\\x{11C42}\\x{11EF7}\\x{11EF8}\\x{11F43}\\x{11F44}\\x{16A6E}\\x{16A6F}\\x{16AF5}\\x{16B37}\\x{16B38}\\x{16B44}\\x{16E98}\\x{1BC9F}\\x{1DA88}]");

        // Terminal_Punctuation (superset of Sentence_Terminal, includes commas etc.)
        BINARY_PROPERTY.put("Terminal_Punctuation", "[\\u0021\\u002C\\u002E\\u003A\\u003B\\u003F\\u037E\\u0387\\u0589\\u05C3\\u060C\\u061B\\u061D\\u061F\\u06D4\\u0700-\\u070A\\u070C\\u07F8\\u07F9\\u0830-\\u083E\\u085E\\u0964\\u0965\\u0E5A\\u0E5B\\u0F08\\u0F0D-\\u0F12\\u104A\\u104B\\u1361-\\u1368\\u166E\\u16EB-\\u16ED\\u1735\\u1736\\u17D4-\\u17D6\\u17DA\\u1802-\\u1805\\u1808\\u1809\\u1944\\u1945\\u1AA8-\\u1AAB\\u1B5A-\\u1B5B\\u1B5D-\\u1B5F\\u1B7D\\u1B7E\\u1C3B-\\u1C3F\\u1C7E\\u1C7F\\u203C\\u203D\\u2047-\\u2049\\u2E2E\\u2E3C\\u2E41\\u2E4C\\u2E4E\\u2E4F\\u2E53\\u2E54\\u3001\\u3002\\uA4FE\\uA4FF\\uA60D-\\uA60F\\uA6F3-\\uA6F7\\uA876\\uA877\\uA8CE\\uA8CF\\uA92F\\uA9C7-\\uA9C9\\uAA5D-\\uAA5F\\uAADF\\uAAF0\\uAAF1\\uABEB\\uFE50-\\uFE52\\uFE54-\\uFE57\\uFF01\\uFF0C\\uFF0E\\uFF1A\\uFF1B\\uFF1F\\uFF61\\uFF64]");
        BINARY_PROPERTY.put("Term", "[\\u0021\\u002C\\u002E\\u003A\\u003B\\u003F\\u037E\\u0387\\u0589\\u05C3\\u060C\\u061B\\u061D\\u061F\\u06D4\\u0700-\\u070A\\u070C\\u07F8\\u07F9\\u0830-\\u083E\\u085E\\u0964\\u0965\\u0E5A\\u0E5B\\u0F08\\u0F0D-\\u0F12\\u104A\\u104B\\u1361-\\u1368\\u166E\\u16EB-\\u16ED\\u1735\\u1736\\u17D4-\\u17D6\\u17DA\\u1802-\\u1805\\u1808\\u1809\\u1944\\u1945\\u1AA8-\\u1AAB\\u1B5A-\\u1B5B\\u1B5D-\\u1B5F\\u1B7D\\u1B7E\\u1C3B-\\u1C3F\\u1C7E\\u1C7F\\u203C\\u203D\\u2047-\\u2049\\u2E2E\\u2E3C\\u2E41\\u2E4C\\u2E4E\\u2E4F\\u2E53\\u2E54\\u3001\\u3002\\uA4FE\\uA4FF\\uA60D-\\uA60F\\uA6F3-\\uA6F7\\uA876\\uA877\\uA8CE\\uA8CF\\uA92F\\uA9C7-\\uA9C9\\uAA5D-\\uAA5F\\uAADF\\uAAF0\\uAAF1\\uABEB\\uFE50-\\uFE52\\uFE54-\\uFE57\\uFF01\\uFF0C\\uFF0E\\uFF1A\\uFF1B\\uFF1F\\uFF61\\uFF64]");

        // Quotation_Mark
        BINARY_PROPERTY.put("Quotation_Mark", "[\\u0022\\u0027\\u00AB\\u00BB\\u2018-\\u201F\\u2039\\u203A\\u2E42\\u300C-\\u300F\\u301D-\\u301F\\uFE41-\\uFE44\\uFF02\\uFF07\\uFF62\\uFF63]");
        BINARY_PROPERTY.put("QMark", "[\\u0022\\u0027\\u00AB\\u00BB\\u2018-\\u201F\\u2039\\u203A\\u2E42\\u300C-\\u300F\\u301D-\\u301F\\uFE41-\\uFE44\\uFF02\\uFF07\\uFF62\\uFF63]");

        // Bidi_Control
        BINARY_PROPERTY.put("Bidi_Control", "[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
        BINARY_PROPERTY.put("Bidi_C", "[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");

        // Soft_Dotted
        BINARY_PROPERTY.put("Soft_Dotted", "[\\u0069\\u006A\\u012F\\u0249\\u0268\\u029D\\u02B2\\u03F3\\u0456\\u0458\\u1D62\\u1D96\\u1DA4\\u1DA8\\u1E2D\\u1ECB\\u2071\\u2148\\u2149\\u2C7C\\x{1D422}\\x{1D423}\\x{1D456}\\x{1D457}\\x{1D48A}\\x{1D48B}\\x{1D4BE}\\x{1D4BF}\\x{1D4F2}\\x{1D4F3}\\x{1D526}\\x{1D527}\\x{1D55A}\\x{1D55B}\\x{1D58E}\\x{1D58F}\\x{1D5C2}\\x{1D5C3}\\x{1D5F6}\\x{1D5F7}\\x{1D62A}\\x{1D62B}\\x{1D65E}\\x{1D65F}\\x{1D692}\\x{1D693}]");
        BINARY_PROPERTY.put("SD", "[\\u0069\\u006A\\u012F\\u0249\\u0268\\u029D\\u02B2\\u03F3\\u0456\\u0458\\u1D62\\u1D96\\u1DA4\\u1DA8\\u1E2D\\u1ECB\\u2071\\u2148\\u2149\\u2C7C\\x{1D422}\\x{1D423}\\x{1D456}\\x{1D457}\\x{1D48A}\\x{1D48B}\\x{1D4BE}\\x{1D4BF}\\x{1D4F2}\\x{1D4F3}\\x{1D526}\\x{1D527}\\x{1D55A}\\x{1D55B}\\x{1D58E}\\x{1D58F}\\x{1D5C2}\\x{1D5C3}\\x{1D5F6}\\x{1D5F7}\\x{1D62A}\\x{1D62B}\\x{1D65E}\\x{1D65F}\\x{1D692}\\x{1D693}]");

        // Default_Ignorable_Code_Point (simplified)
        BINARY_PROPERTY.put("Default_Ignorable_Code_Point", "[\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\u3164\\uFE00-\\uFE0F\\uFEFF\\uFFA0\\uFFF0-\\uFFF8\\x{1BCA0}-\\x{1BCA3}\\x{1D173}-\\x{1D17A}\\x{E0000}-\\x{E0FFF}]");
        BINARY_PROPERTY.put("DI", "[\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\u3164\\uFE00-\\uFE0F\\uFEFF\\uFFA0\\uFFF0-\\uFFF8\\x{1BCA0}-\\x{1BCA3}\\x{1D173}-\\x{1D17A}\\x{E0000}-\\x{E0FFF}]");

        // Deprecated (very few code points)
        BINARY_PROPERTY.put("Deprecated", "[\\u0149\\u0673\\u0F77\\u0F79\\u17A3\\u17A4\\u206A-\\u206F\\u2329\\u232A\\x{E0001}]");
        BINARY_PROPERTY.put("Dep", "[\\u0149\\u0673\\u0F77\\u0F79\\u17A3\\u17A4\\u206A-\\u206F\\u2329\\u232A\\x{E0001}]");

        // Logical_Order_Exception
        BINARY_PROPERTY.put("Logical_Order_Exception", "[\\u0E40-\\u0E44\\u0EC0-\\u0EC4\\u19B5-\\u19B7\\u19BA\\uAAB5\\uAAB6\\uAAB9\\uAABB\\uAABC]");
        BINARY_PROPERTY.put("LOE", "[\\u0E40-\\u0E44\\u0EC0-\\u0EC4\\u19B5-\\u19B7\\u19BA\\uAAB5\\uAAB6\\uAAB9\\uAABB\\uAABC]");

        // Emoji properties (Unicode 15.0 core ranges)
        BINARY_PROPERTY.put("Emoji", "[\\u0023\\u002A\\u0030-\\u0039\\u00A9\\u00AE\\u203C\\u2049\\u2122\\u2139\\u2194-\\u2199\\u21A9\\u21AA\\u231A\\u231B\\u2328\\u23CF\\u23E9-\\u23F3\\u23F8-\\u23FA\\u24C2\\u25AA\\u25AB\\u25B6\\u25C0\\u25FB-\\u25FE\\u2600-\\u2604\\u260E\\u2611\\u2614\\u2615\\u2618\\u261D\\u2620\\u2622\\u2623\\u2626\\u262A\\u262E\\u262F\\u2638-\\u263A\\u2640\\u2642\\u2648-\\u2653\\u265F\\u2660\\u2663\\u2665\\u2666\\u2668\\u267B\\u267E\\u267F\\u2692-\\u2697\\u2699\\u269B\\u269C\\u26A0\\u26A1\\u26A7\\u26AA\\u26AB\\u26B0\\u26B1\\u26BD\\u26BE\\u26C4\\u26C5\\u26C8\\u26CE\\u26CF\\u26D1\\u26D3\\u26D4\\u26E9\\u26EA\\u26F0-\\u26F5\\u26F7-\\u26FA\\u26FD\\u2702\\u2705\\u2708-\\u270D\\u270F\\u2712\\u2714\\u2716\\u271D\\u2721\\u2728\\u2733\\u2734\\u2744\\u2747\\u274C\\u274E\\u2753-\\u2755\\u2757\\u2763\\u2764\\u2795-\\u2797\\u27A1\\u27B0\\u27BF\\u2934\\u2935\\u2B05-\\u2B07\\u2B1B\\u2B1C\\u2B50\\u2B55\\u3030\\u303D\\u3297\\u3299\\x{1F004}\\x{1F0CF}\\x{1F170}\\x{1F171}\\x{1F17E}\\x{1F17F}\\x{1F18E}\\x{1F191}-\\x{1F19A}\\x{1F1E0}-\\x{1F1FF}\\x{1F201}\\x{1F202}\\x{1F21A}\\x{1F22F}\\x{1F232}-\\x{1F23A}\\x{1F250}\\x{1F251}\\x{1F300}-\\x{1F321}\\x{1F324}-\\x{1F393}\\x{1F396}\\x{1F397}\\x{1F399}-\\x{1F39B}\\x{1F39E}\\x{1F39F}\\x{1F3A0}-\\x{1F3CA}\\x{1F3CF}-\\x{1F3D3}\\x{1F3E0}-\\x{1F3F0}\\x{1F3F3}-\\x{1F3F5}\\x{1F3F7}-\\x{1F4FD}\\x{1F4FF}-\\x{1F53D}\\x{1F549}-\\x{1F54E}\\x{1F550}-\\x{1F567}\\x{1F56F}\\x{1F570}\\x{1F573}-\\x{1F57A}\\x{1F587}\\x{1F58A}-\\x{1F58D}\\x{1F590}\\x{1F595}\\x{1F596}\\x{1F5A4}\\x{1F5A5}\\x{1F5A8}\\x{1F5B1}\\x{1F5B2}\\x{1F5BC}\\x{1F5C2}-\\x{1F5C4}\\x{1F5D1}-\\x{1F5D3}\\x{1F5DC}-\\x{1F5DE}\\x{1F5E1}\\x{1F5E3}\\x{1F5E8}\\x{1F5EF}\\x{1F5F3}\\x{1F5FA}-\\x{1F64F}\\x{1F680}-\\x{1F6C5}\\x{1F6CB}-\\x{1F6D2}\\x{1F6D5}-\\x{1F6D7}\\x{1F6DC}-\\x{1F6E5}\\x{1F6E9}\\x{1F6EB}\\x{1F6EC}\\x{1F6F0}\\x{1F6F3}-\\x{1F6FC}\\x{1F7E0}-\\x{1F7EB}\\x{1F7F0}\\x{1F90C}-\\x{1F93A}\\x{1F93C}-\\x{1F945}\\x{1F947}-\\x{1F9FF}\\x{1FA70}-\\x{1FA7C}\\x{1FA80}-\\x{1FA88}\\x{1FA90}-\\x{1FABD}\\x{1FABF}-\\x{1FAC5}\\x{1FACE}-\\x{1FADB}\\x{1FAE0}-\\x{1FAE8}\\x{1FAF0}-\\x{1FAF8}]");
        BINARY_PROPERTY.put("Emoji_Presentation", "[\\u231A\\u231B\\u23E9-\\u23F3\\u23F8-\\u23FA\\u25FD\\u25FE\\u2614\\u2615\\u2648-\\u2653\\u267F\\u2693\\u26A1\\u26AA\\u26AB\\u26BD\\u26BE\\u26C4\\u26C5\\u26CE\\u26D4\\u26EA\\u26F2\\u26F3\\u26F5\\u26FA\\u26FD\\u2702\\u2705\\u2708-\\u270D\\u270F\\u2712\\u2714\\u2716\\u271D\\u2721\\u2728\\u2733\\u2734\\u2744\\u2747\\u274C\\u274E\\u2753-\\u2755\\u2757\\u2763\\u2764\\u2795-\\u2797\\u27A1\\u27B0\\u27BF\\u2934\\u2935\\u2B05-\\u2B07\\u2B1B\\u2B1C\\u2B50\\u2B55\\u3030\\u303D\\u3297\\u3299\\x{1F004}\\x{1F0CF}\\x{1F18E}\\x{1F191}-\\x{1F19A}\\x{1F1E6}-\\x{1F1FF}\\x{1F201}\\x{1F21A}\\x{1F22F}\\x{1F232}-\\x{1F236}\\x{1F238}-\\x{1F23A}\\x{1F250}\\x{1F251}\\x{1F300}-\\x{1F320}\\x{1F32D}-\\x{1F335}\\x{1F337}-\\x{1F37C}\\x{1F37E}-\\x{1F393}\\x{1F3A0}-\\x{1F3CA}\\x{1F3CF}-\\x{1F3D3}\\x{1F3E0}-\\x{1F3F0}\\x{1F3F4}\\x{1F3F8}-\\x{1F43E}\\x{1F440}\\x{1F442}-\\x{1F4FC}\\x{1F4FF}-\\x{1F53D}\\x{1F54B}-\\x{1F54E}\\x{1F550}-\\x{1F567}\\x{1F57A}\\x{1F595}\\x{1F596}\\x{1F5A4}\\x{1F5FB}-\\x{1F64F}\\x{1F680}-\\x{1F6C5}\\x{1F6CC}\\x{1F6D0}-\\x{1F6D2}\\x{1F6D5}-\\x{1F6D7}\\x{1F6DC}-\\x{1F6DF}\\x{1F6EB}\\x{1F6EC}\\x{1F6F4}-\\x{1F6FC}\\x{1F7E0}-\\x{1F7EB}\\x{1F7F0}\\x{1F90C}-\\x{1F93A}\\x{1F93C}-\\x{1F945}\\x{1F947}-\\x{1F9FF}\\x{1FA70}-\\x{1FA7C}\\x{1FA80}-\\x{1FA88}\\x{1FA90}-\\x{1FABD}\\x{1FABF}-\\x{1FAC5}\\x{1FACE}-\\x{1FADB}\\x{1FAE0}-\\x{1FAE8}\\x{1FAF0}-\\x{1FAF8}]");
        BINARY_PROPERTY.put("Emoji_Modifier", "[\\x{1F3FB}-\\x{1F3FF}]");
        BINARY_PROPERTY.put("Emoji_Modifier_Base", "[\\u261D\\u26F9\\u270A-\\u270D\\x{1F385}\\x{1F3C2}-\\x{1F3C4}\\x{1F3C7}\\x{1F3CA}-\\x{1F3CC}\\x{1F442}\\x{1F443}\\x{1F446}-\\x{1F450}\\x{1F466}-\\x{1F478}\\x{1F47C}\\x{1F481}-\\x{1F483}\\x{1F485}-\\x{1F487}\\x{1F4AA}\\x{1F574}\\x{1F575}\\x{1F57A}\\x{1F590}\\x{1F595}\\x{1F596}\\x{1F645}-\\x{1F647}\\x{1F64B}-\\x{1F64F}\\x{1F6A3}\\x{1F6B4}-\\x{1F6B6}\\x{1F6C0}\\x{1F6CC}\\x{1F90C}\\x{1F90F}\\x{1F918}-\\x{1F91F}\\x{1F926}\\x{1F930}-\\x{1F939}\\x{1F93C}-\\x{1F93E}\\x{1F9B5}\\x{1F9B6}\\x{1F9B8}\\x{1F9B9}\\x{1F9BB}\\x{1F9CD}-\\x{1F9CF}\\x{1F9D1}-\\x{1F9DD}\\x{1FAC3}-\\x{1FAC5}\\x{1FAF0}-\\x{1FAF8}]");
        BINARY_PROPERTY.put("Emoji_Component", "[\\u0023\\u002A\\u0030-\\u0039\\u200D\\u20E3\\uFE0F\\x{1F1E6}-\\x{1F1FF}\\x{1F3FB}-\\x{1F3FF}\\x{1F9B0}-\\x{1F9B3}\\x{E0020}-\\x{E007F}]");
        BINARY_PROPERTY.put("Extended_Pictographic", "[\\u00A9\\u00AE\\u203C\\u2049\\u2122\\u2139\\u2194-\\u2199\\u21A9\\u21AA\\u231A\\u231B\\u2328\\u23CF\\u23E9-\\u23F3\\u23F8-\\u23FA\\u24C2\\u25AA\\u25AB\\u25B6\\u25C0\\u25FB-\\u25FE\\u2600-\\u2605\\u2607-\\u2612\\u2614-\\u2685\\u2690-\\u2705\\u2708-\\u270D\\u270F\\u2712\\u2714\\u2716\\u271D\\u2721\\u2728\\u2733\\u2734\\u2744\\u2747\\u274C\\u274E\\u2753-\\u2755\\u2757\\u2763-\\u2767\\u2795-\\u2797\\u27A1\\u27B0\\u27BF\\u2934\\u2935\\u2B05-\\u2B07\\u2B1B\\u2B1C\\u2B50\\u2B55\\u3030\\u303D\\u3297\\u3299\\x{1F000}-\\x{1F0FF}\\x{1F10D}-\\x{1F10F}\\x{1F12F}\\x{1F16C}-\\x{1F171}\\x{1F17E}\\x{1F17F}\\x{1F18E}\\x{1F191}-\\x{1F19A}\\x{1F1AD}-\\x{1F1FF}\\x{1F201}\\x{1F202}\\x{1F21A}\\x{1F22F}\\x{1F232}-\\x{1F23A}\\x{1F250}\\x{1F251}\\x{1F300}-\\x{1FFFD}]");
    }

    /**
     * Translate an ECMAScript Unicode property escape to a Java regex fragment.
     * Returns null if the property is not recognized.
     */
    public static String translate(String propertyExpr, boolean negated, boolean insideCharClass) {
        // Ground-truth codepoint data (extracted from test262's own
        // generated property-escapes suite) takes precedence over the
        // hand-built tables below: it's exact for every alias test262
        // exercises, independent of whichever Unicode version the running
        // JVM's own java.util.regex \p{IsXxx} support happens to bundle.
        String result = translateFromDataTable(propertyExpr, negated);

        if (result == null) {
            int eqIdx = propertyExpr.indexOf('=');
            if (eqIdx >= 0) {
                String prop = propertyExpr.substring(0, eqIdx).trim();
                String value = propertyExpr.substring(eqIdx + 1).trim();
                result = translatePropertyValue(prop, value, negated);
            } else {
                result = translateLoneValue(propertyExpr, negated);
            }
        }

        if (result == null) {
            return null;
        }

        // If inside a character class and the result is itself a character class,
        // embed it properly. Per spec, EVERY element of a JS character class
        // (positive or negated) contributes to the class's UNION, never an
        // intersection - Java's nested-class syntax [abc[def]] is already a
        // union with no extra operator needed (confirmed empirically: only an
        // explicit "&&" turns it into an intersection), so a negated [^...]
        // result can be embedded completely unchanged as a nested class.
        // Only a POSITIVE class needs its own outer brackets stripped, since
        // its members should merge directly into the enclosing class's list
        // rather than nest (equivalent either way, but matches prior style).
        if (insideCharClass && result.startsWith("[") && result.endsWith("]")) {
            if (result.startsWith("[^")) {
                return result;
            }
            return result.substring(1, result.length() - 1);
        }

        return result;
    }

    private static String translateFromDataTable(String propertyExpr, boolean negated) {
        int[] ranges = UnicodePropertyData.getRanges(propertyExpr);
        if (ranges == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(negated ? "[^" : "[");
        for (int i = 0; i < ranges.length; i += 2) {
            int start = ranges[i];
            int end = ranges[i + 1];
            sb.append("\\x{").append(Integer.toHexString(start)).append('}');
            if (end != start) {
                sb.append('-').append("\\x{").append(Integer.toHexString(end)).append('}');
            }
        }
        return sb.append(']').toString();
    }

    private static String translateLoneValue(String value, boolean negated) {
        // Try General Category first
        String gc = GENERAL_CATEGORY.get(value);
        if (gc != null) {
            return negated ? "\\P{" + gc + "}" : "\\p{" + gc + "}";
        }

        // Try Binary property
        String binary = BINARY_PROPERTY.get(value);
        if (binary != null) {
            return applyNegation(binary, negated);
        }

        // Try as Script name
        String script = SCRIPT_NAMES.get(value);
        if (script != null) {
            return negated ? "\\P{Is" + script + "}" : "\\p{Is" + script + "}";
        }

        return null;
    }

    private static String translatePropertyValue(String property, String value, boolean negated) {
        switch (property) {
            case "General_Category":
            case "gc":
                String gc = GENERAL_CATEGORY.get(value);
                if (gc != null) {
                    return negated ? "\\P{" + gc + "}" : "\\p{" + gc + "}";
                }
                break;
            case "Script":
            case "sc":
            case "Script_Extensions":
            case "scx":
                String script = SCRIPT_NAMES.get(value);
                if (script != null) {
                    return negated ? "\\P{Is" + script + "}" : "\\p{Is" + script + "}";
                }
                break;
        }
        return null;
    }

    private static String applyNegation(String fragment, boolean negated) {
        if (!negated) {
            return fragment;
        }
        if (fragment.startsWith("\\p{")) {
            return "\\P{" + fragment.substring(3);
        }
        if (fragment.startsWith("\\P{")) {
            return "\\p{" + fragment.substring(3);
        }
        if (fragment.startsWith("[^")) {
            return "[" + fragment.substring(2);
        }
        if (fragment.startsWith("[")) {
            return "[^" + fragment.substring(1);
        }
        return "[^" + fragment + "]";
    }
}
