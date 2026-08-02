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
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
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
    private final JToggleButton packProToggle = new JToggleButton();
    private final JToggleButton packLiteToggle = new JToggleButton();

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
    private final JPanel resizeGrip = new ResizeGripPanel();
    private Point dragOffset;
    private Point resizeStartMouse;
    private Dimension resizeStartSize;

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
        setResizable(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(700, 500));
        initComponents();
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

        // Ventana sin decoracion -> sin esquinas de resize del sistema operativo.
        // Reaplica el shape redondeado cada vez que cambia de tamano (si no, el
        // "recorte" redondeado se queda con las medidas viejas) y muestra la
        // resize grip flotando en la esquina inferior derecha.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                positionResizeGrip();
                // Sin esto, al arrastrar el grip quedaban restos de texto/fondo
                // viejos pintados encima del nuevo layout -- Swing no siempre
                // vuelve a pintar TODO el area despues de un resize manual de
                // una ventana sin decoracion, revalidate+repaint fuerza que se
                // recalcule y repinte todo de cero.
                Container content = getContentPane();
                content.revalidate();
                content.repaint();
            }
        });
        getLayeredPane().add(resizeGrip, JLayeredPane.PALETTE_LAYER);
        resizeGrip.setSize(16, 16);
        positionResizeGrip();
        wireResizeGrip();

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
     * Panel central: titulo grande en fuente pixel + selector de pack
     * (Lite/Pro) + descripcion/eventos reales del server.
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

        JPanel topSection = new JPanel();
        topSection.setOpaque(false);
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.add(headerBox);
        topSection.add(createPackSelector());

        webView = createNewsPanel();
        webView.setOpaque(false);
        styleGlassPanel(webView);

        panel.add(topSection, BorderLayout.NORTH);
        panel.add(webView, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Selector de pack Lite/Pro -- dos "cards" grandes y clickeables (no un
     * combo chico) porque el pedido explicito fue que se note bien cual
     * esta eligiendo el jugador antes de apretar Jugar. Usa
     * BasicToggleButtonUI en vez del look nativo de Windows por la misma
     * razon que el resto de los botones tematizados: un ToggleButton con
     * Border propio + setBackground se pinta blanco/plano con
     * WindowsButtonUI si no se fuerza el UI basico.
     */
    private JPanel createPackSelector() {
        ButtonGroup group = new ButtonGroup();
        group.add(packProToggle);
        group.add(packLiteToggle);

        stylePackCard(packProToggle, SharedLocale.tr("launcher.packProTitle"), SharedLocale.tr("launcher.packProDesc"));
        stylePackCard(packLiteToggle, SharedLocale.tr("launcher.packLiteTitle"), SharedLocale.tr("launcher.packLiteDesc"));

        if ("pokeworld_lite".equals(launcher.getConfig().getSelectedPack())) {
            packLiteToggle.setSelected(true);
        } else {
            packProToggle.setSelected(true);
        }

        ActionListener onPick = ev -> {
            String pack = packLiteToggle.isSelected() ? "pokeworld_lite" : "pokeworld";
            Configuration config = launcher.getConfig();
            config.setSelectedPack(pack);
            Persistence.commitAndForget(config);
        };
        packProToggle.addActionListener(onPick);
        packLiteToggle.addActionListener(onPick);

        JLabel hint = new JLabel(SharedLocale.tr("launcher.packChooseHint"));
        hint.setForeground(new Color(0xff, 0xc9, 0x4d));
        hint.setFont(hint.getFont().deriveFont(Font.BOLD, 12f));
        hint.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));

        JPanel cardsRow = new JPanel(new GridLayout(1, 2, 10, 0));
        cardsRow.setOpaque(false);
        cardsRow.add(packProToggle);
        cardsRow.add(packLiteToggle);
        cardsRow.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(hint, BorderLayout.NORTH);
        container.add(cardsRow, BorderLayout.CENTER);

        return container;
    }

    private void stylePackCard(JToggleButton toggle, String title, String description) {
        toggle.setText("<html><div style='text-align:center; width:170px;'><b style='font-size:13px;'>"
                + title + "</b><br><span style='font-size:11px;'>" + description + "</span></div></html>");
        toggle.setForeground(Color.WHITE);
        toggle.setFocusPainted(false);
        toggle.setPreferredSize(new Dimension(200, 68));
        toggle.setUI(new BasicToggleButtonUI());
        toggle.setOpaque(true);
        toggle.setBackground(new Color(30, 18, 46, 220));
        toggle.setBorder(BorderFactory.createLineBorder(PANEL_BORDER, 1));

        toggle.addChangeListener(ev -> {
            if (toggle.isSelected()) {
                toggle.setBackground(BRAND_PURPLE.darker());
                toggle.setBorder(BorderFactory.createLineBorder(BRAND_PURPLE, 2));
            } else {
                toggle.setBackground(new Color(30, 18, 46, 220));
                toggle.setBorder(BorderFactory.createLineBorder(PANEL_BORDER, 1));
            }
        });
    }

    /**
     * Panel del centro con la imagen de Mewtwo/eventos de fondo (la que
     * mando el usuario), atras del titulo y del panel de noticias -- que el
     * HTML tenga fondo semitransparente en vez de solido es lo que deja ver
     * esta imagen sin perder la lectura del texto.
     */
    private static class DescriptionBackgroundPanel extends JPanel {
        private final Image background;
        // Cachea la version ya escalada al tamano del panel -- sin esto,
        // Java2D reescalaba la imagen fuente (1920x1080) de cero en CADA
        // repintado (arrastrar la ventana, parpadeo del cursor en el campo
        // de texto, hover de botones, etc.), que es carisimo y se sentia
        // como lag general del launcher pese a que la ventana no cambia de
        // tamano nunca (setResizable(false)).
        private BufferedImage scaledCache;
        private int cachedWidth = -1;
        private int cachedHeight = -1;

        DescriptionBackgroundPanel() {
            setOpaque(true);
            java.net.URL url = Launcher.class.getResource("description_bg.jpg");
            background = url != null ? new ImageIcon(url).getImage() : null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (background != null) {
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0 && (w != cachedWidth || h != cachedHeight)) {
                    scaledCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = scaledCache.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(background, 0, 0, w, h, null);
                    g2.setColor(new Color(10, 6, 18, 90));
                    g2.fillRect(0, 0, w, h);
                    g2.dispose();
                    cachedWidth = w;
                    cachedHeight = h;
                }
                g.drawImage(scaledCache, 0, 0, null);
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

        styleThemedButton(discordButton, "btn_discord.png", 240, 129, 0.63);
        styleThemedButton(optionsButton, "btn_options.png", 240, 140, 0.65);
        styleThemedButton(launchButton, "btn_play.png", 260, 121, 0.69);
        // Ajuste fino: en Discord y Jugar el texto quedaba 1px corrido a la
        // izquierda del centro real de la placa (no es simetrica pixel a pixel).
        discordButton.putClientProperty("textXOffset", 1);
        launchButton.putClientProperty("textXOffset", 1);

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
    private void positionResizeGrip() {
        resizeGrip.setLocation(getWidth() - resizeGrip.getWidth() - 4, getHeight() - resizeGrip.getHeight() - 4);
    }

    /**
     * La ventana es undecorated (sin marco de Windows), asi que no hay
     * esquina de resize nativa -- esta es la propia, arrastrando desde la
     * esquina inferior derecha.
     */
    private void wireResizeGrip() {
        resizeGrip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        resizeGrip.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                resizeStartMouse = e.getLocationOnScreen();
                resizeStartSize = getSize();
            }
        });
        resizeGrip.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeStartMouse == null) return;
                Point current = e.getLocationOnScreen();
                int dw = current.x - resizeStartMouse.x;
                int dh = current.y - resizeStartMouse.y;
                Dimension min = getMinimumSize();
                int newWidth = Math.max(min.width, resizeStartSize.width + dw);
                int newHeight = Math.max(min.height, resizeStartSize.height + dh);
                setSize(newWidth, newHeight);
            }
        });
    }

    /**
     * Grip visual chico -- unas lineas diagonales, como el resize handle de
     * cualquier ventana nativa -- para que se note que ahi se puede agrandar.
     */
    private static class ResizeGripPanel extends JPanel {
        ResizeGripPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(255, 255, 255, 120));
            int w = getWidth();
            int h = getHeight();
            for (int i = 1; i <= 3; i++) {
                int offset = i * 4;
                g2.drawLine(w - offset, h - 1, w - 1, h - offset);
            }
            g2.dispose();
        }
    }

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
        styleThemedButton(button, resourceName, width, height, 0.64);
    }

    private void styleThemedButton(JButton button, String resourceName, int width, int height, double plateCenterRatio) {
        Icon icon = SwingHelper.createIcon(Launcher.class, resourceName, width, height);
        button.setIcon(icon);
        button.setText(button.getText().toUpperCase());
        // Donde cae verticalmente el centro de la "placa" vacia del arte de
        // ESTE boton en particular -- se midio por pixeles, no es igual en
        // los 3 (btn_play es mas ancho/chato que los otros dos).
        button.putClientProperty("plateCenterRatio", plateCenterRatio);
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
            // El ratio (en vez de 0.5 = medio exacto) es porque la "placa" vacia
            // del arte no esta en el medio de la imagen -- el Pokemon ocupa el
            // tercio de arriba. Se midio por pixeles para cada boton (variable
            // "plateCenterRatio"), no es el mismo numero en los 3.
            Object ratioProp = b.getClientProperty("plateCenterRatio");
            double ratio = ratioProp instanceof Double ? (Double) ratioProp : 0.6;
            Object xOffsetProp = b.getClientProperty("textXOffset");
            int xOffset = xOffsetProp instanceof Integer ? (Integer) xOffsetProp : 0;
            int x = (b.getWidth() - fm.stringWidth(text)) / 2 + xOffset;
            int y = (int) (b.getHeight() * ratio) + fm.getAscent() / 2 - fm.getDescent() / 2;

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
        // Mismo motivo que en DescriptionBackgroundPanel -- cachear el
        // escalado en vez de reescalar la imagen fuente en cada repintado.
        private BufferedImage scaledCache;
        private int cachedWidth = -1;
        private int cachedHeight = -1;

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
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0 && (w != cachedWidth || h != cachedHeight)) {
                    scaledCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = scaledCache.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(background, 0, 0, w, h, null);
                    g2.dispose();
                    cachedWidth = w;
                    cachedHeight = h;
                }
                g.drawImage(scaledCache, 0, 0, null);
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

        Instance instance = findInstanceByName(launcher.getConfig().getSelectedPack());
        if (instance == null) {
            int row = instancesTable.getSelectedRow();
            instance = launcher.getInstances().get(row >= 0 ? row : 0);
        }

        LaunchOptions options = new LaunchOptions.Builder()
                .setInstance(instance)
                .setListener(new LaunchListenerImpl(this))
                .setUpdatePolicy(permitUpdate ? UpdatePolicy.UPDATE_IF_SESSION_ONLINE : UpdatePolicy.NO_UPDATE)
                .setWindow(this)
                .build();
        launcher.getLaunchSupervisor().launch(options);
    }

    private Instance findInstanceByName(String name) {
        if (name == null) return null;
        for (Instance instance : launcher.getInstances().getInstances()) {
            if (name.equals(instance.getName())) {
                return instance;
            }
        }
        return null;
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
