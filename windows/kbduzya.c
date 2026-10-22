/*
 * Alifbo: раскладка Windows для нового узбекского алфавита (закон O‘RQ-1184 от 21.10.2026).
 *
 * Основа — американская раскладка. Пять клавиш справа отданы новым буквам, вытесненные
 * символы переехали на AltGr (правый Alt):
 *
 *   [  ->  ğ Ğ   (AltGr: [ {)        ;  ->  ş Ş   (AltGr: ; :)
 *   ]  ->  ö Ö   (AltGr: ] })        '  ->  ʼ "   (AltGr: ')
 *   \  ->  ç Ç   (AltGr: \ |)
 *
 * Это только таблица клавиш: в DLL нет кода, который видит или передаёт текст.
 * Проверка: windows/test-layout.ps1.
 */

#include <windows.h>
#include "kbd.h"

#if defined(_M_IA64)
#pragma section(".data")
#define ALLOC_SECTION_LDATA __declspec(allocate(".data"))
#else
#pragma data_seg(".data")
#define ALLOC_SECTION_LDATA
#endif

/* Скан-код -> виртуальная клавиша: как в стандартной раскладке (KBD_TYPE 4). */
static ALLOC_SECTION_LDATA USHORT ausVK[] = {
    T00, T01, T02, T03, T04, T05, T06, T07,
    T08, T09, T0A, T0B, T0C, T0D, T0E, T0F,
    T10, T11, T12, T13, T14, T15, T16, T17,
    T18, T19, T1A, T1B, T1C, T1D, T1E, T1F,
    T20, T21, T22, T23, T24, T25, T26, T27,
    T28, T29, T2A, T2B, T2C, T2D, T2E, T2F,
    T30, T31, T32, T33, T34, T35,
    T36 | KBDEXT,                   /* правый Shift */
    T37 | KBDMULTIVK,
    T38, T39, T3A, T3B, T3C, T3D, T3E,
    T3F, T40, T41, T42, T43, T44,
    T45 | KBDEXT | KBDMULTIVK,      /* NumLock / Pause */
    T46 | KBDMULTIVK,
    T47 | KBDNUMPAD | KBDSPECIAL,
    T48 | KBDNUMPAD | KBDSPECIAL,
    T49 | KBDNUMPAD | KBDSPECIAL,
    T4A,
    T4B | KBDNUMPAD | KBDSPECIAL,
    T4C | KBDNUMPAD | KBDSPECIAL,
    T4D | KBDNUMPAD | KBDSPECIAL,
    T4E,
    T4F | KBDNUMPAD | KBDSPECIAL,
    T50 | KBDNUMPAD | KBDSPECIAL,
    T51 | KBDNUMPAD | KBDSPECIAL,
    T52 | KBDNUMPAD | KBDSPECIAL,
    T53 | KBDNUMPAD | KBDSPECIAL,
    T54, T55, T56, T57, T58, T59, T5A, T5B,
    T5C, T5D, T5E, T5F, T60, T61, T62, T63,
    T64, T65, T66, T67, T68, T69, T6A, T6B,
    T6C, T6D, T6E, T6F, T70, T71, T72, T73,
    T74, T75, T76, T77, T78, T79, T7A, T7B,
    T7C, T7D, T7E
};

static ALLOC_SECTION_LDATA VSC_VK aE0VscToVk[] = {
    { 0x10, X10 | KBDEXT }, { 0x19, X19 | KBDEXT }, { 0x1D, X1D | KBDEXT },
    { 0x20, X20 | KBDEXT }, { 0x21, X21 | KBDEXT }, { 0x22, X22 | KBDEXT },
    { 0x24, X24 | KBDEXT }, { 0x2E, X2E | KBDEXT }, { 0x30, X30 | KBDEXT },
    { 0x32, X32 | KBDEXT }, { 0x35, X35 | KBDEXT }, { 0x37, X37 | KBDEXT },
    { 0x38, X38 | KBDEXT }, { 0x47, X47 | KBDEXT }, { 0x48, X48 | KBDEXT },
    { 0x49, X49 | KBDEXT }, { 0x4B, X4B | KBDEXT }, { 0x4D, X4D | KBDEXT },
    { 0x4F, X4F | KBDEXT }, { 0x50, X50 | KBDEXT }, { 0x51, X51 | KBDEXT },
    { 0x52, X52 | KBDEXT }, { 0x53, X53 | KBDEXT }, { 0x5B, X5B | KBDEXT },
    { 0x5C, X5C | KBDEXT }, { 0x5D, X5D | KBDEXT }, { 0x5F, X5F | KBDEXT },
    { 0x65, X65 | KBDEXT }, { 0x66, X66 | KBDEXT }, { 0x67, X67 | KBDEXT },
    { 0x68, X68 | KBDEXT }, { 0x69, X69 | KBDEXT }, { 0x6A, X6A | KBDEXT },
    { 0x6B, X6B | KBDEXT }, { 0x6C, X6C | KBDEXT }, { 0x6D, X6D | KBDEXT },
    { 0x1C, X1C | KBDEXT }, { 0x46, X46 | KBDEXT },
    { 0, 0 }
};

