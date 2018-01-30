package lsfusion.gwt.form.client.form.ui.layout;

import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.base.client.Dimension;
import lsfusion.gwt.base.client.ui.FlexPanel;
import lsfusion.gwt.base.client.ui.GFlexAlignment;
import lsfusion.gwt.form.client.form.ui.layout.flex.FlexCaptionPanel;
import lsfusion.gwt.form.client.form.ui.layout.flex.FlexFormLayoutImpl;
import lsfusion.gwt.form.client.form.ui.layout.table.TableCaptionPanel;
import lsfusion.gwt.form.shared.view.GComponent;
import lsfusion.gwt.form.shared.view.GContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static lsfusion.gwt.base.client.GwtClientUtils.calculateMaxPreferredSize;
import static lsfusion.gwt.base.client.GwtClientUtils.enlargeDimension;
import static lsfusion.gwt.base.shared.GwtSharedUtils.relativePosition;

public abstract class GAbstractContainerView {
    protected final GContainer container;
    protected Widget view;

    protected final List<GComponent> children = new ArrayList<>();
    protected final List<Widget> childrenViews = new ArrayList<>();

    protected GAbstractContainerView(GContainer container) {
        this.container = container;
    }

    public void add(GComponent child, final Widget view) {
        assert child != null && view != null && container.children.contains(child);

        int index = relativePosition(child, container.children, children);

        children.add(index, child);
        childrenViews.add(index, view);

        addImpl(index, child, view);

        if(child.autoSize && view instanceof FlexFormLayoutImpl.GridPanel) {
            updateLayoutListeners.add(new UpdateLayoutListener() {
                @Override
                public void updateLayout() {
                    ((FlexFormLayoutImpl.GridPanel)view).autoSize();
                }
            });
        }
    }

    public void remove(GComponent child) {
        int index = children.indexOf(child);
        if (index == -1) {
            throw new IllegalStateException("Child wasn't added");
        }

        children.remove(index);
        Widget view = childrenViews.remove(index);

        removeImpl(index, child, view);
    }

    public boolean hasChild(GComponent child) {
        return children.contains(child);
    }

    public int getChildrenCount() {
        return children.size();
    }

    public GComponent getChild(int index) {
        return children.get(index);
    }

    public Widget getChildView(int index) {
        return childrenViews.get(index);
    }

    public Widget getChildView(GComponent child) {
        int index = children.indexOf(child);
        return index != -1 ? childrenViews.get(index) : null;
    }

    protected Dimension getChildMaxPreferredSize(Map<GContainer, GAbstractContainerView> containerViews, int index) {
        return getChildMaxPreferredSize(containerViews, getChild(index));
    }

    protected Dimension getChildMaxPreferredSize(Map<GContainer, GAbstractContainerView> containerViews, GComponent child) {
        Dimension dimensions = child instanceof GContainer
                               ? getMaxPreferredSize((GContainer) child, containerViews, true)
                               : getMaxPreferredSize(child, getChildView(child));
        Dimension result = enlargeDimension(dimensions, child.getHorizontalMargin(), child.getVerticalMargin());
        GFormLayout.setDebugDimensionsAttributes(getChildView(child), result);
        return result;
    }
    
    public static Dimension getMaxPreferredSize(GContainer child, Map<GContainer, GAbstractContainerView> containerViews, boolean max) {
        return overrideSize(child, containerViews.get(child).getMaxPreferredSize(containerViews), max);
    }
    private static Dimension getMaxPreferredSize(GComponent child, Widget childView) {
        return overrideSize(child, calculateMaxPreferredSize(childView), true);        
    }

    private static Dimension overrideSize(GComponent child, Dimension dimension, boolean max) {
        if(child.height == -1 && child.width == -1) // оптимизация
            return dimension;

        int preferredWidth = child.width;
        if(preferredWidth == -1)
            preferredWidth = dimension.width;
        else if(max)
            preferredWidth = Math.max(preferredWidth, dimension.width);

        int preferredHeight = child.height;
        if(preferredHeight == -1)
            preferredHeight = dimension.height;
        else if(max)
            preferredHeight = Math.max(preferredHeight, dimension.height);
        return new Dimension(preferredWidth, preferredHeight);
    }

    // не предполагает явное использование (так как не содержит проверки на явный size)
    protected Dimension getMaxPreferredSize(Map<GContainer, GAbstractContainerView> containerViews) {
        boolean vertical = container.isVertical();
        int width = 0;
        int height = 0;
        int chCnt = children.size();
        for (int i = 0; i < chCnt; ++i) {
            if (childrenViews.get(i).isVisible()) {
                Dimension childSize = getChildMaxPreferredSize(containerViews, i);
                if (vertical) {
                    width = max(width, childSize.width);
                    height += childSize.height;
                } else {
                    width += childSize.width;
                    height = max(height, childSize.height);
                }
            }
        }

        return addCaptionDimensions(new Dimension(width, height));
    }

    protected Dimension addCaptionDimensions(Dimension dimension) {
        if (needCaption()) {
            dimension.width += 10;
            dimension.height += 20;
        }
        return dimension;
    }

    private boolean needCaption() { // не top, не tabbed и есть caption
        return (container.container != null && !container.container.isTabbed()) && container.caption != null;
    }

    protected FlexPanel wrapWithFlexCaption(FlexPanel view) {
        return needCaption() ? new FlexCaptionPanel(container.caption, view) : view;
    }

    protected Widget wrapWithTableCaption(Widget content) {
        return needCaption() ? new TableCaptionPanel(container.caption, content) : content;
    }

    public void onResize() {
        Widget view = getView();
        if (view instanceof RequiresResize) {
            ((RequiresResize) view).onResize();
        }
    }

    private interface UpdateLayoutListener {
        void updateLayout();
    }

    private List<UpdateLayoutListener> updateLayoutListeners = new ArrayList<>();
    public void updateLayout() {
        for(UpdateLayoutListener updateLayoutListener : updateLayoutListeners)
            updateLayoutListener.updateLayout();
    }

    private static Integer getSize(boolean vertical, boolean mainAxis, GComponent component) {
        int size;
        if (mainAxis) {
            size = vertical ? component.height : component.width;    
        } else {
            size = vertical ? component.width : component.height;
        }
        if (size != -1)
            return size;
        return null;
    }
    public static void add(FlexPanel panel, Widget widget, int beforeIndex, GFlexAlignment alignment, double flex, GComponent component, boolean vertical) { // последний параметр временный хак для Scrollable
//        assert alignment == component.alignment;
//        assert flex == component.flex;
        panel.add(widget, beforeIndex, alignment, flex, getSize(vertical, true, component), getSize(vertical, false, component));
    }

    protected abstract void addImpl(int index, GComponent child, Widget view);
    protected abstract void removeImpl(int index, GComponent child, Widget view);
    public abstract Widget getView();
}
