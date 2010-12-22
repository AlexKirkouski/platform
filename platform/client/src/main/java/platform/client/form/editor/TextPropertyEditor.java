package platform.client.form.editor;

import platform.client.form.PropertyEditorComponent;
import platform.interop.ComponentDesign;
import platform.interop.KeyStrokes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.EventObject;


@SuppressWarnings({"FieldCanBeLocal"})
public class TextPropertyEditor extends JScrollPane implements PropertyEditorComponent, PropertyChangeListener {
    private final int WIDTH = 250;
    private final int HEIGHT = 200;
    private String typedText;
    private JTextArea textArea;
    private JDialog dialog;

    private JOptionPane optionPane;

    private String btnString1 = "Применить";
    private String btnString2 = "Отменить";
    private boolean state;

    public TextPropertyEditor(Object value, ComponentDesign design) {
        textArea = new JTextArea((String) value);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);

        getViewport().add(textArea);
        setPreferredSize(new Dimension(200, 200));
        dialog = new JDialog((Frame) null, true);
        textArea.setFont(new Font("Tahoma", Font.PLAIN, 12));
        if (design != null) {
            design.designCell(this);
        }


        String msgString1 = "Текст";
        Object[] array = {msgString1, this};

        Object[] options = {btnString1, btnString2};

        optionPane = new JOptionPane(array,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null,
                options,
                options[0]);

        dialog.setContentPane(optionPane);
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Color.gray, 1));
        optionPane.addPropertyChangeListener(this);
        setFocusable(true);
        textArea.setEditable(false);
        textArea.getCaret().setVisible(true);

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                textArea.requestFocusInWindow();
            }
        });

        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                textArea.setEditable(true);
            }
        }
        );
    }


    public void clearAndHide() {
        dialog.setVisible(false);
    }


    public Component getComponent(Point tableLocation, Rectangle cellRectangle, EventObject editEvent) throws IOException, ClassNotFoundException {
        if (KeyStrokes.isSpaceEvent(editEvent)) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (int) Math.min(tableLocation.getX(), screenSize.getWidth() - WIDTH);
            dialog.setBounds(x, (int) tableLocation.getY(), WIDTH, HEIGHT);
            dialog.setVisible(true);
            textArea.setEditable(true);
            return null;
        } else {
            return this;
        }
    }

    public Object getCellEditorValue() throws RemoteException {
        return textArea.getText();
    }

    public boolean valueChanged() {
        return state;
    }

    public void propertyChange(PropertyChangeEvent e) {
        String prop = e.getPropertyName();

        if (isVisible() && (e.getSource() == optionPane) && (JOptionPane.VALUE_PROPERTY.equals(prop) ||
                JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
            Object value = optionPane.getValue();

            if (value == JOptionPane.UNINITIALIZED_VALUE) {
                //ignore reset
                return;
            }

            optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

            if (btnString1.equals(value)) {
                state = !(textArea.getText().equals(typedText));
                typedText = textArea.getText();
                clearAndHide();
            } else {
                state = false;
                typedText = null;
                clearAndHide();
            }
        }
    }


}