static ALLOC_SECTION_LDATA VSC_VK aE1VscToVk[] = {
    { 0x1D, Y1D },
    { 0, 0 }
};

/* Shift, Ctrl, Alt. AltGr = Ctrl + Alt. */
static ALLOC_SECTION_LDATA VK_TO_BIT aVkToBits[] = {
    { VK_SHIFT, KBDSHIFT },
    { VK_CONTROL, KBDCTRL },
    { VK_MENU, KBDALT },
    { 0, 0 }
};

/* Номер столбца в таблице символов для каждого сочетания модификаторов. */
static ALLOC_SECTION_LDATA MODIFIERS CharModifiers = {
    &aVkToBits[0],
    7,
    {
        0,              /* —                 */
        1,              /* Shift             */
        2,              /* Ctrl              */
        SHFT_INVALID,   /* Shift + Ctrl      */
        SHFT_INVALID,   /* Alt               */
        SHFT_INVALID,   /* Shift + Alt       */
        3,              /* AltGr             */
        4               /* Shift + AltGr     */
    }
};

#define NONE WCH_NONE

/* Столбцы: обычный, Shift, Ctrl, AltGr, Shift+AltGr. */
static ALLOC_SECTION_LDATA VK_TO_WCHARS5 aVkToWch5[] = {
    /* Новые буквы */
    { VK_OEM_4, CAPLOK, { 0x011F, 0x011E, 0x001B, L'[', L'{' } },   /* ğ Ğ */
    { VK_OEM_6, CAPLOK, { 0x00F6, 0x00D6, 0x001D, L']', L'}' } },   /* ö Ö */
    { VK_OEM_5, CAPLOK, { 0x00E7, 0x00C7, 0x001C, L'\\', L'|' } },  /* ç Ç */
    { VK_OEM_1, CAPLOK, { 0x015F, 0x015E, NONE, L';', L':' } },     /* ş Ş */
    { VK_OEM_7, 0,      { 0x02BC, L'"', NONE, L'\'', NONE } },      /* тутук белгиси ʼ */

    /* Цифры */
    { '1', 0, { L'1', L'!', NONE, NONE, NONE } },
    { '2', 0, { L'2', L'@', NONE, NONE, NONE } },
    { '3', 0, { L'3', L'#', NONE, NONE, NONE } },
    { '4', 0, { L'4', L'$', NONE, NONE, NONE } },
    { '5', 0, { L'5', L'%', NONE, NONE, NONE } },
    { '6', 0, { L'6', L'^', NONE, NONE, NONE } },
    { '7', 0, { L'7', L'&', NONE, NONE, NONE } },
    { '8', 0, { L'8', L'*', NONE, NONE, NONE } },
    { '9', 0, { L'9', L'(', NONE, NONE, NONE } },
    { '0', 0, { L'0', L')', NONE, NONE, NONE } },

    /* Буквы: Ctrl даёт управляющий код, как в английской раскладке */
    { 'A', CAPLOK, { L'a', L'A', 0x01, NONE, NONE } },
    { 'B', CAPLOK, { L'b', L'B', 0x02, NONE, NONE } },
    { 'C', CAPLOK, { L'c', L'C', 0x03, NONE, NONE } },
    { 'D', CAPLOK, { L'd', L'D', 0x04, NONE, NONE } },
    { 'E', CAPLOK, { L'e', L'E', 0x05, NONE, NONE } },
    { 'F', CAPLOK, { L'f', L'F', 0x06, NONE, NONE } },
    { 'G', CAPLOK, { L'g', L'G', 0x07, NONE, NONE } },
    { 'H', CAPLOK, { L'h', L'H', 0x08, NONE, NONE } },
    { 'I', CAPLOK, { L'i', L'I', 0x09, NONE, NONE } },
    { 'J', CAPLOK, { L'j', L'J', 0x0A, NONE, NONE } },
    { 'K', CAPLOK, { L'k', L'K', 0x0B, NONE, NONE } },
    { 'L', CAPLOK, { L'l', L'L', 0x0C, NONE, NONE } },
    { 'M', CAPLOK, { L'm', L'M', 0x0D, NONE, NONE } },
    { 'N', CAPLOK, { L'n', L'N', 0x0E, NONE, NONE } },
    { 'O', CAPLOK, { L'o', L'O', 0x0F, NONE, NONE } },
    { 'P', CAPLOK, { L'p', L'P', 0x10, NONE, NONE } },
    { 'Q', CAPLOK, { L'q', L'Q', 0x11, NONE, NONE } },
    { 'R', CAPLOK, { L'r', L'R', 0x12, NONE, NONE } },
    { 'S', CAPLOK, { L's', L'S', 0x13, NONE, NONE } },
    { 'T', CAPLOK, { L't', L'T', 0x14, NONE, NONE } },
    { 'U', CAPLOK, { L'u', L'U', 0x15, NONE, NONE } },
    { 'V', CAPLOK, { L'v', L'V', 0x16, NONE, NONE } },
    { 'W', CAPLOK, { L'w', L'W', 0x17, NONE, NONE } },
    { 'X', CAPLOK, { L'x', L'X', 0x18, NONE, NONE } },
    { 'Y', CAPLOK, { L'y', L'Y', 0x19, NONE, NONE } },
    { 'Z', CAPLOK, { L'z', L'Z', 0x1A, NONE, NONE } },

    /* Знаки как в английской раскладке */
    { VK_OEM_3,      0, { L'`', L'~', NONE, NONE, NONE } },
    { VK_OEM_MINUS,  0, { L'-', L'_', NONE, NONE, NONE } },
    { VK_OEM_PLUS,   0, { L'=', L'+', NONE, NONE, NONE } },
    { VK_OEM_COMMA,  0, { L',', L'<', NONE, NONE, NONE } },
    { VK_OEM_PERIOD, 0, { L'.', L'>', NONE, NONE, NONE } },
    { VK_OEM_2,      0, { L'/', L'?', NONE, NONE, NONE } },
    { VK_OEM_102,    0, { L'\\', L'|', 0x001C, NONE, NONE } },  /* лишняя клавиша на европейских клавиатурах */

    /* Служебные клавиши */
    { VK_TAB,      0, { L'\t', L'\t', NONE, NONE, NONE } },
    { VK_BACK,     0, { L'\b', L'\b', 0x007F, NONE, NONE } },
    { VK_ESCAPE,   0, { 0x001B, 0x001B, 0x001B, NONE, NONE } },
    { VK_RETURN,   0, { L'\r', L'\r', L'\n', NONE, NONE } },
    { VK_SPACE,    0, { L' ', L' ', L' ', NONE, NONE } },
    { VK_CANCEL,   0, { 0x0003, 0x0003, 0x0003, NONE, NONE } },
    { VK_ADD,      0, { L'+', L'+', NONE, NONE, NONE } },
    { VK_SUBTRACT, 0, { L'-', L'-', NONE, NONE, NONE } },
    { VK_MULTIPLY, 0, { L'*', L'*', NONE, NONE, NONE } },
    { VK_DIVIDE,   0, { L'/', L'/', NONE, NONE, NONE } },
    { VK_DECIMAL,  0, { L'.', L'.', NONE, NONE, NONE } },
    { 0, 0, { 0, 0, 0, 0, 0 } }
};

