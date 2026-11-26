package com.retailpos.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Formatting and parsing helpers for values shown in or typed into the UI. */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** "Rs 1,450" for whole amounts, "Rs 1,450.50" otherwise; empty for null. */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal scaled = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (scaled.stripTrailingZeros().scale() <= 0) {
            return String.format(Locale.US, "Rs %,d", scaled.toBigInteger());
        }
        return String.format(Locale.US, "Rs %,.2f", scaled);
    }

    /** Parses a money amount typed by the user ("1,450", "Rs 1450.50"). */
    public static BigDecimal parseMoney(String text, String field) {
        String clean = text == null ? "" : text.replace("Rs", "").replace(",", "").trim();
        if (clean.isEmpty()) {
            throw new BusinessException(field + " is required.");
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be a number, e.g. 1450 or 1450.50.");
        }
    }

    /** Parses a whole number typed by the user. */
    public static int parseWholeNumber(String text, String field) {
        String clean = text == null ? "" : text.replace(",", "").trim();
        if (clean.isEmpty()) {
            throw new BusinessException(field + " is required.");
        }
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be a whole number.");
        }
    }

    /** Display form of a supplier ID, e.g. 7 -> "S-07". */
    public static String supplierId(int supplierID) {
        return String.format(Locale.US, "S-%02d", supplierID);
    }

    /** Display form of a sale ID, e.g. 10459 -> "SL-10459". */
    public static String saleId(int saleID) {
        return String.format(Locale.US, "SL-%05d", saleID);
    }

    /** Display form of a receipt number, e.g. sale 10459 -> "RC-SL-10459". */
    public static String receiptNumber(int saleID) {
        return "RC-" + saleId(saleID);
    }

    /** Display form of a return ID, e.g. 12 -> "RT-00012". */
    public static String returnId(int returnID) {
        return String.format(Locale.US, "RT-%05d", returnID);
    }

    /** Display form of a stock adjustment ID, e.g. 7 -> "ADJ-00007". */
    public static String adjustmentId(int adjustmentID) {
        return String.format(Locale.US, "ADJ-%05d", adjustmentID);
    }

    /** Reads a sale number typed by the user: "SL-00012", "sl-12" or "12" all give 12. */
    public static int parseSaleId(String text) {
        String clean = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) {
            throw new BusinessException("Enter a sale number, e.g. SL-00012.");
        }
        if (clean.startsWith("SL-")) {
            clean = clean.substring(3);
        }
        try {
            int id = Integer.parseInt(clean);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException e) {
            // fall through to the message below
        }
        throw new BusinessException("\"" + text.trim() + "\" is not a sale number. Use the number on the receipt, e.g. SL-00012.");
    }

    /** Parses a date typed as YYYY-MM-DD. */
    public static LocalDate parseDate(String text, String field) {
        LocalDate date = parseOptionalDate(text, field);
        if (date == null) {
            throw new BusinessException(field + " is required.");
        }
        return date;
    }

    /** Parses a date typed as YYYY-MM-DD; an empty field gives null. */
    public static LocalDate parseOptionalDate(String text, String field) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(clean);
        } catch (DateTimeParseException e) {
            throw new BusinessException(field + " must be a date in YYYY-MM-DD format, e.g. 2026-11-26.");
        }
    }

    /** "ACTIVE" -> "Active", "DISCONTINUED" -> "Discontinued". */
    public static String status(String status) {
        if (status == null || status.isEmpty()) {
            return "";
        }
        return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
