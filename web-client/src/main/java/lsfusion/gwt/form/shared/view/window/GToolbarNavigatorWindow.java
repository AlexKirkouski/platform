package lsfusion.gwt.form.shared.view.window;

import com.google.gwt.user.client.ui.HasAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import lsfusion.gwt.form.client.navigator.GINavigatorController;
import lsfusion.gwt.form.client.navigator.GNavigatorView;
import lsfusion.gwt.form.client.navigator.GToolbarNavigatorView;

public class GToolbarNavigatorWindow extends GNavigatorWindow {
    public static final float TOP_ALIGNMENT = 0.0f;
    public static final float CENTER_ALIGNMENT = 0.5f;
    public static final float BOTTOM_ALIGNMENT = 1.0f;
    public static final float LEFT_ALIGNMENT = 0.0f;
    public static final float RIGHT_ALIGNMENT = 1.0f;

    public static final int CENTER  = 0;
    public static final int TOP     = 1;
    public static final int LEFT    = 2;
    public static final int BOTTOM  = 3;
    public static final int RIGHT   = 4;

    public int type;
    public boolean showSelect;

    public int verticalTextPosition;
    public int horizontalTextPosition;

    public int verticalAlignment;
    public int horizontalAlignment;

    public float alignmentY;
    public float alignmentX;

    public boolean isVertical() {
        return type == 1;
    }

    public boolean isHorizontal() {
        return type == 0;
    }

    @Override
    public GNavigatorView createView(GINavigatorController navigatorController) {
        return new GToolbarNavigatorView(this, navigatorController);
    }

    public HasVerticalAlignment.VerticalAlignmentConstant getAlignmentY() {
        if (alignmentY == CENTER_ALIGNMENT) {
            return HasVerticalAlignment.ALIGN_MIDDLE;
        } else if (alignmentY == BOTTOM_ALIGNMENT) {
            return HasVerticalAlignment.ALIGN_BOTTOM;
        }
        return HasVerticalAlignment.ALIGN_TOP;
    }

    public HasAlignment.HorizontalAlignmentConstant getAlignmentX() {
        if (alignmentX == CENTER_ALIGNMENT) {
            return HasAlignment.ALIGN_CENTER;
        } else if (alignmentX == RIGHT_ALIGNMENT) {
            return HasAlignment.ALIGN_RIGHT;
        }
        return HasAlignment.ALIGN_LEFT;
    }

    public boolean hasVerticalTextPosition() {
        return verticalTextPosition == BOTTOM;
    }
}
