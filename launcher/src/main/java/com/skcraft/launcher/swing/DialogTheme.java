package com.skcraft.launcher.swing;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * Aplica el tema morado/oscuro de PokeWorld a un dialogo entero, recorriendo
 * todos sus componentes. Los dialogos de SKCraft (Opciones, seleccion de
 * cuenta, progreso, etc.) vienen con el blanco por defecto de Swing -- esto
 * evita tener que re-estilizar cada uno a mano, componente por componente.
 */
public class DialogTheme {

    public static final Color BG_DARK = new Color(0x14, 0x0d, 0x1e);
    public static final Color BG_FIELD = new Color(0x24, 0x18, 0x33);
    public static final Color BORDER = new Color(120, 80, 190);
    public static final Color TEXT = new Color(0xf0, 0xf0, 0xf0);
    public static final Color ACCENT = new Color(0xa2, 0x59, 0xff);

    /** Aplica el tema a la ventana entera (content pane para abajo). */
    public static void apply(Window window) {
        if (window instanceof RootPaneContainer) {
            Container content = ((RootPaneContainer) window).getContentPane();
            if (content instanceof JComponent) {
                ((JComponent) content).setOpaque(true);
            }
            content.setBackground(BG_DARK);
            applyToTree(content);
        }
    }

    private static void applyToTree(Component c) {
        if (c instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) c;
            tabs.setBackground(BG_DARK);
            tabs.setForeground(TEXT);
        } else if (c instanceof JScrollPane) {
            JScrollPane scroll = (JScrollPane) c;
            scroll.setBackground(BG_DARK);
            scroll.getViewport().setBackground(BG_DARK);
            scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        } else if (c instanceof JList) {
            c.setBackground(BG_FIELD);
            c.setForeground(TEXT);
        } else if (c instanceof JTextField || c instanceof JSpinner || c instanceof JComboBox) {
            c.setBackground(BG_FIELD);
            c.setForeground(TEXT);
            if (c instanceof JTextField) {
                // Sin esto el caret queda en el negro por defecto del Look
                // and Feel -- invisible contra el fondo morado oscuro de
                // BG_FIELD (pedido explicito: "falta el palito" al escribir).
                ((JTextField) c).setCaretColor(TEXT);
            }
            if (c instanceof JComponent) {
                ((JComponent) c).setBorder(BorderFactory.createLineBorder(BORDER));
            }
        } else if (c instanceof JButton) {
            JButton button = (JButton) c;
            button.setBackground(BG_FIELD);
            button.setForeground(TEXT);
            button.setFocusPainted(false);
            // Sin esto Windows a veces ignora setBackground en botones con
            // Border propio -- ver LauncherFrame.styleSecondaryButton.
            button.setUI(new BasicButtonUI());
            button.setOpaque(true);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        } else if (c instanceof JCheckBox || c instanceof JRadioButton) {
            c.setForeground(TEXT);
            ((JComponent) c).setOpaque(false);
        } else if (c instanceof JLabel) {
            c.setForeground(TEXT);
        } else if (c instanceof JPanel) {
            c.setBackground(BG_DARK);
            ((JPanel) c).setOpaque(true);
        }

        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                applyToTree(child);
            }
        }
    }
}
