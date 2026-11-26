package com.retailpos.util;

import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.SalesReceipt;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Lays out a sales receipt as fixed-width text for the screen and the printer (FR21). */
public final class ReceiptFormatter {

    public static final String STORE_NAME = "RETAIL STORE";
    public static final String STORE_ADDRESS = "Main Branch, Lahore";
    public static final String STORE_PHONE = "Tel: 042-1234567";
    public static final String THANK_YOU = "Thank you for shopping with us!";
    public static final String RETURN_NOTE = "Goods once sold can only be returned against this receipt.";

    /** Characters per line. */
    public static final int WIDTH = 44;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private ReceiptFormatter() {
    }

    public static String format(SalesReceipt receipt) {
        Sale sale = receipt.getSale();
        StringBuilder out = new StringBuilder();
        center(out, STORE_NAME);
        center(out, STORE_ADDRESS);
        center(out, STORE_PHONE);
        rule(out);
        pair(out, "Receipt no.", FormatUtil.receiptNumber(sale.getSaleID()));
        pair(out, "Date / time", receipt.getIssuedAt().format(DATE_TIME));
        pair(out, "Cashier", sale.getCashier() == null ? "" : sale.getCashier().getUsername());
        rule(out);
        out.append(String.format(Locale.US, "%-20s %4s %8s %9s%n", "Item", "Qty", "Price", "Total"));
        for (SaleItem item : sale.getItems()) {
            String name = item.getProduct().getName();
            out.append(String.format(Locale.US, "%-20s %4d %8s %9s%n", cut(name, 20), item.getQuantity(),
                    plain(item.getSellingPrice()), plain(item.getLineTotal())));
        }
        rule(out);
        pair(out, "Subtotal", FormatUtil.money(sale.getSubtotal()));
        if (sale.getDiscount() != null && sale.getDiscount().signum() > 0) {
            pair(out, "Discount", "- " + FormatUtil.money(sale.getDiscount()));
        }
        pair(out, "TOTAL", FormatUtil.money(sale.getFinalAmount()));
        rule(out);
        center(out, THANK_YOU);
        center(out, RETURN_NOTE);
        return out.toString();
    }

    private static String plain(BigDecimal amount) {
        return FormatUtil.money(amount).replace("Rs ", "");
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static void rule(StringBuilder out) {
        out.append("-".repeat(WIDTH)).append('\n');
    }

    private static void pair(StringBuilder out, String left, String right) {
        int gap = Math.max(1, WIDTH - left.length() - right.length());
        out.append(left).append(" ".repeat(gap)).append(right).append('\n');
    }

    private static void center(StringBuilder out, String text) {
        // Long lines (the return note) are wrapped on word boundaries, each wrapped line centred.
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WIDTH) {
                centerLine(out, line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        centerLine(out, line.toString());
    }

    private static void centerLine(StringBuilder out, String text) {
        out.append(" ".repeat(Math.max(0, (WIDTH - text.length()) / 2))).append(text).append('\n');
    }
}
