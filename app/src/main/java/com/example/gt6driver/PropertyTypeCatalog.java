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
                || Integer.valueOf(17).equals(asInt(item.propertyItemTypeId));
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
            case 11:
                return "License Plate";
            case 13:
                return "Manuals (shop/parts)";
            case 14:
                return "Marti Report";
            case 15:
                return "Misc Documents";
            case 16:
                return "Title";
            case 19:
                return "Owner's Manual";
            case 22:
                return "Protect-O-Plate";
            case 23:
                return "Remote";
            case 24:
                return "Service Records";
            case 25:
                return "Spare / Misc Parts";
            case 27:
                return "Stereo / Radio Manual";
            case 28:
                return "Story Board";
            case 29:
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
        list.add(new Option(10, "Spare Keys/Fobs"));
        list.add(new Option(11, "License Plate"));
        list.add(new Option(12, "Magazine"));
        list.add(new Option(13, "Manuals (shop/parts)"));
        list.add(new Option(14, "Marti Report"));
        list.add(new Option(15, "Misc Documents"));
        list.add(new Option(16, "NCRS Documents"));
        list.add(new Option(16, "Title"));
        list.add(new Option(17, "No Property"));
        list.add(new Option(18, "Other"));
        list.add(new Option(19, "Owner's Manual"));
        list.add(new Option(20, "Photos"));
        list.add(new Option(21, "PHS Docs"));
        list.add(new Option(22, "Protect-O-Plate"));
        list.add(new Option(23, "Remote"));
        list.add(new Option(24, "Service Records"));
        list.add(new Option(25, "Spare/Misc Parts"));
        list.add(new Option(26, "Spare Tire"));
        list.add(new Option(27, "Stereo/Radio Manual"));
        list.add(new Option(28, "Story Board"));
        list.add(new Option(29, "Tank Sticker"));
        list.add(new Option(30, "Tires"));
        list.add(new Option(31, "Manual(s)"));
        list.add(new Option(31, "Tool Kit"));
        list.add(new Option(32, "Top/T-Tops"));
        list.add(new Option(33, "Warranty Book"));
        list.add(new Option(34, "Wheel Lock(s)"));
        list.add(new Option(35, "Window Sticker"));
        list.add(new Option(36, "Jack"));
        list.add(new Option(37, "Engine Tuner"));
        list.add(new Option(38, "Trickle Charger"));
        list.add(new Option(39, "Battery Charger"));
        list.add(new Option(40, "Battery Tender"));
        list.add(new Option(41, "Receipts"));
        list.add(new Option(42, "Key/Key Fobs"));
        list.add(new Option(42, "Umbrella (s)"));
        list.add(new Option(43, "Literature"));
        list.add(new Option(44, "Books"));
        list.add(new Option(45, "Bill of Sale"));
        list.add(new Option(46, "Floor Mats"));
        list.add(new Option(47, "Air Pumps"));
        list.add(new Option(48, "Brochure"));
        list.add(new Option(49, "Luggage"));
        list.add(new Option(50, "Posters"));
        list.add(new Option(51, "manufacturer's literature."));
        list.add(new Option(52, "Windows"));
        list.add(new Option(53, "Carfax"));
        list.add(new Option(54, "Copy of title"));
        list.add(new Option(55, "First-aid kit"));
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
        rows.add(new FormRow(11, "License Plate"));
        rows.add(new FormRow(13, "Manuals (shop/parts)"));
        rows.add(new FormRow(14, "Marti Report"));
        rows.add(new FormRow(15, "Misc Documents"));
        rows.add(new FormRow(19, "Owner's Manual"));
        rows.add(new FormRow(22, "Protect-O-Plate"));
        rows.add(new FormRow(null, "Registration"));
        rows.add(new FormRow(23, "Remote"));
        rows.add(new FormRow(24, "Service Records"));
        rows.add(new FormRow(25, "Spare / Misc Parts"));
        rows.add(new FormRow(27, "Stereo / Radio Manual"));
        rows.add(new FormRow(28, "Story Board"));
        rows.add(new FormRow(29, "Tank Sticker"));
        rows.add(new FormRow(16, "Title"));
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

        private Option(int id, String name) {
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
