package lsfusion.gwt.form.shared.view.panel;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.base.client.Dimension;
import lsfusion.gwt.base.client.ui.FlexPanel;
import lsfusion.gwt.base.client.ui.GFlexAlignment;
import lsfusion.gwt.base.client.ui.ResizableComplexPanel;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.client.form.ui.GFormController;
import lsfusion.gwt.form.client.form.ui.GSinglePropertyTable;
import lsfusion.gwt.form.client.form.ui.TooltipManager;
import lsfusion.gwt.form.shared.view.GEditBindingMap;
import lsfusion.gwt.form.shared.view.GKeyStroke;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValue;
import lsfusion.gwt.form.shared.view.changes.dto.ColorDTO;

import static lsfusion.gwt.base.client.GwtClientUtils.getOffsetSize;
import static lsfusion.gwt.base.client.GwtClientUtils.isShowing;
import static lsfusion.gwt.form.client.HotkeyManager.Binding;

public class DataPanelRenderer implements PanelRenderer {
    public final GPropertyDraw property;

    public final FlexPanel panel;
    public final ResizableComplexPanel gridPanel;
    private final SimplePanel focusPanel;

    public final Label label;
    public final GSinglePropertyTable valueTable;

    private String caption;
    private String tooltip;

    private EventTarget focusTargetAfterEdit;

    public DataPanelRenderer(final GFormController form, GPropertyDraw iproperty, GGroupObjectValue columnKey) {
        this.property = iproperty;

        boolean vertical = property.panelLabelAbove;

        tooltip = property.getTooltipText(property.getCaptionOrEmpty());

        label = new Label(caption = property.getEditCaption());
        if (!vertical) {
            label.getElement().getStyle().setMarginRight(4, Style.Unit.PX);
        }

        label.addMouseOverHandler(new MouseOverHandler() {
            @Override
            public void onMouseOver(MouseOverEvent event) {
                TooltipManager.get().showTooltip(event.getClientX(), event.getClientY(), tooltip);
            }
        });
        label.addMouseOutHandler(new MouseOutHandler() {
            @Override
            public void onMouseOut(MouseOutEvent event) {
                TooltipManager.get().hideTooltip();
            }
        });
        label.addMouseMoveHandler(new MouseMoveHandler() {
            @Override
            public void onMouseMove(MouseMoveEvent event) {
                TooltipManager.get().updateMousePosition(event.getClientX(), event.getClientY());
            }
        });

        if (property.headerFont != null) {
            property.headerFont.apply(label.getElement().getStyle());
        }

        valueTable = new ValueTable(form, columnKey);

        gridPanel = new ResizableComplexPanel();
        gridPanel.addStyleName("dataPanelRendererGridPanel");

        if (property.focusable) {
            focusPanel = new SimplePanel();
            focusPanel.addStyleName("dataPanelRendererFocusPanel");
            focusPanel.setVisible(false);
            gridPanel.add(focusPanel);
        } else {
            valueTable.setTableFocusable(false);
            focusPanel = null;
        }

        gridPanel.add(valueTable);

        valueTable.setSize("100%", "100%");

        panel = new Panel(vertical);
        panel.addStyleName("dataPanelRendererPanel");

        // Рамка фокуса сделана как абсолютно позиционированный div с border-width: 2px,
        // который выходит за пределы панели, поэтому убираем overflow: hidden
        panel.getElement().getStyle().clearOverflow();
        gridPanel.getElement().getStyle().clearOverflow();

        panel.add(label, GFlexAlignment.CENTER);
        panel.add(gridPanel, vertical ? GFlexAlignment.STRETCH : GFlexAlignment.CENTER, 1, "auto");

        String preferredHeight = property.getPreferredHeight();
        String preferredWidth = property.getPreferredWidth();

        gridPanel.setHeight(preferredHeight);
        if (!vertical) {
            gridPanel.setWidth(preferredWidth);
        }

        gridPanel.getElement().getStyle().setProperty("minHeight", preferredHeight);
        gridPanel.getElement().getStyle().setProperty("minWidth", preferredWidth);
        gridPanel.getElement().getStyle().setProperty("maxHeight", preferredHeight);
        gridPanel.getElement().getStyle().setProperty("maxWidth", preferredWidth);

        finishLayoutSetup();

        valueTable.getElement().setPropertyObject("groupObject", property.groupObject);
        if (property.editKey != null) {
            form.addHotkeyBinding(property.groupObject, property.editKey, new Binding() {
                @Override
                public boolean onKeyPress(NativeEvent event, GKeyStroke key) {
                    if (!form.isEditing() && isShowing(panel)) {
                        focusTargetAfterEdit = event.getEventTarget();
                        valueTable.editCellAt(0, 0, GEditBindingMap.CHANGE);
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void finishLayoutSetup() {
        if (property.isVerticallyStretched()) {
            panel.setChildAlignment(gridPanel, GFlexAlignment.STRETCH);

            gridPanel.getElement().getStyle().clearHeight();
            gridPanel.getElement().getStyle().clearProperty("minHeight");
            gridPanel.getElement().getStyle().clearProperty("maxHeight");
            gridPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE);

            valueTable.setupFillParent();
        }

        if (property.isHorizontallyStretched()) {
            gridPanel.getElement().getStyle().clearWidth();
            gridPanel.getElement().getStyle().clearProperty("minWidth");
            gridPanel.getElement().getStyle().clearProperty("maxWidth");
        }
    }

    @Override
    public Widget getComponent() {
        return panel;
    }

    @Override
    public void setValue(Object value) {
        valueTable.setValue(value);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        valueTable.setReadOnly(readOnly);
    }

    @Override
    public void setCaption(String caption) {
        if (!GwtSharedUtils.nullEquals(this.caption, caption)) {
            this.caption = caption;
            label.setText(property.getEditCaption(caption));
            tooltip = property.getTooltipText(caption);
        }
    }

    @Override
    public void setDefaultIcon() {
    }

    @Override
    public void setIcon(String iconPath) {
    }

    @Override
    public void updateCellBackgroundValue(Object value) {
        valueTable.setBackground((ColorDTO) value);
    }

    @Override
    public void updateCellForegroundValue(Object value) {
        valueTable.setForeground((ColorDTO) value);
    }

    @Override
    public void focus() {
        valueTable.setFocus(true);
    }

    private class Panel extends FlexPanel {
        public Panel(boolean vertical) {
            super(vertical);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension pref = getOffsetSize(label);

            //+extra for borders and margins
            int width = property.getPreferredPixelWidth() + 5;
            int height = property.getPreferredPixelHeight() + 5;

            if (isVertical()) {
                pref.width = Math.max(pref.width, width);
                pref.height += height;
            } else {
                pref.width += width;
                pref.height = Math.max(pref.height, height);
            }

            return pref;
        }
    }

    private class ValueTable extends GSinglePropertyTable {
        public ValueTable(GFormController form, GGroupObjectValue columnKey) {
            super(form, DataPanelRenderer.this.property, columnKey);
        }

        @Override
        protected void onFocus() {
            super.onFocus();
            if (property.focusable) {
                focusPanel.setVisible(true);
            }
        }

        @Override
        protected void onBlur() {
            super.onBlur();
            if (property.focusable) {
                focusPanel.setVisible(false);
            }
        }

        @Override
        public void takeFocusAfterEdit() {
            if (focusTargetAfterEdit != null) {
                Element.as(focusTargetAfterEdit).focus();
                focusTargetAfterEdit = null;
            } else {
                setFocus(true);
            }
        }
    }
}
