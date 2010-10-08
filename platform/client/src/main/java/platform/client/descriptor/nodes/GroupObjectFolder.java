package platform.client.descriptor.nodes;

import platform.client.descriptor.GroupObjectDescriptor;
import platform.client.descriptor.PropertyDrawDescriptor;
import platform.client.descriptor.FormDescriptor;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

public class GroupObjectFolder extends DefaultMutableTreeNode {

    public GroupObjectFolder(FormDescriptor form) {
        super("Группы объектов");

        for (GroupObjectDescriptor descriptor : form.groups) {
            add(new GroupObjectNode(descriptor, form));
        }

    }
}
