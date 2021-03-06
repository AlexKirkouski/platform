package lsfusion.gwt.form.shared.view;

import lsfusion.gwt.base.client.ui.FlexPanel;
import lsfusion.gwt.base.client.ui.GAlignment;

import java.util.ArrayList;
import java.util.List;

import static lsfusion.gwt.form.shared.view.GContainerType.*;

public class GContainer extends GComponent {
    public String caption;
    public String description;

    public GContainerType type;
    public GAlignment childrenAlignment;

    public int columns;

    public ArrayList<GComponent> children = new ArrayList<>();

    @Override
    public String toString() {
        return "GContainer" +
                "[" + sID + "]" +
                "[" + type + "]{" +
                "caption='" + caption + '\'' +
                ", alignment=" + getAlignment() +
                '}';
    }

    public FlexPanel.Justify getFlexJustify() {
        switch (childrenAlignment) {
            case START: return FlexPanel.Justify.START;
            case CENTER: return FlexPanel.Justify.CENTER;
            case END: return FlexPanel.Justify.END;
        }
        throw new IllegalStateException("Unknown alignment");
    }

    public boolean isTabbed() {
        return type == TABBED_PANE;
    }

    public int getFlexCount() {
        int count = 0;
        for(GComponent child : children)
            if(child.getFlex() > 0)
                count++;
        return count;
    }

    public boolean isSplit() {
        return type == HORIZONTAL_SPLIT_PANE || type == VERTICAL_SPLIT_PANE;
    }

    public boolean isScroll() {
        return type == SCROLL;
    }

    public List<GGrid> getAllGrids() {
        List<GGrid> grids = new ArrayList<>();
        for (GComponent child : children) {
            if (child instanceof GGrid) {
                grids.add((GGrid) child);
            } else if (child instanceof GContainer) {
                grids.addAll(((GContainer) child).getAllGrids());
            }
        }
        return grids;
    }

    public List<GTreeGroup> getAllTreeGrids() {
        List<GTreeGroup> grids = new ArrayList<>();
        for (GComponent child : children) {
            if (child instanceof GTreeGroup) {
                grids.add((GTreeGroup) child);
            } else if (child instanceof GContainer) {
                grids.addAll(((GContainer) child).getAllTreeGrids());
            }
        }
        return grids;
    }

    public List<GPropertyDraw> getAllPropertyDraws() {
        List<GPropertyDraw> draws = new ArrayList<>();
        for (GComponent child : children) {
            if (child instanceof GPropertyDraw) {
                draws.add((GPropertyDraw) child);
            } else if (child instanceof GContainer) {
                draws.addAll(((GContainer) child).getAllPropertyDraws());
            }
        }
        return draws;
    }

    public GContainer findContainerByID(int id) {
        if (id == this.ID) return this;
        for (GComponent comp : children) {
            if (comp instanceof GContainer) {
                GContainer result = ((GContainer) comp).findContainerByID(id);
                if (result != null) return result;
            }
        }
        return null;
    }

    public boolean isSplitVertical() {
        return type == VERTICAL_SPLIT_PANE;
    }

    public boolean isSplitHorizontal() {
        return type == HORIZONTAL_SPLIT_PANE;
    }

    public boolean isVertical() {
        return isLinearVertical() || isSplitVertical();
    }

    public boolean isHorizontal() {
        return isLinearHorizontal() || isSplitHorizontal();
    }

    public boolean isLinearVertical() {
        return type == CONTAINERV;
    }

    public boolean isLinearHorizontal() {
        return type == CONTAINERH;
    }

    public boolean isLinear() {
        return isLinearVertical() || isLinearHorizontal();
    }

    public boolean isColumns() {
        return type == COLUMNS;
    }
}
