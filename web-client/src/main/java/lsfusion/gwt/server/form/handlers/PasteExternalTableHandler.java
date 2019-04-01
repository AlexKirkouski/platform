package lsfusion.gwt.server.form.handlers;

import com.google.common.base.Throwables;
import lsfusion.gwt.client.controller.remote.action.form.PasteExternalTable;
import lsfusion.gwt.client.controller.remote.action.form.ServerResponseResult;
import lsfusion.gwt.server.MainDispatchServlet;
import lsfusion.gwt.server.convert.GwtToClientConverter;
import lsfusion.gwt.server.form.FormServerResponseActionHandler;
import lsfusion.http.provider.form.FormSessionObject;
import net.customware.gwt.dispatch.server.ExecutionContext;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import static lsfusion.base.BaseUtils.serializeObject;

public class PasteExternalTableHandler extends FormServerResponseActionHandler<PasteExternalTable> {
    private static GwtToClientConverter gwtConverter = GwtToClientConverter.getInstance();

    public PasteExternalTableHandler(MainDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public ServerResponseResult executeEx(PasteExternalTable action, ExecutionContext context) throws RemoteException {
        List<List<byte[]>> values = new ArrayList<>();
        for (List<Object> gRowValues : action.values) {
            List<byte[]> rowValues = new ArrayList<>();

            for (Object gRowValue : gRowValues) {
                Object oCell = gwtConverter.convertOrCast(gRowValue);
                try {
                    rowValues.add(serializeObject(oCell));
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }

            values.add(rowValues);
        }

        List<byte[]> columnKeys = new ArrayList<>();
        for (int i = 0; i < action.columnKeys.size(); i++) {
            columnKeys.add((byte[]) gwtConverter.convertOrCast(action.columnKeys.get(i)));
        }

        FormSessionObject form = getFormSessionObject(action.formSessionID);
        return getServerResponseResult(form, form.remoteForm.pasteExternalTable(action.requestIndex, action.lastReceivedRequestIndex, action.propertyIdList, columnKeys, values));
    }
}
