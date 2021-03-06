package lsfusion.server.logics.property.actions;

import lsfusion.interop.FormExportType;
import lsfusion.interop.action.ReportPath;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormSelector;
import lsfusion.server.form.entity.ObjectSelector;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.actions.exporting.HierarchicalFormExporter;
import lsfusion.server.logics.property.actions.exporting.PlainFormExporter;
import lsfusion.server.logics.property.actions.exporting.csv.CSVFormExporter;
import lsfusion.server.logics.property.actions.exporting.dbf.DBFFormExporter;
import lsfusion.server.logics.property.actions.exporting.json.JSONFormExporter;
import lsfusion.server.logics.property.actions.exporting.xml.XMLFormExporter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExportActionProperty<O extends ObjectSelector> extends FormStaticActionProperty<O, FormExportType> {

    // csv
    private final boolean noHeader;
    private final String separator;
    private final String charset;

    //xml
    Set<String> attrs;
    Map<String, String> headers;

    public ExportActionProperty(LocalizedString caption,
                                FormSelector<O> form,
                                List<O> objectsToSet,
                                List<Boolean> nulls,
                                FormExportType staticType,
                                LCP exportFile,
                                boolean noHeader,
                                String separator,
                                String charset,
                                Set<String> attrs,
                                Map<String, String> headers) {
        super(caption, form, objectsToSet, nulls, staticType, exportFile);
        
        this.noHeader = noHeader;
        this.separator = separator;
        this.charset = charset;
        this.attrs = attrs;
        this.headers = headers;
    }


    @Override
    protected Map<String, byte[]> exportPlain(ReportGenerationData reportData) throws IOException {
        PlainFormExporter exporter;
        if(staticType == FormExportType.CSV) {
            exporter = new CSVFormExporter(reportData, noHeader, separator, charset);
        } else {
            assert staticType == FormExportType.DBF;
            exporter = new DBFFormExporter(reportData, charset);
        }
        return exporter.export();
    }

    @Override
    protected byte[] exportHierarchical(ExecutionContext<ClassPropertyInterface> context, ReportGenerationData reportData) throws IOException {
        HierarchicalFormExporter exporter;
        if (staticType == FormExportType.XML) {
            exporter = new XMLFormExporter(reportData, attrs, headers);
        } else {
            assert staticType == FormExportType.JSON;
            exporter = new JSONFormExporter(reportData);
        }
        return exporter.export();
    }

    @Override
    protected void exportClient(ExecutionContext<ClassPropertyInterface> context, LocalizedString caption, ReportGenerationData reportData, List<ReportPath> reportPathList, String formSID) throws SQLException, SQLHandledException {
        throw new UnsupportedOperationException();
    }
}
