/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.dialog;

import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.launch.LaunchListener;
import com.skcraft.launcher.launch.LaunchOptions;
import com.skcraft.launcher.launch.LaunchOptions.UpdatePolicy;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.*;
import com.skcraft.launcher.util.ServerStatusPinger;
import com.skcraft.launcher.util.SharedLocale;
import com.skcraft.launcher.util.SwingExecutor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.ref.WeakReference;

import static com.skcraft.launcher.util.SharedLocale.tr;

/**
 * The main launcher frame.
 */
@Log
public class LauncherFrame extends JFrame {

    private final Launcher launcher;

    @Getter
    private final InstanceTable instancesTable = new InstanceTable();
    private final InstanceTableModel instancesModel;
    private WebpagePanel webView;
    private final JButton launchButton = new JButton(SharedLocale.tr("launcher.launch"));
    private final JButton websiteButton = new JButton(SharedLocale.tr("launcher.website"));
    private final JButton optionsButton = new JButton(SharedLocale.tr("launcher.options"));
    private final JButton selfUpdateButton = new JButton(SharedLocale.tr("launcher.updateLauncher"));
    private final JCheckBox updateCheck = new JCheckBox(SharedLocale.tr("launcher.downloadUpdates"));
    private final JButton discordButton = new JButton(SharedLocale.tr("launcher.discord"));
    private final JButton donateButton = new JButton(SharedLocale.tr("launcher.donate"));
    private final JSpinner maxMemorySpinner = new JSpinner(new SpinnerNumberModel(2048, 512, 32768, 512));
    private final JLabel onlineCountLabel = new JLabel(" ");

    private static final String DISCORD_URL = "https://discord.gg/Gz2rD4hE6F";
    private static final String DONATE_URL = "https://pokeworld.contetops.com/tienda";
    private static final String WEBSITE_URL = "https://pokeworld.contetops.com";
    private static final String SERVER_HOST = "hour-fiction.gl.joinmc.link";
    private static final int SERVER_PORT = 25565;
    private static final String BACKGROUND_RESOURCE = "background.png";
    private static final int CORNER_RADIUS = 24;

    /** Morado de marca real de la web (pokeworld.contetops.com), no un aproximado. */
    private static final Color BRAND_PURPLE = new Color(0xa2, 0x59, 0xff);
    private static final Color BRAND_BLACK = new Color(0x0a, 0x0a, 0x0c);
    private static final Color PANEL_BG = new Color(10, 8, 14, 210);
    private static final Color PANEL_BORDER = new Color(120, 80, 190);

    private final JButton closeButton = new JButton("✕");
    private final JButton minimizeButton = new JButton("–");
    private Point dragOffset;

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public LauncherFrame(@NonNull Launcher launcher) {
        super(tr("launcher.title", launcher.getVersion()));

        this.launcher = launcher;
        instancesModel = new InstanceTableModel(launcher.getInstances());

        setUndecorated(true);
        // Sin esto, en algunas combinaciones de Windows 11 + DWM el setShape() de mas
        // abajo deja un resto del marco nativo (titulo centrado en negrita, botones de
        // minimizar/cerrar propios de Windows) superpuesto con nuestra barra de titulo
        // propia. Declarar la ventana como translucida por pixel evita que DWM intente
        // "ayudar" dibujando su propio marco encima.
        if (GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            setBackground(new Color(0, 0, 0, 0));
        }
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(400, 300));
        initComponents();
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

