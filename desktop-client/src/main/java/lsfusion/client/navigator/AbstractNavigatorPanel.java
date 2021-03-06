package lsfusion.client.navigator;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public abstract class AbstractNavigatorPanel extends JPanel {
    protected final ClientNavigator clientNavigator;

    protected final NavigatorTree tree;

    public AbstractNavigatorPanel(ClientNavigator clientNavigator) {
        this.clientNavigator = clientNavigator;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        setPreferredSize(new Dimension(175, 400));

        tree = new NavigatorTree(this, clientNavigator.rootElement);

        JScrollPane pane = new JScrollPane(tree);
        add(pane);
    }


    public void openAction(ClientNavigatorAction action) {
        clientNavigator.openAction(action, 0);
    }

    protected List<ClientNavigatorElement> getNodeElements(ClientNavigatorElement element) throws IOException {
        return element.children;
    }

    public void nodeChanged(NavigatorTreeNode node) {
        //do nothing by default
    }
}
