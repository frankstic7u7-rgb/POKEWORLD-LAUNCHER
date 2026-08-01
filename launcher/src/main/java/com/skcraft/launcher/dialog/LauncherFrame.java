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
    private final JButton refreshButton = new JButton(SharedLocale.tr("launcher.checkForUpdates"));
    private final JButton optionsButton = new JButton(SharedLocale.tr("launcher.options"));
    private final JButton selfUpdateButton = new JButton(SharedLocale.tr("launcher.updateLauncher"));
    private final JCheckBox updateCheck = new JCheckBox(SharedLocale.tr("launcher.downloadUpdates"));
    private final JButton discordButton = new JButton(SharedLocale.tr("launcher.discord"));
    private final JButton donateButton = new JButton(SharedLocale.tr("launcher.donate"));
    private final JSpinner maxMemorySpinner = new JSpinner(new SpinnerNumberModel(2048, 512, 32768, 512));
    private final JLabel onlineCountLabel = new JLabel(" ");

    private static final String DISCORD_URL = "https://discord.gg/Gz2rD4hE6F";
    private static final String DONATE_URL = "https://pokeworld.contetops.com/tienda";
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

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadInstances();
                launcher.getUpdateManager().checkForUpdate(LauncherFrame.this);
                webView.browse(launcher.getNewsURL(), false);
                refreshOnlineCount();
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
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JLabel title = new JLabel(SharedLocale.tr("launcher.welcomeTitle"));
        title.setFont(PixelFont.deriveSize(30f));
        title.setForeground(Color.WHITE);
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
     * Columna vertical de botones grandes a la derecha (Discord, Opciones,
     * Jugar), en vez de la fila chica de antes.
     */
    private JPanel createButtonColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new MigLayout("insets 6, gap 12", "[]", "[][][]push"));

        styleThemedButton(discordButton, "btn_discord.png", 210, 113);
        styleThemedButton(optionsButton, "btn_options.png", 210, 122);
        styleThemedButton(launchButton, "btn_play.png", 230, 107);

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
        styleGlassPanel(panel);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 165, 0, 160)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        JLabel donateTitle = new JLabel(SharedLocale.tr("launcher.donateTitle"));
        donateTitle.setForeground(Color.ORANGE);
        donateTitle.setFont(donateTitle.getFont().deriveFont(Font.BOLD, 15f));

        JLabel donateText = new JLabel(SharedLocale.tr("launcher.donateText"));
        donateText.setForeground(new Color(210, 210, 210));

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
        panel.add(refreshButton);
        panel.add(donateButton);

        styleSecondaryButton(refreshButton);
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
     * que armamos) y el texto centrado encima, sacando el look de boton
     * cuadrado de Swing por defecto -- solo queda visible la imagen.
     */
    private void styleThemedButton(JButton button, String resourceName, int width, int height) {
        Icon icon = SwingHelper.createIcon(Launcher.class, resourceName, width, height);
        button.setIcon(icon);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setFont(PixelFont.deriveSize(15f));
        button.setPreferredSize(new Dimension(width, height));
    }

    /**
     * Para los botones que no tienen su propio arte (Buscar actualizaciones,
     * Actualizar launcher) -- fondo oscuro semitransparente a tono con el
     * resto en vez del blanco por defecto de Swing (que con texto blanco
     * quedaba invisible).
     */
    private void styleSecondaryButton(JButton button) {
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(40, 25, 55, 230));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        button.setFocusPainted(false);
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
