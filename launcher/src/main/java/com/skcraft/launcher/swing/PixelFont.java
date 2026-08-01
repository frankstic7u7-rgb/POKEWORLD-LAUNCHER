package com.skcraft.launcher.swing;

import com.skcraft.launcher.Launcher;
import lombok.extern.java.Log;

import java.awt.*;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Fuente estilo Minecraft (Monocraft, licencia SIL OFL 1.1, ver
 * Monocraft-LICENSE.txt) para titulos. Si por algun motivo no carga, cae a
 * una fuente sans-serif en negrita del sistema -- nunca rompe la interfaz.
 */
@Log
public class PixelFont {

    private static final String RESOURCE = "Monocraft.ttf";
    private static Font base;

    private static synchronized Font getBase() {
        if (base == null) {
            try (InputStream in = Launcher.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    base = Font.createFont(Font.TRUETYPE_FONT, in);
                    // La registramos en el GraphicsEnvironment para que tambien la pueda
                    // usar el panel de noticias (HTML/CSS) por nombre de familia, no solo
                    // los JLabel/JButton que la piden directo por objeto Font.
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "No se pudo cargar la fuente " + RESOURCE, e);
            }
            if (base == null) {
                base = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            }
        }
        return base;
    }

    /** Nombre de familia para usar en CSS (font-family) una vez cargada/registrada. */
    public static String getFamilyName() {
        return getBase().getFamily();
    }

    public static Font deriveSize(float size) {
        return getBase().deriveFont(Font.PLAIN, size);
    }
}
