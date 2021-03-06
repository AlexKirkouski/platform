package lsfusion.server.form.view;

import lsfusion.interop.form.layout.FlexAlignment;
import lsfusion.server.serialization.ServerSerializationPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FilterView extends ComponentView {

    public boolean visible = true;

    public FilterView() {
    }

    public FilterView(int ID) {
        super(ID);

        setAlignment(FlexAlignment.STRETCH);
    }

    @Override
    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        super.customSerialize(pool, outStream, serializationType);

        outStream.writeBoolean(visible);
    }

    @Override
    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        visible = inStream.readBoolean();
    }
}
