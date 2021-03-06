package lsfusion.client.navigator;

import lsfusion.client.Main;
import lsfusion.client.tree.ClientTree;
import lsfusion.client.tree.ClientTreeNode;

import javax.swing.*;

public class NavigatorTreeNode extends ClientTreeNode<ClientNavigatorElement, NavigatorTreeNode> {
    private final NavigatorTree tree;
    public final ClientNavigatorElement navigatorElement;
    public boolean nodeStructureChanged;

    public NavigatorTreeNode(NavigatorTree tree, ClientNavigatorElement navigatorElement) {
        super(navigatorElement);
        this.tree = tree;

        this.navigatorElement = navigatorElement;
    }

    public boolean canImport(TransferHandler.TransferSupport info) {
        ClientTreeNode node = ClientTree.getNode(info);
        return node instanceof NavigatorTreeNode && !node.isNodeDescendant(this) && this.allowsChildren;
    }

    public boolean importData(ClientTree iTree, TransferHandler.TransferSupport info) {
        tree.expandPath(tree.getPathToRoot(this));

        NavigatorTreeNode draggingNode = ((NavigatorTreeNode) ClientTree.getNode(info));
        NavigatorTreeNode parentNode = draggingNode.getParent();

        int index = ClientTree.getChildIndex(info);
        if (index == -1) {
            index = getChildCount();
        }

        if (parentNode == this) {
            int origIndex = parentNode.getIndex(draggingNode);
            parentNode.removeNode(draggingNode);
            parentNode.insertNode(draggingNode, index > origIndex ? index - 1 : index);
        } else {
            parentNode.remove(draggingNode);
            insertNode(draggingNode, index);

            tree.getModel().reload(parentNode);
            parentNode.structureChanged();
        }

        tree.getModel().reload(this);

        structureChanged();

        return true;
    }

    private void structureChanged() {
        nodeStructureChanged = true;
        tree.navigator.nodeChanged(this);
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public NavigatorTreeNode getParent() {
        return (NavigatorTreeNode) super.getParent();
    }

    public void addNode(ClientTreeNode newChild) {
        super.add(newChild);
        if (newChild instanceof NavigatorTreeNode) {
            navigatorElement.hasChildren = true;
            structureChanged();
        }
    }

    public void removeNode(ClientTreeNode child) {
        super.remove(child);
        if (child instanceof NavigatorTreeNode) {
            structureChanged();
        }
    }

    public void insertNode(ClientTreeNode newChild, int index) {
        super.insert(newChild, index);
        if (newChild instanceof NavigatorTreeNode) {
            navigatorElement.hasChildren = true;
            structureChanged();
        }
    }
}
