package com.example.gt6driver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;

import com.example.gt6driver.model.PropertyItem;
import com.example.gt6driver.model.PropertyItemCheckInType;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PropertyPrintDocumentAdapter extends PrintDocumentAdapter {
    private static final float FORM_WIDTH = 1720f;
    private static final float FORM_HEIGHT = 2266f;

    private final Context context;
    private final Data data;
    private PrintAttributes printAttributes;

    public PropertyPrintDocumentAdapter(Context context, Data data) {
        this.context = context;
        this.data = data != null ? data : new Data();
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes,
                         PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal,
                         LayoutResultCallback callback,
                         Bundle extras) {
        printAttributes = newAttributes;
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        PrintDocumentInfo info = new PrintDocumentInfo.Builder("property-check-in.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build();
        callback.onLayoutFinished(info, oldAttributes == null || !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(PageRange[] pages,
                        ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal,
                        WriteResultCallback callback) {
        PrintedPdfDocument document = new PrintedPdfDocument(context, printAttributes);
        try {
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
                return;
            }

            PdfDocument.Page page = document.startPage(0);
            drawPage(page.getCanvas(), page.getInfo().getContentRect());
            document.finishPage(page);

            document.writeTo(new FileOutputStream(destination.getFileDescriptor()));
            callback.onWriteFinished(new PageRange[] { PageRange.ALL_PAGES });
        } catch (Exception e) {
            callback.onWriteFailed(e.getMessage());
        } finally {
            document.close();
        }
    }

    private void drawPage(Canvas canvas, Rect contentRect) {
        canvas.drawColor(Color.WHITE);

        float scale = Math.min(contentRect.width() / FORM_WIDTH, contentRect.height() / FORM_HEIGHT);
        float left = contentRect.left + (contentRect.width() - FORM_WIDTH * scale) / 2f;
        float top = contentRect.top + (contentRect.height() - FORM_HEIGHT * scale) / 2f;

        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        drawForm(canvas);
        canvas.restore();
    }

    private void drawForm(Canvas c) {
        Paint line = stroke(Color.rgb(32, 32, 32), 3f);
        Paint thinLine = stroke(Color.rgb(32, 32, 32), 2f);
        Paint blackFill = fill(Color.rgb(42, 42, 42));
        Paint red = text(Color.rgb(170, 56, 72), 28f, Typeface.BOLD);
        Paint redSmall = text(Color.rgb(170, 56, 72), 24f, Typeface.BOLD);
        Paint labelWhite = text(Color.WHITE, 26f, Typeface.BOLD);
        Paint labelBlack = text(Color.rgb(35, 35, 35), 28f, Typeface.BOLD);
        Paint normal = text(Color.rgb(35, 35, 35), 28f, Typeface.NORMAL);
        Paint bold = text(Color.rgb(35, 35, 35), 30f, Typeface.BOLD);
        Paint small = text(Color.rgb(35, 35, 35), 23f, Typeface.NORMAL);
        Paint rowText = text(Color.rgb(20, 20, 20), 25f, Typeface.BOLD);
        Paint mark = text(Color.rgb(20, 20, 20), 34f, Typeface.BOLD);

        c.drawLine(130, 150, 1465, 150, thinLine);

        Paint logo = text(Color.rgb(150, 45, 65), 54f, Typeface.BOLD_ITALIC);
        logo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        c.drawText("Barrett-Jackson", 160, 235, logo);

        c.drawRect(995, 148, 1466, 244, blackFill);
        drawCenteredText(c, safe(data.eventName), 995, 148, 471, 96, text(Color.WHITE, 38f, Typeface.BOLD));

        c.drawText("Yr", 170, 320, normal);
        drawLineField(c, safe(data.year), 202, 323, 328, bold, line);
        c.drawText("MAKE / MODEL", 332, 320, bold);
        drawLineField(c, safe(data.makeModel), 520, 323, 1134, bold, line);
        c.drawText("COLOR", 1172, 320, bold);
        drawLineField(c, safe(data.color), 1265, 323, 1458, bold, line);

        Paint titlePaint = text(Color.rgb(35, 35, 35), 38f, Typeface.BOLD);
        titlePaint.setUnderlineText(true);
        drawCenteredText(c, "PROPERTY CHECK-IN SHEET", 0, 380, FORM_WIDTH, 72, titlePaint);

        drawBoxedField(c, "LOT #:", safe(data.lotNumber), 132, 486, 180, 45, 314, 529, 815, labelWhite, bold, blackFill, line);
        drawBoxedField(c, "DATE:", safe(data.dateText), 994, 486, 152, 45, 1146, 529, 1458, labelWhite, bold, blackFill, line);
        drawBoxedField(c, "INITIALS:", safe(data.initials), 132, 582, 180, 45, 314, 625, 815, labelWhite, bold, blackFill, line);
        drawBoxedField(c, "LOCATION:", safe(data.parkingLocation), 994, 582, 152, 45, 1146, 625, 1458, labelWhite, bold, blackFill, line);

        PropertyMarks marks = buildMarks();
        drawInventory(c, marks, line, thinLine, blackFill, labelWhite, labelBlack, red, rowText, mark);
        drawOtherAndComments(c, marks, line, small, normal, mark);

        c.drawText("DROPPED OFF BY :", 134, 2060, redSmall);
        c.drawLine(374, 2058, 440, 2058, line);
        c.drawText("OWNER/SELLER", 448, 2060, redSmall);
        c.drawLine(676, 2058, 744, 2058, line);
        c.drawText("CONSIGNMENT", 752, 2060, redSmall);
        c.drawLine(1004, 2058, 1072, 2058, line);
        c.drawText("CAR CHECK-IN", 1080, 2060, redSmall);
        c.drawText("Auctioneer", 1290, 2064, small);
        c.drawLine(1385, 2058, 1455, 2058, line);
    }

    private void drawInventory(Canvas c,
                               PropertyMarks marks,
                               Paint line,
                               Paint thinLine,
                               Paint blackFill,
                               Paint labelWhite,
                               Paint labelBlack,
                               Paint red,
                               Paint rowText,
                               Paint mark) {
        float tableX = 132;
        float tableY = 705;
        float colRemoved = 182;
        float colItem = 296;
        float colLeft = 206;
        float headerH = 130;
        float rowH = 44;
        List<PropertyTypeCatalog.FormRow> formRows = PropertyTypeCatalog.formRows();
        float tableRight = tableX + colRemoved + colItem + colLeft;
        float tableBottom = tableY + headerH + formRows.size() * rowH;

        c.drawRect(tableX, 660, 1458, 704, blackFill);
        drawCenteredText(c, "LOOSE PARTS INVENTORY", tableX, 660, 1326, 44, labelWhite);

        c.drawRect(tableX, tableY, tableRight, tableBottom, line);
        c.drawLine(tableX + colRemoved, tableY, tableX + colRemoved, tableBottom, line);
        c.drawLine(tableX + colRemoved + colItem, tableY, tableX + colRemoved + colItem, tableBottom, line);
        c.drawLine(tableX, tableY + headerH, tableRight, tableY + headerH, line);

        drawMultilineCentered(c, listOf("Property", "Removed from", "Vehicle"), tableX, tableY, colRemoved, headerH, labelBlack, 32f);
        drawMultilineCentered(c, listOf("Property Left in", "Vehicle"), tableX + colRemoved + colItem, tableY, colLeft, headerH, red, 34f);

        for (int i = 0; i < formRows.size(); i++) {
            float top = tableY + headerH + i * rowH;
            c.drawLine(tableX, top + rowH, tableRight, top + rowH, thinLine);

            String row = formRows.get(i).label;
            drawCenteredText(c, row, tableX + colRemoved, top, colItem, rowH, rowText);

            RowMark rowMark = marks.forRow(row);
            drawCenteredText(c, rowMark.removed, tableX, top, colRemoved, rowH, mark);
            drawCenteredText(c, rowMark.leftInVehicle, tableX + colRemoved + colItem, top, colLeft, rowH, mark);
        }

        Paint noPropertyPaint = text(Color.rgb(55, 55, 55), 40f, Typeface.BOLD);
        c.drawText("No Property", 1152, 780, noPropertyPaint);
        c.drawRect(1360, 742, 1392, 774, line);
        if (marks.noProperty) {
            c.drawLine(1364, 746, 1388, 770, line);
            c.drawLine(1388, 746, 1364, 770, line);
        }
    }

    private void drawOtherAndComments(Canvas c, PropertyMarks marks, Paint line, Paint small, Paint normal, Paint mark) {
        c.drawText("Other:", 845, 870, small);
        float lineStart = 990;
        float lineEnd = 1454;
        float y = 878;
        for (int i = 0; i < 14; i++) {
            c.drawLine(lineStart, y + i * 45, lineEnd, y + i * 45, line);
        }

        List<String> otherLines = wrapLines(marks.otherLines, normal, lineEnd - lineStart - 12);
        drawOnLines(c, otherLines, lineStart + 8, 865, 45, 13, normal);

        c.drawText("Comments / Left In Vehicle:", 845, 1578, small);
        c.drawLine(1260, 1582, 1454, 1582, line);
        float commentY = 1624;
        for (int i = 0; i < 7; i++) {
            c.drawLine(840, commentY + i * 45, 1454, commentY + i * 45, line);
        }

        List<String> commentLines = wrapLines(marks.commentLines, normal, 590);
        drawOnLines(c, commentLines, 850, 1610, 45, 6, normal);

        c.drawRect(840, 1894, 1260, 1990, line);
        c.drawLine(840, 1942, 1260, 1942, line);
        c.drawLine(1146, 1894, 1146, 1990, line);
        drawCenteredText(c, "No Trunk Access / No Key", 840, 1894, 306, 48, small);
        drawCenteredText(c, "No Glove Box", 840, 1942, 306, 48, mark);
    }

    private PropertyMarks buildMarks() {
        PropertyMarks marks = new PropertyMarks();
        List<PropertyItem> items = data.items != null ? data.items : Collections.emptyList();

        for (PropertyItem item : items) {
            if (PropertyTypeCatalog.isNoProperty(item)) {
                marks.noProperty = true;
                continue;
            }

            String row = PropertyTypeCatalog.formRowFor(item);
            String status = propertyStatus(item);
            String mark = markFor(item);
            String details = itemDetails(item, status);

            if (row == null) {
                if (!details.isEmpty()) {
                    marks.otherLines.add(details);
                }
                continue;
            }

            RowMark rowMark = marks.forRow(row);
            if ("left".equals(status)) {
                rowMark.leftInVehicle = appendMark(rowMark.leftInVehicle, mark);
                addIfNotEmpty(marks.commentLines, notesOnly(item));
            } else if ("removed".equals(status)) {
                rowMark.removed = appendMark(rowMark.removed, mark);
                addIfNotEmpty(marks.commentLines, notesOnly(item));
            } else {
                addIfNotEmpty(marks.commentLines, details);
            }
        }

        return marks;
    }

    private String itemDetails(PropertyItem item, String status) {
        String type = safe(item.propertyType);
        String desc = safe(item.propertyDescription);
        String notes = safe(item.notes);
        String qty = item.quantity != null && item.quantity > 1 ? " x" + item.quantity : "";
        String label = desc.isEmpty() || desc.equalsIgnoreCase(type) ? type : type + " - " + desc;
        String statusText = statusText(status);

        StringBuilder out = new StringBuilder();
        out.append(label).append(qty);
        if (!statusText.isEmpty()) out.append(" (").append(statusText).append(")");
        if (!notes.isEmpty()) out.append(": ").append(notes);
        return out.toString();
    }

    private String notesOnly(PropertyItem item) {
        String notes = safe(item.notes);
        if (notes.isEmpty()) return "";
        String type = safe(item.propertyType);
        return type.isEmpty() ? notes : type + ": " + notes;
    }

    private String propertyStatus(PropertyItem item) {
        if (item != null && Boolean.TRUE.equals(item.isLeftInCar)) return "left";

        String value = normalize(coalesce(
                item == null ? "" : safe(item.checkInType),
                item == null ? "" : safe(item.status)
        ));

        if (value.equals(PropertyItemCheckInType.AWAITING_ARRIVAL.getApiValue())
                || value.equalsIgnoreCase(PropertyItemCheckInType.AWAITING_ARRIVAL_LEGACY_API_VALUE)
                || value.equals("awaitingarrival")
                || value.equals("awaiting")) {
            return "";
        }
        if (value.equals(PropertyItemCheckInType.ARRIVED.getApiValue())
                || value.equals(PropertyItemCheckInType.REMOVED.getApiValue())
                || value.equals("arrived")
                || value.equals("removed")
                || value.equals("removedfromvehicle")
                || value.equals("propertyremovedfromvehicle")) {
            return "removed";
        }
        if (value.equals(PropertyItemCheckInType.LEFT_IN_CAR.getApiValue())
                || value.equals(PropertyItemCheckInType.LEFT_IN_CAR_ALTERNATE.getApiValue())
                || value.equals("leftincar")
                || value.equals("incar")
                || value.equals("leftinvehicle")
                || value.equals("leftinveh")
                || value.equals("propertyleftinvehicle")) {
            return "left";
        }
        if (item != null && Boolean.FALSE.equals(item.isLeftInCar)) {
            return "removed";
        }
        return "";
    }

    private String statusText(String status) {
        if ("left".equals(status)) return "Left in Vehicle";
        if ("removed".equals(status)) return "Removed";
        return "Awaiting Arrival";
    }

    private String markFor(PropertyItem item) {
        if (item != null && item.quantity != null && item.quantity > 0) {
            return String.valueOf(item.quantity);
        }
        return "1";
    }

    private String appendMark(String current, String mark) {
        if (safe(current).isEmpty()) return mark;
        try {
            int currentCount = Integer.parseInt(current.trim());
            int addedCount = Integer.parseInt(mark.trim());
            return String.valueOf(currentCount + addedCount);
        } catch (NumberFormatException ignored) {
            return current + ", " + mark;
        }
    }

    private void addIfNotEmpty(List<String> list, String value) {
        if (!safe(value).isEmpty()) list.add(value);
    }

    private String coalesce(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!safe(value).isEmpty()) return value;
        }
        return "";
    }

    private String normalize(String value) {
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

    private List<String> wrapLines(List<String> source, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String value : source) {
            String text = safe(value);
            if (text.isEmpty()) continue;

            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        return lines;
    }

    private void drawOnLines(Canvas c, List<String> lines, float x, float firstBaseline, float lineGap, int maxLines, Paint paint) {
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            c.drawText(lines.get(i), x, firstBaseline + i * lineGap, paint);
        }
        if (lines.size() > maxLines) {
            c.drawText("...", x, firstBaseline + maxLines * lineGap, paint);
        }
    }

    private void drawBoxedField(Canvas c,
                                String label,
                                String value,
                                float boxX,
                                float boxY,
                                float boxW,
                                float boxH,
                                float lineStart,
                                float lineY,
                                float lineEnd,
                                Paint labelPaint,
                                Paint valuePaint,
                                Paint boxFill,
                                Paint linePaint) {
        c.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, boxFill);
        drawCenteredText(c, label, boxX, boxY, boxW, boxH, labelPaint);
        drawLineField(c, value, lineStart, lineY, lineEnd, valuePaint, linePaint);
    }

    private void drawLineField(Canvas c, String value, float startX, float lineY, float endX, Paint valuePaint, Paint linePaint) {
        c.drawLine(startX, lineY, endX, lineY, linePaint);
        String text = safe(value);
        if (text.isEmpty()) return;

        Paint fit = new Paint(valuePaint);
        while (fit.getTextSize() > 18f && fit.measureText(text) > (endX - startX - 10f)) {
            fit.setTextSize(fit.getTextSize() - 1f);
        }
        c.drawText(text, startX + 8f, lineY - 8f, fit);
    }

    private void drawCenteredText(Canvas c, String value, float x, float y, float width, float height, Paint paint) {
        String text = safe(value);
        if (text.isEmpty()) return;

        Paint fit = new Paint(paint);
        while (fit.getTextSize() > 14f && fit.measureText(text) > width - 8f) {
            fit.setTextSize(fit.getTextSize() - 1f);
        }

        Paint.FontMetrics fm = fit.getFontMetrics();
        float baseline = y + (height - fm.ascent - fm.descent) / 2f;
        c.drawText(text, x + (width - fit.measureText(text)) / 2f, baseline, fit);
    }

    private void drawMultilineCentered(Canvas c, List<String> lines, float x, float y, float width, float height, Paint paint, float lineGap) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        float totalHeight = lines.size() * lineGap;
        float firstBaseline = y + (height - totalHeight) / 2f - fm.ascent;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            c.drawText(text, x + (width - paint.measureText(text)) / 2f, firstBaseline + i * lineGap, paint);
        }
    }

    private List<String> listOf(String... values) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, values);
        return list;
    }

    private Paint text(int color, float size, int style) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        return paint;
    }

    private Paint stroke(int color, float width) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        return paint;
    }

    private Paint fill(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class Data {
        public String eventName = "";
        public String year = "";
        public String makeModel = "";
        public String color = "";
        public String lotNumber = "";
        public String dateText = "";
        public String parkingLocation = "";
        public String initials = "";
        public List<PropertyItem> items = Collections.emptyList();
    }

    private static class PropertyMarks {
        boolean noProperty;
        final List<RowMark> rows = new ArrayList<>();
        final List<String> otherLines = new ArrayList<>();
        final List<String> commentLines = new ArrayList<>();

        RowMark forRow(String label) {
            for (RowMark row : rows) {
                if (row.label.equals(label)) return row;
            }
            RowMark row = new RowMark(label);
            rows.add(row);
            return row;
        }
    }

    private static class RowMark {
        final String label;
        String removed = "";
        String leftInVehicle = "";

        RowMark(String label) {
            this.label = label;
        }
    }
}
