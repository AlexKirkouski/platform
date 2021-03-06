package lsfusion.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import lsfusion.base.BaseUtils;
import lsfusion.base.ERunnable;
import lsfusion.base.IOUtils;
import lsfusion.base.SystemUtils;
import lsfusion.client.form.TableTransferHandler;
import lsfusion.client.form.layout.ClientFormLayout;
import lsfusion.client.logics.ClientGroupObject;
import lsfusion.interop.KeyStrokes;
import lsfusion.utils.WriteUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jdesktop.swingx.SwingXUtilities;
import org.jfree.ui.ExtensionFileFilter;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static lsfusion.client.ClientResourceBundle.getString;

public class SwingUtils {

    private static Map<String, Icon> icons = new HashMap<>();

    public static void addFocusTraversalKey(Component comp, int id, KeyStroke key) {
        Set keys = comp.getFocusTraversalKeys(id);
        Set newKeys = new HashSet(keys);
        newKeys.add(key);
        comp.setFocusTraversalKeys(id, newKeys);
    }

    public static void removeFocusable(Container container) {
        container.setFocusable(false);
        for (Component comp : container.getComponents()) {
            comp.setFocusable(false);
            if (comp instanceof Container) {
                removeFocusable((Container) comp);
            }
        }
    }

    public static Window getWindow(Component comp) {

        while (comp != null && !(comp instanceof Window)) {
            comp = comp.getParent();
        }

        return comp == null ? Main.frame : (Window) comp;
    }

    public static void assertDispatchThread() {
        Preconditions.checkState(EventQueue.isDispatchThread(), "should be executed in dispatch thread");
    }

    public static Point computeAbsoluteLocation(Component comp) {
        Point result = new Point(0, 0);
        SwingUtilities.convertPointToScreen(result, comp);
        return result;
    }

    public static Point translate(Point p, int dx, int dy) {
        Point np = new Point(p);
        np.translate(dx, dy);
        return np;
    }

