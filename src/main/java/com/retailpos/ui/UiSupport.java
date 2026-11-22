package com.retailpos.ui;

import com.retailpos.util.BusinessException;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Runs UI actions and shows failures in a JOptionPane: a BusinessException's
 * user-readable message as a warning, anything else (e.g. a database error) as an error.
 */
final class UiSupport {

    private UiSupport() {
    }

    /** Runs the action; if it throws, shows the error and returns false. */
    static boolean run(Component parent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            showError(parent, e);
            return false;
        }
    }

    static void showError(Component parent, RuntimeException e) {
        if (e instanceof BusinessException) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Cannot continue", JOptionPane.WARNING_MESSAGE);
        } else {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, e.getMessage() == null ? e.toString() : e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
