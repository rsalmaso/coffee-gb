package eu.rekawek.coffeegb.gui;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.CartridgeOptions;
import eu.rekawek.coffeegb.debug.Console;
import eu.rekawek.coffeegb.memory.cart.Cartridge;
import eu.rekawek.coffeegb.serial.SerialEndpoint;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwingGui {

    private static final Logger LOG = LoggerFactory.getLogger(SwingGui.class);

    private static final File PROPERTIES_FILE = new File(new File(System.getProperty("user.home")), ".coffeegb.properties");

    private static final int SCALE = 2;

    private final SwingDisplay display;

    private final SwingController controller;

    private final AudioSystemSoundOutput sound;

    private final CartridgeOptions options;

    private final File initialRom;

    private final Properties properties;

    private final Console console;

    private JFrame mainWindow;

    private Cartridge cart;

    private Gameboy gameboy;

    private boolean isRunning;

    public SwingGui(CartridgeOptions options, boolean debug, File initialRom) throws IOException {
        properties = loadProperties();
        AudioSystemSoundOutput sound = new AudioSystemSoundOutput();
        SwingDisplay display = new SwingDisplay(SCALE, false);
        SwingController controller = new SwingController(properties);

        this.options = options;
        this.display = display;
        this.controller = controller;
        this.sound = sound;
        this.initialRom = initialRom;
        this.console = debug ? new Console() : null;
    }

    public void run() throws Exception {
        System.setProperty("apple.awt.application.name", "Coffee GB");
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(this::startGui);
    }

    public void startEmulation(File rom) {
        Cartridge newCart;
        try {
            newCart = loadRom(rom);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainWindow, e.getMessage(), "Can't load ROM", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (isRunning) {
            stopEmulation();
        }
        cart = newCart;
        mainWindow.setTitle("Coffee GB: " + cart.getTitle());
        gameboy = new Gameboy(cart, display, controller, sound, SerialEndpoint.NULL_ENDPOINT, console);
        gameboy.registerTickListener(new TimingTicker());
        if (console != null) {
            console.setGameboy(gameboy);
        }
        new Thread(display).start();
        new Thread(sound).start();
        new Thread(gameboy).start();
        isRunning = true;
    }

    private void stopEmulation() {
        isRunning = false;
        if (gameboy != null) {
            gameboy.stop();
            gameboy = null;
        }
        if (cart != null) {
            cart.flushBattery();
            cart = null;
        }
        sound.stopThread();
        display.stop();
        if (console != null) {
            console.setGameboy(null);
        }
    }

    private Cartridge loadRom(File rom) throws IOException {
        Cartridge.GameboyType type = Cartridge.GameboyType.AUTOMATIC;
        if (options.isForceCgb()) {
            type = Cartridge.GameboyType.FORCE_CGB;
        }
        if (options.isForceDmg()) {
            type = Cartridge.GameboyType.FORCE_DMG;
        }
        return new Cartridge(rom, options.isSupportBatterySaves(), type, options.isUsingBootstrap());
    }

    private void startGui() {
        display.setPreferredSize(new Dimension(160 * SCALE, 144 * SCALE));

        mainWindow = new JFrame("Coffee GB");

        final JFileChooser fc = new JFileChooser();
        if (properties.containsKey("rom_dir")) {
            fc.setCurrentDirectory(new File(properties.getProperty("rom_dir")));
        }

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem load = new JMenuItem("Load ROM");
        mainWindow.setJMenuBar(menuBar);
        menuBar.add(fileMenu);
        fileMenu.add(load);
        load.addActionListener(actionEvent -> {
            int code = fc.showOpenDialog(load);
            if (code == JFileChooser.APPROVE_OPTION) {
                setProperty("rom_dir", fc.getSelectedFile().getParent());
                startEmulation(fc.getSelectedFile());
            }
        });

        mainWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        mainWindow.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                stopGui();
            }
        });
        mainWindow.setLocationRelativeTo(null);

        mainWindow.setContentPane(display);
        mainWindow.setResizable(false);
        mainWindow.setVisible(true);
        mainWindow.pack();
        mainWindow.addKeyListener(controller);
        if (console != null) {
            new Thread(console).start();
        }
        if (initialRom != null) {
            startEmulation(initialRom);
        }
    }

    private void stopGui() {
        stopEmulation();
        if (console != null) {
            console.stop();
        }
        System.exit(0);
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        if (PROPERTIES_FILE.exists()) {
            try (FileReader reader = new FileReader(PROPERTIES_FILE)) {
                props.load(reader);
            }
        }
        return props;
    }

    private void setProperty(String key, String value) {
        properties.setProperty(key, value);
        try (FileWriter writer = new FileWriter(PROPERTIES_FILE)) {
            properties.store(writer, "");
        } catch (IOException e) {
            LOG.error("Can't store properties", e);
        }
    }
}