    public static void invokeLater(final ERunnable runnable) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    Throwables.propagate(t);
                }
            }
        });
    }

    public static void invokeAndWait(final ERunnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    Throwables.propagate(t);
                }
            }
        });
    }

    private final static WeakHashMap<String, SingleActionTimer> timers = new WeakHashMap<>();

    public static void invokeLaterSingleAction(final String actionID, final ActionListener actionListener, int delay) {
        stopSingleAction(actionID, false);

        if (actionListener != null) {
            SingleActionTimer timer = SingleActionTimer.create(actionID, delay, actionListener);

            timers.put(actionID, timer);

            timer.start();
        }
    }

    public static void stopSingleAction(String actionID, boolean execute) {
        SingleActionTimer timer = timers.get(actionID);
        if (timer != null) {
            if (execute) {
                timer.forceExecute();
            } else {
                timer.cancel();
            }
        }
    }

    public static void commitDelayedGroupObjectChange(ClientGroupObject groupObject) {
        if (groupObject != null) {
            SwingUtils.stopSingleAction(groupObject.getActionID(), true);
        }
    }

    public static void cancelDelayedGroupObjectChange(ClientGroupObject groupObject) {
        if (groupObject != null) {
            SwingUtils.stopSingleAction(groupObject.getActionID(), false);
        }
    }

    public static final int YES_BUTTON = 0;
    public static final int NO_BUTTON = 1;

    public static int showConfirmDialog(Component parentComponent, Object message, String title, int messageType, boolean cancel) {
        return showConfirmDialog(parentComponent, message, title, messageType, 0, cancel, 0);
    }
    
    public static int showConfirmDialog(Component parentComponent, Object message, String title, int messageType, boolean cancel, int timeout, int initialValue) {
        return showConfirmDialog(parentComponent, message, title, messageType, initialValue, cancel, timeout);
    }

    public static int showConfirmDialog(Component parentComponent, Object message, String title, int messageType, int initialValue,
                                        boolean cancel, int timeout) {

        Object[] options = {UIManager.getString("OptionPane.yesButtonText"),
                UIManager.getString("OptionPane.noButtonText")};
        if (cancel) {
            options = BaseUtils.add(options, UIManager.getString("OptionPane.cancelButtonText"));
        }

        JOptionPane dialogPane = new JOptionPane(message,
                messageType,
                cancel ? JOptionPane.YES_NO_CANCEL_OPTION : JOptionPane.YES_NO_OPTION,
                null, options, options[initialValue]);

        addFocusTraversalKey(dialogPane, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("RIGHT"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("UP"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("LEFT"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("DOWN"));

        final JDialog dialog = dialogPane.createDialog(parentComponent, title);
        if (timeout != 0) {
            final java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    timer.cancel();
                    dialog.setVisible(false);
                }
            }, timeout);
        }
        dialog.setVisible(true);

        if (dialogPane.getValue() == JOptionPane.UNINITIALIZED_VALUE)
            return initialValue;
        if (dialogPane.getValue() == options[0]) {
            return JOptionPane.YES_OPTION;
        } else {
            if (!cancel || dialogPane.getValue() == options[1])
                return JOptionPane.NO_OPTION;
            else
                return JOptionPane.CANCEL_OPTION;
        }
    }

    // приходится писать свой toString у KeyStroke, поскольку, по умолчанию, используется абсолютно кривой
    public static String getKeyStrokeCaption(KeyStroke editKey) {
        return editKey.toString().replaceAll("typed ", "").replaceAll("pressed ", "").replaceAll("released ", "");
    }

    // запрашивает положение объекта, чтобы он не вылезал за экран
    public static void requestLocation(Window window, Point onScreen) {
        Dimension screen = getUsableDeviceBounds();

        onScreen.x = max(10, min(onScreen.x, screen.width - window.getWidth() - 10));
        onScreen.y = max(10, min(onScreen.y, screen.height - window.getHeight() - 10));
        window.setLocation(onScreen);
    }

    public static Dimension clipDimension(Dimension toClip, Dimension min, Dimension max) {
        return new Dimension(max(min.width, min(max.width, toClip.width)),
                             max(min.height, min(max.height, toClip.height))
        );
    }

    /**
     * обрезает до размеров экрана минус 20 пикселей
     */
    public static Dimension clipToScreen(Dimension toClip) {
        Dimension screen = getUsableDeviceBounds();
        return clipDimension(toClip, new Dimension(0, 0), new Dimension(screen.width, screen.height));
    }

    /**
     * c/p from org.jdesktop.swingx.util.WindowUtils
     */
    public static Dimension getUsableDeviceBounds() {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();

        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle bounds = gc.getBounds();
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= (insets.left + insets.right);
        bounds.height -= (insets.top + insets.bottom);

        return new Dimension(bounds.width, bounds.height);
    }

    public static String toMultilineHtml(String text, Font font) {
        String result = "<html>";
        String line = "";
        FontMetrics fm = SwingUtilities2.getFontMetrics(null, font);
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width - 10;
        String delims = " \n";
        StringTokenizer st = new StringTokenizer(text, delims, true);
        String wordDelim = "";
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (delims.contains(token)) {
                if (token.equals("\n")) {
                    result += line;
                    line = "<br>" + wordDelim;
                    wordDelim = "";
                } else {
                    wordDelim += token;
                }
            } else {
                if (fm.stringWidth(line + wordDelim + token) >= screenWidth) {
                    result += line;
                    result += !line.equals("") ? "<br>" : "";
                    line = "";
                }
                line += wordDelim + token;
                wordDelim = "";
            }
        }
        return result += line + "</html>";
    }

    public static Icon getSystemIcon(String extension) {
        if (icons.containsKey(extension)) {
            return icons.get(extension);
        } else {
            File file = null;
            try {
                file = File.createTempFile("icon", "." + extension);
            } catch (IOException e) {
                e.printStackTrace();
            }
            FileSystemView view = FileSystemView.getFileSystemView();
            Icon icon = view.getSystemIcon(file);
            icons.put(extension, icon);
            //Delete the temporary file
            file.delete();
            return icon;
        }
    }

    public static Dimension overrideSize(Dimension base, Dimension override) {
        if (override != null) {
            if (override.width >= 0) {
                base.width = override.width;
            }
            if (override.height >= 0) {
                base.height = override.height;
            }
        }
        return base;
    }

    public static ClientFormLayout getClientFormLayout(Component comp) {
        while (comp != null) {
            if (comp instanceof ClientFormLayout) {
                return (ClientFormLayout) comp;
            }
            comp = comp.getParent();
        }
        return null;
    }

    public static Window getActiveWindow() {
        return getSelectedWindow(Frame.getFrames());
    }

    public static Window getActiveVisibleWindow() {
        Container selectedWindow = getSelectedWindow(Frame.getFrames());
        while (selectedWindow != null && (!selectedWindow.isVisible())) {
            selectedWindow = selectedWindow.getParent();
        }
        return getWindow(selectedWindow);
    }

    private static Window getSelectedWindow(Window[] windows) {
        for (Window window : windows) {
            if (window.isActive()) {
                return window;
            } else {
                Window[] ownedWindows = window.getOwnedWindows();
                if (ownedWindows != null) {
                    Window selectedWindow = getSelectedWindow(ownedWindows);
                    if (selectedWindow != null) {
                        return selectedWindow;
                    }
                }
            }
        }
        return null;
    }

    /**
     * c/p from javax.swing.plaf.basic.BasicGraphicsUtils#isMenuShortcutKeyDown(java.awt.event.InputEvent)
     */
    public static boolean isMenuShortcutKeyDown(InputEvent event) {
        return (event.getModifiers() &
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0;
    }

    /**
     * c/p from JXTable.isFocusOwnerDescending
     */
    public static boolean isFocusOwnerDescending(Component component) {
        Component focusOwner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return false;
        }
        if (SwingXUtilities.isDescendingFrom(focusOwner, component)) {
            return true;
        }
        Component permanent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return SwingXUtilities.isDescendingFrom(permanent, component);
    }

    public static void setupClientTable(final JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSurrendersFocusOnKeystroke(false);
//        table.setSurrendersFocusOnKeystroke(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        table.getTableHeader().setFocusable(false);
        table.getTableHeader().setReorderingAllowed(false);

        if (table instanceof TableTransferHandler.TableInterface) {
            table.setTransferHandler(new TableTransferHandler((TableTransferHandler.TableInterface) table));
        }

        table.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (table.getEditorComponent() != null) {
                    table.getEditorComponent().requestFocusInWindow();
                }
            }
        });
    }

    public static void setupSingleCellTable(final JTable table) {
        table.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                table.changeSelection(0, 0, false, false);
            }

            public void focusLost(FocusEvent e) {
                table.getSelectionModel().clearSelection();
            }
        });

        // для таблиц с одной ячейкой будем сами мэнэджить перадачу фокуса,
        // это нужно потому, что setFocusTraversalKeys на самом деле используется и для дочерних компонентов,
        // т.е. в случае таблицы - для editorComp
        // из-за этого при использовании этих кнопок во время редактирования фокус переходит в таблицу без окончания редактирования
        table.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, new HashSet<AWTKeyStroke>());
        table.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, new HashSet<AWTKeyStroke>());

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getEnter(), "forward-traversal");
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getTab(), "forward-traversal");
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getCtrlTab(), "forward-traversal");

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getShiftTab(), "backward-traversal");
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStrokes.getCtrlShiftTab(), "backward-traversal");

        table.getActionMap().put("forward-traversal", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
                    return;
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(table);
            }
        });
        table.getActionMap().put("backward-traversal", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
                    return;
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(table);
            }
        });

        table.getColumnModel().setColumnMargin(2);
        table.setRowMargin(2);
        table.setBorder(BorderFactory.createLineBorder(Color.gray));
    }

    public static Border randomBorder() {
        int r = (int) (Math.random() * 256);
        int g = (int) (Math.random() * 256);
        int b = (int) (Math.random() * 256);
        return BorderFactory.createLineBorder(new Color(r, g, b), 2);
    }

    public static boolean isRecursivelyVisible(Component component) {
        return component.isVisible() && (component.getParent() == null || isRecursivelyVisible(component.getParent()));
    }

    private static final class SingleActionTimer extends Timer {
        private boolean stopped = false;

        public SingleActionTimer(int delay, final ActionListener actionListener) {
            super(delay, actionListener);
            setRepeats(false);
        }

        public void cancel() {
            stopped = true;
            stop();
        }

        public void forceExecute() {
            assert getActionListeners().length == 1;
            getActionListeners()[0].actionPerformed(null);
        }

        public static SingleActionTimer create(final String actionID, int delay, final ActionListener actionListener) {
            final SingleActionTimer[] timerHolder = new SingleActionTimer[1];
            ActionListener timerListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!timerHolder[0].stopped) {
                        actionListener.actionPerformed(e);
                        timerHolder[0].cancel();
                    }
                    timers.remove(actionID);
                }
            };

            SingleActionTimer timer = new SingleActionTimer(delay, timerListener);
            timerHolder[0] = timer;
            return timer;
        }
    }

    public static Rectangle getNewBoundsIfNotAlmostEquals(Component comp, int x, int y, int width, int height) {
        Rectangle rect = comp.getBounds();

        rect.x = changeIfNotAlmostEquals(rect.x, x);
        rect.y = changeIfNotAlmostEquals(rect.y, y);
        rect.width = changeIfNotAlmostEquals(rect.width, width);
        rect.height = changeIfNotAlmostEquals(rect.height, height);

        return rect;
    }

    private static int changeIfNotAlmostEquals(int currVal, int newVal) {
        return almostEquals(currVal, newVal) ? currVal : newVal;
    }

    public static boolean almostEquals(int a, int b) {
        return Math.abs(a - b) < 3;
    }

    public static void paintRightBottomCornerTriangle(Graphics2D graphics, int triangleSize, Color color, int x, int y, int w, int h) {
        paintCornerTriangle(graphics, triangleSize, color, x, y, w, h, false, false);
    }
    
    public static void paintCornerTriangle(Graphics2D graphics, int triangleSize, Color color, int x, int y, int w, int h, boolean left, boolean top) {
        int compRight = x + w;
        int compBottom = y + h;
        
        int[] xs;
        int[] ys;

        if (left) {
            xs = new int[]{x + triangleSize, x, x};
            ys = top ? new int[]{y, y, y + triangleSize} : new int[]{compBottom, compBottom, compBottom - triangleSize};
        } else {
            xs = new int[]{compRight, compRight, compRight - triangleSize};
            ys = top ? new int[]{y, y + triangleSize, y} : new int[]{compBottom - triangleSize, compBottom, compBottom};
        }

        Polygon polygon = new Polygon(xs, ys, 3);

        graphics.setColor(color);
        graphics.fillPolygon(polygon);
    }

    private static Class<?> tooltipListenerClass;
    private static KeyStroke closeTooltipKeyStroke;
    
    private static boolean tryToInitTooltipStuff() {
        if (tooltipListenerClass == null || closeTooltipKeyStroke == null) {
            for (Class<?> declaredClass : ToolTipManager.class.getDeclaredClasses()) {
                if (declaredClass.getCanonicalName().equals("javax.swing.ToolTipManager.AccessibilityKeyListener")) {
                    tooltipListenerClass = declaredClass;
                    break;
                }
            }

            try {
                Field hideTip = ToolTipManager.class.getDeclaredField("hideTip");
                if (hideTip != null) {
                    hideTip.setAccessible(true);
                    closeTooltipKeyStroke = (KeyStroke) hideTip.get(ToolTipManager.sharedInstance());
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}    
        }
        return tooltipListenerClass != null && closeTooltipKeyStroke != null;
    }
    
    /**
     * Все компоненты, для которых показывается всплывающая подсказка, регистрируются в <code>ToolTipManager</code> 
     * ({@link javax.swing.ToolTipManager#registerComponent(JComponent)}), который добавляет им свои <code>MouseMotionListener</code> 
     * и <code>KeyListener</code>. <code>KeyListener</code> оповещается глобально каждым компонентом, у которого он есть, 
     * при возникновении любого события клавиатуры в {@link Component#processKeyEvent(KeyEvent)}. 
     * <p>
     * На время обработки нажатия клавиши Escape отключаем этот listener для некоторых focusable компонентов формы. Делалось, 
     * чтобы не нажимать Escape дважды для закрытия модальной формы, а также отмены редактирования дат и закрытия панели отбора
     * при показанной подсказке. 
     */
    public static void getAroundTooltipListener(JComponent component, KeyEvent event, Runnable process) {
        boolean init = tryToInitTooltipStuff();
        
        if (init && closeTooltipKeyStroke.equals(KeyStroke.getKeyStrokeForEvent(event))) {
            KeyListener tooltipListener = null;

            KeyListener[] keyListeners = component.getKeyListeners();
            for (KeyListener keyListener : keyListeners) {
                if (tooltipListenerClass.isAssignableFrom(keyListener.getClass())) {
                    tooltipListener = keyListener;
                    component.removeKeyListener(keyListener);
                }
            }

            process.run();

            if (tooltipListener != null) {
                component.addKeyListener(tooltipListener);
            }
        } else {
            process.run();
        }
    }

    public static void showSaveFileDialog(Map<String, byte[]> files) {
        showSaveFileDialog(files, false, false);
    }

    public static void showSaveFileDialog(Map<String, byte[]> files, boolean noDialog, boolean append) {
        try {
            if (noDialog) {
                for (Map.Entry<String, byte[]> fileEntry : files.entrySet()) {
                    writeFile(fileEntry.getKey(), fileEntry.getValue(), append);
                }
            } else {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setCurrentDirectory(SystemUtils.loadCurrentDirectory());
                boolean singleFile;
                fileChooser.setAcceptAllFileFilterUsed(false);
                if (files.size() > 1) {
                    singleFile = false;
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                } else {
                    singleFile = true;
                    File file = new File(files.keySet().iterator().next());
                    fileChooser.setSelectedFile(file);
                    String extension = BaseUtils.getFileExtension(file);
                    if (!BaseUtils.isRedundantString(extension)) {
                        ExtensionFileFilter filter = new ExtensionFileFilter("." + extension, extension);
                        fileChooser.addChoosableFileFilter(filter);
                    }
                }
                if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    String path = fileChooser.getSelectedFile().getAbsolutePath();
                    for (String file : files.keySet()) {
                        if (singleFile) {
                            File file1 = new File(path);
                            if (file1.exists()) {
                                int answer = showConfirmDialog(fileChooser, getString("layout.menu.file.already.exists.replace"),
                                        getString("layout.menu.file.already.exists"), JOptionPane.QUESTION_MESSAGE, false);
                                if (answer == JOptionPane.YES_OPTION) {
                                    IOUtils.putFileBytes(file1, files.get(file));
                                }
                            } else {
                                IOUtils.putFileBytes(file1, files.get(file));
                            }
                        } else {
                            IOUtils.putFileBytes(new File(path + "\\" + file), files.get(file));
                        }
                    }
                    SystemUtils.saveCurrentDirectory(!singleFile ? new File(path) : new File(path.substring(0, path.lastIndexOf("\\"))));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeFile(String filePath, byte[] fileBytes, boolean append) throws IOException {
        if (append) {
            String extension = BaseUtils.getFileExtension(filePath);
            switch (extension) {
                case "csv":
                    if (new File(filePath).exists()) {
                        Files.write(Paths.get(filePath), fileBytes, StandardOpenOption.APPEND);
                    } else {
                        IOUtils.putFileBytes(new File(filePath), fileBytes);
                    }
                    break;
                case "xls": {
                    File file = new File(filePath);
                    if (file.exists()) {
                        HSSFWorkbook sourceWB = new HSSFWorkbook(new ByteArrayInputStream(fileBytes));
                        HSSFWorkbook destinationWB = new HSSFWorkbook(new FileInputStream(file));
                        WriteUtils.copyHSSFSheets(sourceWB, destinationWB);

                        try (FileOutputStream fos = new FileOutputStream(new File(filePath))) {
                            destinationWB.write(fos);
                        }

                    } else {
                        IOUtils.putFileBytes(new File(filePath), fileBytes);
                    }
                    break;
                }
                case "xlsx":
                    File file = new File(filePath);
                    if (file.exists()) {
                        XSSFWorkbook sourceWB = new XSSFWorkbook(new ByteArrayInputStream(fileBytes));
                        XSSFWorkbook destinationWB = new XSSFWorkbook(new FileInputStream(file));
                        WriteUtils.copyXSSFSheets(sourceWB, destinationWB);

                        try (FileOutputStream fos = new FileOutputStream(new File(filePath))) {
                            destinationWB.write(fos);
                        }

                    } else {
                        IOUtils.putFileBytes(new File(filePath), fileBytes);
                    }
                    break;
                default:
                    throw new RuntimeException("APPEND is supported only for csv, xls, xlsx files");
            }
        } else {
            IOUtils.putFileBytes(new File(filePath), fileBytes);
        }
    }

    // возвращает новую flexWidth
    private static double reducePrefsToBase(double prevFlexWidth, int column, double[] prefs, double[] flexes, int[] basePrefs) {
        double reduce = prefs[column] - basePrefs[column];
        assert greaterEquals(reduce, 0.0);
        if(equals(reduce, 0.0))
            return prevFlexWidth;

        double newFlexWidth = prevFlexWidth + reduce;
        double newTotalFlexes = 0.0;
        double prevTotalFlexes = 0.0;
        for(int i=0;i<prefs.length;i++) {
            if(i!=column) {
                double prevFlex = flexes[i];
                double newFlex = prevFlex * prevFlexWidth / newFlexWidth;
                flexes[i] = newFlex;
                newTotalFlexes += newFlex;
                prevTotalFlexes += prevFlex;
            }
        }
        assert newTotalFlexes < prevTotalFlexes;
        flexes[column] += prevTotalFlexes - newTotalFlexes;
        prefs[column] = basePrefs[column];
        return newFlexWidth;
    }

    private static boolean greater(double a, double b) {
        return a - b > 0.001;
    }
    private static boolean greaterEquals(double a, double b) {
        return a - b > -0.001;
    }
    private static boolean equals(double a, double b) {
        return Math.abs(a - b) < 0.001;
    }

    // prefs на double'ах чтобы не "дрожало", из-за преобразований в разные стороны (строго говоря наверное без adjustTableFixed и overflow дрожать не будет)
    public static void calculateNewFlexes(int column, int delta, int viewWidth, double[] prefs, double[] flexes, int[] basePrefs, double[] baseFlexes, boolean overflow) {
        // проблема в том, что в desktop-client'е вся resizing model построена что у колонки не может увеличиться размер за счет левой части (так как колонке сразу же выставляется delta расположения мышки и получается что одна и та же delta применяется по многу раз, по этой же причине в desktop-client'е не поддерживается resizing fixed колонок) 
        boolean removeLeftPref = false; // вообще так как removeLeftFlex false, логично иметь симметричное поведение, но больше не меньше (removeRightPref и add*Pref не имеют смысла, так как вся delta просто идет в pref колонки)

        // ищем первую динамическую компоненту слева (она должна получить +delta, соответственно правая часть -delta)
        // тут есть варианты -delta идет одной правой колонке, или всем правых колонок, но так как
        // a) так как выравнивание по умолчанию левое, интуитивно при перемещении изменяют именно размер левой колонки, б) так как есть де-факто ограничение Preferred, вероятность получить нужный размер уменьшая все колонки куда выше
        // будем распределять между всеми правыми колонками

        // находим левую flex
        while(column >= 0 && baseFlexes[column] == 0)
            column--;
        if(column < 0) // нет левой flex колонки - ничего не делаем
            return;

        int rightFlex = column + 1;
        while(rightFlex < baseFlexes.length && baseFlexes[rightFlex] == 0)
            rightFlex++;
        if(rightFlex >= baseFlexes.length) // не нашли правй flex - ничего не делаем
            return;        

        // считаем общий текущий preferred
        double totalPref = 0;
        for (double pref : prefs) {
            totalPref += pref;
        }

        // сначала списываем delta справа налево pref (но не меньше basePref), ПОКА сумма pref > viewWidth !!! ( то есть flex не работает, работает ширина контейнера или minTableWidth в таблице)
        // тут можно было бы если идет расширение - delta > 0.0, viewWidth приравнять totalPref (соответственно запретить adjust, то есть pref'ы остались такими же) и reduce'ить остальные, но это пойдет в разрез с уменьшением (когда нужно уменьшать pref'ы иначе в исходное состояние не вернешься), поэтому логичнее исходить из концепции когда если есть scroll тогда просто расширяем колонки, если нет scroll'а пытаемся уместить все без скролла
        double exceedPrefWidth = totalPref - viewWidth;
        if(greater(exceedPrefWidth, 0.0)) {
            if(!overflow)
                return;

            double prefReduceDelta = Math.min(-delta, exceedPrefWidth);
            delta += prefReduceDelta;
            for(int i=column;i>=0;i--) {
                double maxReduce = prefs[i] - basePrefs[i];
                double reduce = Math.min(prefReduceDelta, maxReduce);
                prefs[i] -= reduce;
                prefReduceDelta -= reduce;
                if(!removeLeftPref || equals(prefReduceDelta, 0.0)) // если delta не осталось нет смысла продолжать, у нас либо viewWidth либо уже все расписали
                    break;
            }

            assert greaterEquals(0.0, delta);

            exceedPrefWidth = 0;
        }

        if(delta == 0) // все расписали
            return;

        double flexWidth = -exceedPrefWidth;
        assert greaterEquals(flexWidth, 0.0);

        // можно переходить на basePref - flex (с учетом того что viewWidth может измениться, pref'ы могут быть как равны viewWidth в результате предыдущего шага, так и меньше)
        for(int i=0;i<prefs.length;i++)
            flexWidth = reducePrefsToBase(flexWidth, i, prefs, flexes, basePrefs);

        //если flexWidth все еще равно 0 - вываливаемся (так как нельзя меньше preferred опускаться)
        if(equals(flexWidth,0.0))
            return;

        // запускаем изменение flex'а (пропорциональное)
        double totalFlex = 0;
        double totalBaseFlex = 0;
        double totalRightFlexes = 0.0;
        double totalRightBaseFlexes = 0.0;
        for(int i=0;i<flexes.length;i++) {
            double flex = flexes[i];
            double baseFlex = baseFlexes[i];
            if(i>column) {
                totalRightFlexes += flex;
                totalRightBaseFlexes += baseFlex;
            }
            totalFlex += flex;
            totalBaseFlex += baseFlex;
        }

        // flex колонки увеличиваем на нужную величину, соответственно остальные flex'ы надо уменьшить на эту величину
        double toAddFlex = (double) delta * totalFlex / (double) flexWidth;
        if(greater(0.0, toAddFlex + flexes[column])) // не shrink'аем, но и левые столбцы не уменьшаются (то есть removeLeftFlex false)
            toAddFlex = -flexes[column];

        // сначала уменьшаем правые flex'ы
        double restFlex = 0.0;
        double toAddRightFlex = toAddFlex;
        if(toAddRightFlex > totalRightFlexes) {
            restFlex = toAddRightFlex - totalRightFlexes;
            toAddRightFlex = totalRightFlexes;
        }
        for(int i=column+1;i<flexes.length;i++) {
            if(greater(totalRightFlexes, 0.0))
                flexes[i] -= flexes[i] * toAddRightFlex / totalRightFlexes;
            else {
                assert equals(flexes[i], 0.0);
                flexes[i] = - baseFlexes[i] * toAddRightFlex / totalRightBaseFlexes;
            }
        }


        // может остаться delta, тогда раскидываем ее для левых компонент
        boolean addLeftFlex = !overflow; // (если не overflow, потому как в противном случае все же не очень естественное поведение)
        if(addLeftFlex && greater(restFlex, 0.0)) {
            double totalLeftFlexes = totalFlex - totalRightFlexes - flexes[column];
            double totalLeftBaseFlexes = totalBaseFlex - totalRightBaseFlexes - baseFlexes[column];

            double toAddLeftFlex = restFlex; // надо изменять preferred - то есть overflow'ить / добавлять scroll по сути
            restFlex = 0.0;
            if(toAddLeftFlex > totalLeftFlexes) {
                restFlex = toAddLeftFlex - totalLeftFlexes;
                toAddLeftFlex = totalLeftFlexes;
            }
            for(int i=0;i<column;i++) {
                if(greater(totalLeftFlexes, 0.0))
                    flexes[i] -= flexes[i] * toAddLeftFlex / totalLeftFlexes;
                else {
                    assert equals(flexes[i], 0.0);
                    flexes[i] = - baseFlexes[i] * toAddLeftFlex / totalLeftBaseFlexes;
                }
            }
        }

        toAddFlex = toAddFlex - restFlex;
        flexes[column] += toAddFlex;

        // если и так осталась, то придется давать preferred (соответственно flex не имеет смысла) и "здравствуй" scroll
        if(greater(restFlex, 0.0)) {
            assert !addLeftFlex || equals(flexes[column], totalFlex); // по сути записываем все в эту колонку
            if(overflow) {
                if(!addLeftFlex) {
                    for (int i = 0; i < column; i++)
                        prefs[i] += flexWidth * flexes[i] / totalFlex;
                }
                prefs[column] += flexWidth * ((flexes[column] + restFlex) / totalFlex);
            }
        }
    }

    private static void adjustFlexesToFixedTableLayout(int viewWidth, double[] prefs, boolean[] flexes, double[] flexValues) {
        double minRatio = Double.MAX_VALUE;
        double totalPref = 0;
        double totalFlexValues = 0.0;
        for(int i=0;i<prefs.length;i++) {
            if(flexes[i]) {
                double ratio = flexValues[i] / prefs[i];
                minRatio = Math.min(minRatio, ratio);
                totalFlexValues += flexValues[i];
            }
            totalPref += prefs[i];
        }
        double flexWidth = Math.max((double)viewWidth - totalPref, 0.0);
        for(int i=0;i<prefs.length;i++) {
            if(flexes[i])
                prefs[i] = (prefs[i] + flexWidth * flexValues[i] / totalFlexValues) / (1.0 + flexWidth * minRatio / totalFlexValues);
        }
    }

    // изменяется prefs
    public static void calculateNewFlexesForFixedTableLayout(int column, int delta, int viewWidth, double[] prefs, int[] basePrefs, boolean[] flexes) {
        double[] flexValues = new double[prefs.length];
        double[] baseFlexValues = new double[prefs.length];
        for(int i=0;i<prefs.length;i++) {
            if(flexes[i]) {
                flexValues[i] = prefs[i];
                baseFlexValues[i] = basePrefs[i];
            } else {
                flexValues[i] = 0.0;
                baseFlexValues[i] = 0.0;
            }
        }

        calculateNewFlexes(column, delta, viewWidth, prefs, flexValues, basePrefs, baseFlexValues, true);

        adjustFlexesToFixedTableLayout(viewWidth, prefs, flexes, flexValues);
    }
}