/* Цифры цифрового блока идут отдельной таблицей и последними, как в системных раскладках. */
static ALLOC_SECTION_LDATA VK_TO_WCHARS1 aVkToWch1[] = {
    { VK_NUMPAD0, 0, { L'0' } }, { VK_NUMPAD1, 0, { L'1' } }, { VK_NUMPAD2, 0, { L'2' } },
    { VK_NUMPAD3, 0, { L'3' } }, { VK_NUMPAD4, 0, { L'4' } }, { VK_NUMPAD5, 0, { L'5' } },
    { VK_NUMPAD6, 0, { L'6' } }, { VK_NUMPAD7, 0, { L'7' } }, { VK_NUMPAD8, 0, { L'8' } },
    { VK_NUMPAD9, 0, { L'9' } },
    { 0, 0, { 0 } }
};

static ALLOC_SECTION_LDATA VK_TO_WCHAR_TABLE aVkToWcharTable[] = {
    { (PVK_TO_WCHARS1)aVkToWch5, 5, sizeof(aVkToWch5[0]) },
    { (PVK_TO_WCHARS1)aVkToWch1, 1, sizeof(aVkToWch1[0]) },
    { NULL, 0, 0 }
};

static ALLOC_SECTION_LDATA VSC_LPWSTR aKeyNames[] = {
    { 0x01, L"Esc" }, { 0x0E, L"Backspace" }, { 0x0F, L"Tab" }, { 0x1C, L"Enter" },
    { 0x1D, L"Ctrl" }, { 0x2A, L"Shift" }, { 0x36, L"Right Shift" }, { 0x37, L"Num *" },
    { 0x38, L"Alt" }, { 0x39, L"Space" }, { 0x3A, L"Caps Lock" },
    { 0x3B, L"F1" }, { 0x3C, L"F2" }, { 0x3D, L"F3" }, { 0x3E, L"F4" }, { 0x3F, L"F5" },
    { 0x40, L"F6" }, { 0x41, L"F7" }, { 0x42, L"F8" }, { 0x43, L"F9" }, { 0x44, L"F10" },
    { 0x45, L"Pause" }, { 0x46, L"Scroll Lock" },
    { 0x47, L"Num 7" }, { 0x48, L"Num 8" }, { 0x49, L"Num 9" }, { 0x4A, L"Num -" },
    { 0x4B, L"Num 4" }, { 0x4C, L"Num 5" }, { 0x4D, L"Num 6" }, { 0x4E, L"Num +" },
    { 0x4F, L"Num 1" }, { 0x50, L"Num 2" }, { 0x51, L"Num 3" }, { 0x52, L"Num 0" },
    { 0x53, L"Num Del" }, { 0x54, L"Sys Req" }, { 0x57, L"F11" }, { 0x58, L"F12" },
    { 0x7C, L"F13" }, { 0x7D, L"F14" }, { 0x7E, L"F15" }, { 0x7F, L"F16" },
    { 0x80, L"F17" }, { 0x81, L"F18" }, { 0x82, L"F19" }, { 0x83, L"F20" },
    { 0x84, L"F21" }, { 0x85, L"F22" }, { 0x86, L"F23" }, { 0x87, L"F24" },
    { 0, NULL }
};

