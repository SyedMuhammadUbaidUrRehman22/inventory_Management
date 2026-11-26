package com.retailpos.util;

import java.math.BigDecimal;
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

    /** "ACTIVE" -> "Active", "DISCONTINUED" -> "Discontinued". */
    public static String status(String status) {
        if (status == null || status.isEmpty()) {
            return "";
        }
        return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
