package lsfusion.client.navigator;

import lsfusion.base.IOUtils;
import lsfusion.base.serialization.SerializationUtil;
import lsfusion.client.ClientNavigatorFolder;
import lsfusion.client.Main;
import lsfusion.interop.SerializableImageIconHolder;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ClientNavigatorElement {

    private String canonicalName;

    public String creationPath;
    public String caption;
    
    public List<ClientNavigatorElement> parents = new ArrayList<>();
    public List<ClientNavigatorElement> children = new ArrayList<>();
    public SerializableImageIconHolder image;
    public String imageFileName;

    protected boolean hasChildren = false;
    public ClientNavigatorWindow window;

    public ClientNavigatorElement(DataInputStream inStream) throws IOException {
        canonicalName = SerializationUtil.readString(inStream);
        creationPath = SerializationUtil.readString(inStream);
        
        caption = inStream.readUTF();
        hasChildren = inStream.readBoolean();
        window = ClientNavigatorWindow.deserialize(inStream);

        image = IOUtils.readImageIcon(inStream);
        imageFileName = inStream.readUTF();
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public boolean hasChildren() {
        return hasChildren;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @Override
    public int hashCode() {
        return canonicalName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClientNavigatorElement && ((ClientNavigatorElement) obj).canonicalName.equals(canonicalName);
    }

    public String toString() {
        return caption;
    }

    public ClientNavigatorElement findElementByCanonicalName(String canonicalName) {
        if (canonicalName == null) {
            return null;
        }
        if (canonicalName.equals(this.canonicalName)) {
            return this;
        }
        
        for (ClientNavigatorElement child : children) {
            ClientNavigatorElement found = child.findElementByCanonicalName(canonicalName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static ClientNavigatorElement deserialize(DataInputStream inStream, Map<String, ClientNavigatorWindow> windows) throws IOException {
        byte type = inStream.readByte();

        ClientNavigatorElement element;

        switch (type) {
            case 1: element = new ClientNavigatorFolder(inStream); break;
            case 2: element = new ClientNavigatorAction(inStream); break;
            default:
                throw new IOException("Incorrect navigator element type");
        }

        // todo [dale]: Это не помешало бы отрефакторить 
        // Так как окна десериализуются при десериализации каждого элемента навигатора, то необходимо замещать
        // окна с неуникальным каноническим именем, потому что такое окно уже было создано.
        if (element.window != null) {
            String windowCanonicalName = element.window.canonicalName;
            if (windows.containsKey(windowCanonicalName)) {
                element.window = windows.get(windowCanonicalName);
            } else {
                windows.put(windowCanonicalName, element.window);
            }
        }

        return element;
    }

    //содержатся ли родители текущей вершины в заданном множестве
    public boolean containsParent(Set<ClientNavigatorElement> set) {
        for (ClientNavigatorElement parent : parents) {
            if (set.contains(parent)) {
                return true;
            }
        }
        return false;
    }

    public String getTooltip() {
        return Main.configurationAccessAllowed && creationPath != null ?
                String.format("<html><body bgcolor=#FFFFE1>" +
                        "<b>%s</b><br/><hr>" +
                        "<b>sID:</b> %s<br/>" +
                        "<b>Путь:</b> %s<br/>" +
                        "</body></html>", caption, canonicalName, creationPath) : caption;
    }
}
