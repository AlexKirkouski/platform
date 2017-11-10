package lsfusion.client.form.layout;

import lsfusion.client.logics.ClientComponent;
import lsfusion.client.logics.ClientContainer;
import lsfusion.interop.form.layout.FlexAlignment;

import javax.swing.*;
import java.awt.*;

public class ScrollClientContainerView extends AbstractClientContainerView {

    private final ContainerViewPanel panel;
    private final JComponentPanel scrollPanel;
    private final JScrollPane scroll;

    public ScrollClientContainerView(ClientFormLayout formLayout, ClientContainer container) {
        super(formLayout, container);
        assert container.isScroll();
        
        scroll = new JScrollPane();
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getHorizontalScrollBar().setUnitIncrement(14);
        
        container.design.designComponent(scroll);

        scrollPanel = new JComponentPanel(); // componentSize добавляемого компонента
        scrollPanel.add(scroll, BorderLayout.CENTER);

        panel = new ContainerViewPanel(); // componentSize этого контейнера
        panel.add(scrollPanel, BorderLayout.CENTER);
    }

    @Override
    public void addImpl(int index, ClientComponent child, JComponentPanel view) {
        assert child.flex == 1 && child.alignment == FlexAlignment.STRETCH; // временные assert'ы чтобы проверить обратную совместимость
        scroll.setViewportView(view);
        setSizes(scrollPanel, child);
    }

    @Override
    public void removeImpl(int index, ClientComponent child, JComponentPanel view) {
        scroll.getViewport().setView(null);
    }

    @Override
    public JComponentPanel getView() {
        return panel;
    }
}