static ALLOC_SECTION_LDATA VSC_LPWSTR aKeyNamesExt[] = {
    { 0x1C, L"Num Enter" }, { 0x1D, L"Right Ctrl" }, { 0x35, L"Num /" },
    { 0x37, L"Prnt Scrn" }, { 0x38, L"Right Alt" }, { 0x45, L"Num Lock" },
    { 0x46, L"Break" }, { 0x47, L"Home" }, { 0x48, L"Up" }, { 0x49, L"Page Up" },
    { 0x4B, L"Left" }, { 0x4D, L"Right" }, { 0x4F, L"End" }, { 0x50, L"Down" },
    { 0x51, L"Page Down" }, { 0x52, L"Insert" }, { 0x53, L"Delete" },
    { 0x54, L"<00>" }, { 0x56, L"Help" }, { 0x5B, L"Left Windows" },
    { 0x5C, L"Right Windows" }, { 0x5D, L"Application" },
    { 0, NULL }
};

static ALLOC_SECTION_LDATA KBDTABLES KbdTables = {
    .pCharModifiers = &CharModifiers,
    .pVkToWcharTable = aVkToWcharTable,
    .pDeadKey = NULL,
    .pKeyNames = aKeyNames,
    .pKeyNamesExt = aKeyNamesExt,
    .pKeyNamesDead = NULL,
    .pusVSCtoVK = ausVK,
    .bMaxVSCtoVK = sizeof(ausVK) / sizeof(ausVK[0]),
    .pVSCtoVK_E0 = aE0VscToVk,
    .pVSCtoVK_E1 = aE1VscToVk,
    .fLocaleFlags = MAKELONG(KLLF_ALTGR, KBD_VERSION),
    .nLgMax = 0,
    .cbLgEntry = 0,
    .pLigature = NULL,
};

PKBDTABLES KbdLayerDescriptor(VOID)
{
    return &KbdTables;
}
