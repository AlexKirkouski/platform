package lsfusion.client;

import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;

public class CaptureKeyEventsDispatcher implements KeyEventDispatcher, FocusListener {
    private final static CaptureKeyEventsDispatcher instance = new CaptureKeyEventsDispatcher();

    public static CaptureKeyEventsDispatcher get() {
        return instance;
    }

    private Component capture;

    private CaptureKeyEventsDispatcher() {
    }

    private void install() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    private void uninstall() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

    public void setCapture(final Component newCapture) {
        setCapture(newCapture, true);
    }

    public void setCapture(final Component newCapture, boolean releaseOnFocusGained) {
        assert EventQueue.isDispatchThread();

        if (capture != null) {
            capture.removeFocusListener(this);
            if (newCapture == null) {
                uninstall();
            }
        }

        if (newCapture != null) {
            if (releaseOnFocusGained) {
                newCapture.addFocusListener(this);
            }
            if (capture == null) {
                install();
            }
        }

        capture = newCapture;
    }

    public void releaseCapture() {
        setCapture(null);
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (e.getSource() == capture) {
            releaseCapture();
        }
    }

    @Override
    public void focusLost(FocusEvent e) { }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (capture != null) {
            if (!capture.isDisplayable()) {
                releaseCapture();
            } else {
                e.setSource(capture);
            }
        }
        return false;
    }
}