        SwingHelper.setFrameIcon(this, Launcher.class, "icon.png");

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                loadInstances();
            }
        });

        refreshOnlineCount();
    }

    private void initComponents() {
        // Fuerza la carga/registro de la fuente pixel antes de armar el panel de
        // noticias (HTML), que la referencia por nombre de familia en su CSS.
        PixelFont.getFamilyName();

        JPanel container = createContainerPanel();
        container.setLayout(new MigLayout("fill, insets dialog", "[grow][]", "[]0[grow][]"));

        JPanel titleBar = createTitleBar();
        container.add(titleBar, "growx, wrap, span 2, gapbottom unrel");

        JPanel descriptionPanel = createDescriptionPanel();
        JPanel buttonColumn = createButtonColumn();
        container.add(descriptionPanel, "grow, w null:760");
        container.add(buttonColumn, "growy, wrap, top");

        JPanel donatePanel = createDonatePanel();
        container.add(donatePanel, "growx, span 2");

        add(container, BorderLayout.CENTER);

        instancesModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (instancesTable.getRowCount() > 0) {
                    instancesTable.setRowSelectionInterval(0, 0);
                }
            }
        });

        selfUpdateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launcher.getUpdateManager().performUpdate(LauncherFrame.this);
            }
        });

        optionsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOptions();
            }
        });

        launchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                launch();
            }
        });

        discordButton.addActionListener(ActionListeners.openURL(discordButton, DISCORD_URL));
        donateButton.addActionListener(ActionListeners.openURL(donateButton, DONATE_URL));
        websiteButton.addActionListener(ActionListeners.openURL(websiteButton, WEBSITE_URL));

        maxMemorySpinner.addChangeListener(e -> {
            Configuration config = launcher.getConfig();
            config.setMaxMemory((Integer) maxMemorySpinner.getValue());
            Persistence.commitAndForget(config);
        });
    }

    protected JPanel createContainerPanel() {
        return new BackgroundPanel();
    }

    /**
     * Panel central: titulo grande en fuente pixel + descripcion/eventos
     * reales del server, reemplaza la vieja lista de instancias (con un solo
     * modpack no hacia falta un selector).
     */
    private JPanel createDescriptionPanel() {
        JPanel panel = new DescriptionBackgroundPanel();
        panel.setLayout(new BorderLayout(0, 8));

        GlowLabel title = new GlowLabel(SharedLocale.tr("launcher.welcomeTitle"));
        title.setFont(PixelFont.deriveSize(32f));
        title.setForeground(new Color(0xf0, 0xe0, 0xff));
        title.setGlowColor(new Color(0xc7, 0x3d, 0xff));
        title.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        onlineCountLabel.setForeground(new Color(0x3b, 0xa5, 0x5c));
        onlineCountLabel.setFont(onlineCountLabel.getFont().deriveFont(Font.BOLD, 12f));
        onlineCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));

        JPanel headerBox = new JPanel(new BorderLayout());
        headerBox.setOpaque(false);
        headerBox.add(title, BorderLayout.NORTH);
        headerBox.add(onlineCountLabel, BorderLayout.SOUTH);

        webView = createNewsPanel();
        webView.setOpaque(false);
        styleGlassPanel(webView);

        panel.add(headerBox, BorderLayout.NORTH);
        panel.add(webView, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Panel del centro con la imagen de Mewtwo/eventos de fondo (la que
     * mando el usuario), atras del titulo y del panel de noticias -- que el
     * HTML tenga fondo semitransparente en vez de solido es lo que deja ver
     * esta imagen sin perder la lectura del texto.
     */
    private static class DescriptionBackgroundPanel extends JPanel {
        private final Image background;

        DescriptionBackgroundPanel() {
            setOpaque(true);
            java.net.URL url = Launcher.class.getResource("description_bg.jpg");
            background = url != null ? new ImageIcon(url).getImage() : null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (background != null) {
                g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
                g.setColor(new Color(10, 6, 18, 90));
                g.fillRect(0, 0, getWidth(), getHeight());
            } else {
                g.setColor(BRAND_BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    /**
     * Columna vertical de botones grandes a la derecha (Discord, Opciones,
     * Jugar), en vez de la fila chica de antes.
     */
    private JPanel createButtonColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new MigLayout("insets 6, gap 12", "[]", "[][][]push"));

        styleThemedButton(discordButton, "btn_discord.png", 240, 129);
        styleThemedButton(optionsButton, "btn_options.png", 240, 140);
        styleThemedButton(launchButton, "btn_play.png", 260, 121);

        column.add(discordButton, "wrap, align center");
        column.add(optionsButton, "wrap, align center");
        column.add(launchButton, "wrap, align center");

        return column;
    }

    /**
     * Franja inferior de donaciones -- llamativa, con boton directo a la
     * tienda de Tebex.
     */
    private JPanel createDonatePanel() {
        JPanel panel = new JPanel(new MigLayout("insets 10 16 10 16, fillx", "[grow][][][]", "[]"));
        panel.setOpaque(true);
        panel.setBackground(new Color(0x18, 0x0c, 0x28));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 165, 0, 160)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        JLabel donateTitle = new JLabel(SharedLocale.tr("launcher.donateTitle"));
        donateTitle.setForeground(Color.ORANGE);
        donateTitle.setFont(donateTitle.getFont().deriveFont(Font.BOLD, 15f));

        JLabel donateText = new JLabel(SharedLocale.tr("launcher.donateText"));
        donateText.setForeground(new Color(230, 230, 230));

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
        textBox.add(donateTitle);
        textBox.add(donateText);

        donateButton.setFont(donateButton.getFont().deriveFont(Font.BOLD, 13f));
        donateButton.setBackground(Color.ORANGE);
        donateButton.setForeground(Color.BLACK);
        donateButton.setOpaque(true);
        donateButton.setBorderPainted(false);
        donateButton.setFocusPainted(false);
        donateButton.setMargin(new Insets(8, 20, 8, 20));

        panel.add(textBox, "growx");
        panel.add(websiteButton);
        panel.add(donateButton);

        styleSecondaryButton(websiteButton);
        selfUpdateButton.setVisible(launcher.getUpdateManager().getPendingUpdate());
        launcher.getUpdateManager().addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("pendingUpdate")) {
                    selfUpdateButton.setVisible((Boolean) evt.getNewValue());
                }
            }
        });
        styleSecondaryButton(selfUpdateButton);
        updateCheck.setSelected(true);
        updateCheck.setForeground(Color.WHITE);
        updateCheck.setOpaque(false);
        maxMemorySpinner.setToolTipText(SharedLocale.tr("launcher.maxMemoryTooltip"));
        maxMemorySpinner.setValue(launcher.getConfig().getMaxMemory());
        ((JSpinner.DefaultEditor) maxMemorySpinner.getEditor()).getTextField().setColumns(4);
        JLabel maxMemoryLabel = new JLabel(SharedLocale.tr("launcher.maxMemoryLabel"));
        maxMemoryLabel.setForeground(Color.WHITE);

        JPanel secondaryRow = new JPanel();
        secondaryRow.setOpaque(false);
        secondaryRow.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        secondaryRow.add(updateCheck);
        secondaryRow.add(selfUpdateButton);
        secondaryRow.add(maxMemoryLabel);
        secondaryRow.add(maxMemorySpinner);
        panel.add(secondaryRow, "newline, span 4, growx");

        instancesTable.setModel(instancesModel);

        return panel;
    }

    private void styleGlassPanel(JComponent c) {
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    }

    /**
     * Consulta cuantos jugadores hay online ahora mismo (server list ping) y
     * lo muestra -- si falla (red, timeout, etc.) simplemente no muestra
     * nada, no rompe la interfaz.
     */
    private void refreshOnlineCount() {
        onlineCountLabel.setText(" ");
        new Thread(() -> {
            ServerStatusPinger.Status status = ServerStatusPinger.ping(SERVER_HOST, SERVER_PORT, 4000);
            if (status != null && status.getOnline() >= 0) {
                String text = SharedLocale.tr("launcher.onlineCount", status.getOnline());
                SwingUtilities.invokeLater(() -> onlineCountLabel.setText(text));
            }
        }, "online-count-ping").start();
    }

    /**
     * Barra de titulo propia -- como la ventana no tiene marco nativo (para
     * poder tener esquinas redondeadas), hay que reimplementar a mano el
     * arrastre de la ventana y los botones de minimizar/cerrar.
     */
    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);

        JLabel titleLabel = new JLabel(SharedLocale.tr("launcher.appTitle"));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 0));

        styleTitleBarButton(minimizeButton);
        styleTitleBarButton(closeButton);

        JPanel windowButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        windowButtons.setOpaque(false);
        windowButtons.add(minimizeButton);
        windowButtons.add(closeButton);

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(windowButtons, BorderLayout.EAST);

        minimizeButton.addActionListener(e -> setState(Frame.ICONIFIED));
        closeButton.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
        };
        MouseMotionAdapter dragMotionListener = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                Point current = getLocation();
                setLocation(current.x + e.getX() - dragOffset.x, current.y + e.getY() - dragOffset.y);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragMotionListener);
        titleLabel.addMouseListener(dragListener);
        titleLabel.addMouseMotionListener(dragMotionListener);

        return titleBar;
    }

    /**
     * Le pone a un boton el marco/arte propio como icono (recortado del sheet
     * que armamos) y el texto centrado encima con sombra (para que resalte
     * arriba del arte en vez de perderse), sacando el look de boton cuadrado
     * de Swing por defecto -- solo queda visible la imagen + texto.
     */
    private static final Color BUTTON_TEXT_COLOR = new Color(0xff, 0xd7, 0x4d);
    private static final Color BUTTON_TEXT_HOVER = new Color(0xff, 0xf2, 0xb0);
    private static final Color BUTTON_TEXT_SHADOW = new Color(30, 10, 45);

    private void styleThemedButton(JButton button, String resourceName, int width, int height) {
        Icon icon = SwingHelper.createIcon(Launcher.class, resourceName, width, height);
        button.setIcon(icon);
        button.setText(button.getText().toUpperCase());
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(0);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(BUTTON_TEXT_COLOR);
        button.setFont(PixelFont.deriveSize(21f));
        button.setPreferredSize(new Dimension(width, height));
        // Dibuja el texto con sombra oscura detras para que resalte sobre el
        // arte del boton en vez de leer plano -- BasicButtonUI es la unica
        // forma confiable de interceptar el pintado del texto sin perder el
        // resto del comportamiento normal del boton.
        button.setUI(new ShadowTextButtonUI());

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(BUTTON_TEXT_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(BUTTON_TEXT_COLOR);
            }
        });
    }

    /**
     * ButtonUI minima que pinta el texto dos veces (sombra oscura corrida 2px,
     * despues el color real encima) para que se lea sobre un fondo con
     * detalle en vez de perderse. No cambia nada mas del comportamiento del
     * boton (Basic UI respeta setForeground/setFont normalmente).
     */
    private static class ShadowTextButtonUI extends BasicButtonUI {
        @Override
        protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(b.getFont());
            FontMetrics fm = g2.getFontMetrics();
            // Centrado contra el boton entero, no contra el textRect que calcula
            // BasicButtonUI en base al layout de icono+texto -- con icono y texto
            // los dos en CENTER ese calculo queda corrido, esto es mas confiable.
            // El 0.60 (en vez de 0.5) es porque la "placa" vacia del arte del
            // boton no esta en el medio exacto de la imagen -- el Pokemon
            // ocupa el tercio de arriba, la placa esta mas abajo.
            int x = (b.getWidth() - fm.stringWidth(text)) / 2;
            int y = (int) (b.getHeight() * 0.60) + fm.getAscent() / 2 - fm.getDescent() / 2;

            g2.setColor(BUTTON_TEXT_SHADOW);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    g2.drawString(text, x + dx + 2, y + dy + 2);
                }
            }
            // "Negrita" falsa -- Monocraft solo trae un peso -- dibujando el
            // texto real varias veces con 1px de corrimiento se ve mas grueso.
            g2.setColor(b.getForeground());
            g2.drawString(text, x, y);
            g2.drawString(text, x + 1, y);
            g2.drawString(text, x, y + 1);
        }
    }

    /**
     * Para los botones que no tienen su propio arte (Sitio Web, Actualizar
     * launcher) -- mismo patron que ya funciona bien en el boton "Donar"
     * (fondo solido + sin borde pintado + sin focus). Un JButton con un
     * Border custom (LineBorder, etc.) a veces ignora setBackground en
     * Windows -- por eso este usa solo margin, sin Border real.
     */
    private void styleSecondaryButton(JButton button) {
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setBackground(BRAND_PURPLE.darker());
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setMargin(new Insets(8, 16, 8, 16));
    }

    private void styleTitleBarButton(JButton button) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setMargin(new Insets(2, 8, 2, 8));
    }

    /**
     * A panel that paints {@link #BACKGROUND_RESOURCE} scaled to fill itself, if
     * that resource exists in the jar. If it doesn't (e.g. it hasn't been added
     * to the branding yet), this behaves like a plain JPanel -- no crash, no
     * placeholder box, just the default look.
     */
    private static class BackgroundPanel extends JPanel {
        private final Image background;

        BackgroundPanel() {
            setOpaque(true);
            background = loadBackground();
        }

        private Image loadBackground() {
            java.net.URL url = Launcher.class.getResource(BACKGROUND_RESOURCE);
            if (url == null) {
                return null;
            }
            return new ImageIcon(url).getImage();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (background != null) {
                g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
            } else {
                g.setColor(BRAND_BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    /**
     * JLabel que pinta su texto con un halo/resplandor de color detras
     * (varias copias desenfocadas en el color de glow, despues el texto
     * real encima) -- para el titulo grande, que tiene que resaltar arriba
     * de un fondo con mucho detalle.
     */
    private static class GlowLabel extends JLabel {
        private Color glowColor = BRAND_PURPLE;

        GlowLabel(String text) {
            super(text);
        }

        void setGlowColor(Color color) {
            this.glowColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Insets insets = getInsets();
            int x = insets.left;
            int y = insets.top + fm.getAscent();
            String text = getText();

            // Halo tipo neon -- varias pasadas a distinto radio, mas tenue
            // cuanto mas lejos, para que se vea como un resplandor real en
            // vez de un contorno duro.
            for (int radius = 5; radius >= 1; radius--) {
                int alpha = Math.max(30, 200 - radius * 35);
                g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), alpha));
                for (double angle = 0; angle < 360; angle += 45) {
                    int dx = (int) Math.round(radius * Math.cos(Math.toRadians(angle)));
                    int dy = (int) Math.round(radius * Math.sin(Math.toRadians(angle)));
                    g2.drawString(text, x + dx, y + dy);
                }
            }

            g2.setColor(getForeground());
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    /**
     * Return the news panel.
     *
     * @return the news panel
     */
    protected WebpagePanel createNewsPanel() {
        return WebpagePanel.forURL(launcher.getNewsURL(), false);
    }

    private void confirmDelete(Instance instance) {
        if (!SwingHelper.confirmDialog(this,
                tr("instance.confirmDelete", instance.getTitle()), SharedLocale.tr("confirmTitle"))) {
            return;
        }

        ObservableFuture<Instance> future = launcher.getInstanceTasks().delete(this, instance);

        // Update the list of instances after updating
        future.addListener(new Runnable() {
            @Override
            public void run() {
                loadInstances();
            }
        }, SwingExecutor.INSTANCE);
    }

    private void confirmHardUpdate(Instance instance) {
        if (!SwingHelper.confirmDialog(this, SharedLocale.tr("instance.confirmHardUpdate"), SharedLocale.tr("confirmTitle"))) {
            return;
        }

        ObservableFuture<Instance> future = launcher.getInstanceTasks().hardUpdate(this, instance);

        // Update the list of instances after updating
        future.addListener(new Runnable() {
            @Override
            public void run() {
                launch();
                instancesModel.update();
            }
        }, SwingExecutor.INSTANCE);
    }

    private void loadInstances() {
        ObservableFuture<InstanceList> future = launcher.getInstanceTasks().reloadInstances(this);

        future.addListener(new Runnable() {
            @Override
            public void run() {
                instancesModel.update();
                if (instancesTable.getRowCount() > 0) {
                    instancesTable.setRowSelectionInterval(0, 0);
                }
                requestFocus();
            }
        }, SwingExecutor.INSTANCE);

        ProgressDialog.showProgress(this, future, SharedLocale.tr("launcher.checkingTitle"), SharedLocale.tr("launcher.checkingStatus"));
        SwingHelper.addErrorDialogCallback(this, future);
    }

    private void showOptions() {
        ConfigurationDialog configDialog = new ConfigurationDialog(this, launcher);
        configDialog.setVisible(true);
    }

    private void launch() {
        boolean permitUpdate = updateCheck.isSelected();

        if (launcher.getInstances().size() == 0) {
            SwingHelper.showErrorDialog(this, SharedLocale.tr("launcher.noInstanceError"), SharedLocale.tr("launcher.noInstanceTitle"));
            return;
        }

        int row = instancesTable.getSelectedRow();
        Instance instance = launcher.getInstances().get(row >= 0 ? row : 0);

        LaunchOptions options = new LaunchOptions.Builder()
                .setInstance(instance)
                .setListener(new LaunchListenerImpl(this))
                .setUpdatePolicy(permitUpdate ? UpdatePolicy.UPDATE_IF_SESSION_ONLINE : UpdatePolicy.NO_UPDATE)
                .setWindow(this)
                .build();
        launcher.getLaunchSupervisor().launch(options);
    }

    private static class LaunchListenerImpl implements LaunchListener {
        private final WeakReference<LauncherFrame> frameRef;
        private final Launcher launcher;

        private LaunchListenerImpl(LauncherFrame frame) {
            this.frameRef = new WeakReference<LauncherFrame>(frame);
            this.launcher = frame.launcher;
        }

        @Override
        public void instancesUpdated() {
            LauncherFrame frame = frameRef.get();
            if (frame != null) {
                frame.instancesModel.update();
            }
        }

        @Override
        public void gameStarted() {
            LauncherFrame frame = frameRef.get();
            if (frame != null) {
                frame.dispose();
            }
        }

        @Override
        public void gameClosed() {
            Window newLauncherWindow = launcher.showLauncherWindow();
            launcher.getUpdateManager().checkForUpdate(newLauncherWindow);
        }
    }

}
