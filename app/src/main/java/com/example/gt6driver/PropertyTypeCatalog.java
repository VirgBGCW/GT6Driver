package com.example.gt6driver;

import com.example.gt6driver.model.PropertyItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PropertyTypeCatalog {
    private PropertyTypeCatalog() {}

    private static final List<Option> OPTIONS = buildOptions();
    private static final List<FormRow> FORM_ROWS = buildFormRows();

    public static List<Option> optionsSorted() {
        List<Option> list = new ArrayList<>(OPTIONS);
        Collections.sort(list, new Comparator<Option>() {
            @Override
            public int compare(Option a, Option b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return list;
    }

    public static int resolveIdByName(String name) {
        String target = safe(name);
        for (Option option : OPTIONS) {
            if (option.name.equalsIgnoreCase(target)) {
                return option.id;
            }
        }
        return -1;
    }

    public static List<FormRow> formRows() {
        return new ArrayList<>(FORM_ROWS);
    }

    public static String formRowFor(PropertyItem item) {
        if (item == null) return null;

        String row = formRowForName(item.propertyType);
        if (row != null) return row;

        row = formRowForName(item.propertyDescription);
        if (row != null) return row;

        Integer id = asInt(item.propertyItemTypeId);
        if (id != null) {
            return formRowForId(id);
        }

        return null;
    }

    public static boolean isNoProperty(PropertyItem item) {
        if (item == null) return false;
        return "noproperty".equals(normalize(item.propertyType))
                || "noproperty".equals(normalize(item.propertyDescription))
                || Integer.valueOf(16).equals(asInt(item.propertyItemTypeId));
    }

    private static String formRowForName(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return null;

        switch (normalized) {
            case "award":
            case "awards":
            case "awardtrophy":
            case "awardtrophies":
            case "trophy":
            case "trophies":
                return "Award / Trophy";
            case "bindersofdocuments":
            case "bindersofdocs":
                return "Binder(s) of Docs";
            case "broadcastsheet":
                return "Broadcast Sheet";
            case "buildsheet":
                return "Build Sheet";
            case "carcover":
                return "Car Cover";
            case "cleaningsupplies":
                return "Cleaning Supplies";
            case "dealerinvoices":
                return "Dealer Invoices";
            case "licenseplate":
                return "License Plate";
            case "manual":
            case "manuals":
            case "manualsshopparts":
                return "Manuals (shop/parts)";
            case "martireport":
                return "Marti Report";
            case "miscdocuments":
                return "Misc Documents";
            case "ownersmanual":
                return "Owner's Manual";
            case "protectoplate":
                return "Protect-O-Plate";
            case "registration":
                return "Registration";
            case "remote":
                return "Remote";
            case "servicerecords":
                return "Service Records";
            case "sparemiscparts":
                return "Spare / Misc Parts";
            case "stereoradiomanual":
                return "Stereo / Radio Manual";
            case "storyboard":
                return "Story Board";
            case "tanksticker":
                return "Tank Sticker";
            case "title":
            case "copyoftitle":
                return "Title";
            case "toolkit":
                return "Tool Kit";
            case "top":
            case "topttops":
            case "toptops":
            case "ttops":
                return "Top";
            case "warrantybook":
                return "Warranty Book";
            case "wheellock":
            case "wheellocks":
                return "Wheel Lock";
            case "windowsticker":
                return "Window Sticker";
            default:
                return null;
        }
    }

    private static String formRowForId(int id) {
        switch (id) {
            case 1:
                return "Award / Trophy";
            case 2:
                return "Binder(s) of Docs";
            case 3:
                return "Broadcast Sheet";
            case 4:
                return "Build Sheet";
            case 5:
                return "Car Cover";
            case 7:
                return "Cleaning Supplies";
            case 8:
                return "Dealer Invoices";
            case 10:
                return "License Plate";
            case 12:
                return "Manuals (shop/parts)";
            case 13:
                return "Marti Report";
            case 14:
                return "Misc Documents";
            case 18:
                return "Owner's Manual";
            case 21:
                return "Protect-O-Plate";
            case 22:
                return "Remote";
            case 23:
                return "Service Records";
            case 24:
                return "Spare / Misc Parts";
            case 26:
                return "Stereo / Radio Manual";
            case 27:
                return "Story Board";
            case 28:
                return "Tank Sticker";
            case 31:
                return "Tool Kit";
            case 32:
                return "Top";
            case 33:
                return "Warranty Book";
            case 34:
                return "Wheel Lock";
            case 35:
                return "Window Sticker";
            case 53:
                return "Title";
            case 68:
                return "Title";
            case 71:
                return "Registration";
            default:
                return null;
        }
    }

    private static List<Option> buildOptions() {
        List<Option> list = new ArrayList<>();
        list.add(new Option(1, "Award / Trophy"));
        list.add(new Option(2, "Binder(s) of Documents"));
        list.add(new Option(3, "Broadcast Sheet"));
        list.add(new Option(4, "Build Sheet"));
        list.add(new Option(5, "Car Cover"));
        list.add(new Option(6, "Certificate of Authenticity"));
        list.add(new Option(7, "Cleaning Supplies"));
        list.add(new Option(8, "Dealer Invoices"));
        list.add(new Option(9, "EV/Charging Cables"));
        list.add(new Option(10, "License Plate"));
        list.add(new Option(11, "Magazine"));
        list.add(new Option(12, "Manuals (shop/parts)"));
        list.add(new Option(13, "Marti Report"));
        list.add(new Option(14, "Misc Documents"));
        list.add(new Option(15, "NCRS Documents"));
        list.add(new Option(16, "No Property"));
        list.add(new Option(17, "Other"));
        list.add(new Option(18, "Owner's Manual"));
        list.add(new Option(19, "Photos"));
        list.add(new Option(20, "PHS Docs"));
        list.add(new Option(21, "Protect-O-Plate"));
        list.add(new Option(22, "Remote"));
        list.add(new Option(23, "Service Records"));
        list.add(new Option(24, "Spare Misc Parts"));
        list.add(new Option(25, "Spare Tire"));
        list.add(new Option(26, "Stereo/Radio Manual"));
        list.add(new Option(27, "Story Board"));
        list.add(new Option(28, "Tank Sticker"));
        list.add(new Option(29, "Tires"));
        list.add(new Option(30, "Manual(s)"));
        list.add(new Option(31, "Tool Kit"));
        list.add(new Option(32, "Top/T-Tops"));
        list.add(new Option(33, "Warranty Book"));
        list.add(new Option(34, "Wheel Lock(s)"));
        list.add(new Option(35, "Window Sticker"));
        list.add(new Option(36, "Jack"));
        list.add(new Option(37, "Engine Tuner"));
        list.add(new Option(38, "Trickle Charger / Battery Tender"));
        list.add(new Option(39, "Battery Charger"));
        list.add(new Option(40, "Receipts"));
        list.add(new Option(41, "Key/Key Fobs"));
        list.add(new Option(42, "Literature"));
        list.add(new Option(43, "Books"));
        list.add(new Option(44, "Bill of Sale"));
        list.add(new Option(45, "Floor Mats"));
        list.add(new Option(46, "Air Pumps / tire inflator"));
        list.add(new Option(47, "Brochure"));
        list.add(new Option(48, "Luggage"));
        list.add(new Option(49, "Posters"));
        list.add(new Option(50, "manufacturer's literature."));
        list.add(new Option(51, "Windows"));
        list.add(new Option(52, "Carfax"));
        list.add(new Option(53, "Copy of title"));
        list.add(new Option(54, "First-aid kit"));
        list.add(new Option(55, "Diecast Replica"));
        list.add(new Option(56, "Winch / Winch control"));
        list.add(new Option(57, "Fire Extinguisher"));
        list.add(new Option(58, "Boot Cover"));
        list.add(new Option(59, "Navigation CD"));
        list.add(new Option(60, "Touch Up Paint"));
        list.add(new Option(61, "Umbrella (s)"));
        list.add(new Option(62, "Hard top stand"));
        list.add(new Option(63, "Original Radio"));
        list.add(new Option(64, "Tow Hook"));
        list.add(new Option(65, "Eleanor certification"));
        list.add(new Option(66, "Movie Poster"));
        list.add(new Option(67, "Sales invoice"));
        list.add(new Option(68, "Title"));
        list.add(new Option(69, "Signatures"));
        list.add(new Option(70, "Engine"));
        list.add(new Option(71, "Registration"));
        list.add(new Option(72, "Insurance Card"));
        list.add(new Option(73, "Dyno sheet"));
        return list;
    }

    private static List<FormRow> buildFormRows() {
        List<FormRow> rows = new ArrayList<>();
        rows.add(new FormRow(1, "Award / Trophy"));
        rows.add(new FormRow(2, "Binder(s) of Docs"));
        rows.add(new FormRow(3, "Broadcast Sheet"));
        rows.add(new FormRow(4, "Build Sheet"));
        rows.add(new FormRow(5, "Car Cover"));
        rows.add(new FormRow(7, "Cleaning Supplies"));
        rows.add(new FormRow(8, "Dealer Invoices"));
        rows.add(new FormRow(10, "License Plate"));
        rows.add(new FormRow(12, "Manuals (shop/parts)"));
        rows.add(new FormRow(13, "Marti Report"));
        rows.add(new FormRow(14, "Misc Documents"));
        rows.add(new FormRow(18, "Owner's Manual"));
        rows.add(new FormRow(21, "Protect-O-Plate"));
        rows.add(new FormRow(71, "Registration"));
        rows.add(new FormRow(22, "Remote"));
        rows.add(new FormRow(23, "Service Records"));
        rows.add(new FormRow(24, "Spare / Misc Parts"));
        rows.add(new FormRow(26, "Stereo / Radio Manual"));
        rows.add(new FormRow(27, "Story Board"));
        rows.add(new FormRow(28, "Tank Sticker"));
        rows.add(new FormRow(68, "Title"));
        rows.add(new FormRow(31, "Tool Kit"));
        rows.add(new FormRow(32, "Top"));
        rows.add(new FormRow(33, "Warranty Book"));
        rows.add(new FormRow(34, "Wheel Lock"));
        rows.add(new FormRow(35, "Window Sticker"));
        return rows;
    }

    private static Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Option {
        public final int id;
        public final String name;

        public Option(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class FormRow {
        public final Integer propertyTypeId;
        public final String label;

        private FormRow(Integer propertyTypeId, String label) {
            this.propertyTypeId = propertyTypeId;
            this.label = label;
        }
    }
}
