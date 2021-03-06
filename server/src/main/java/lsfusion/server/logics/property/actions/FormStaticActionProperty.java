package lsfusion.server.logics.property.actions;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.FormExportType;
import lsfusion.interop.FormPrintType;
import lsfusion.interop.FormStaticType;
import lsfusion.interop.action.ReportPath;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.interop.form.ReportGenerationDataType;
import lsfusion.server.ServerLoggers;
import lsfusion.server.SystemProperties;
import lsfusion.server.classes.StaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.FormSelector;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.ObjectSelector;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.Property;
import lsfusion.server.remote.FormReportManager;
import lsfusion.server.remote.StaticFormReportManager;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class FormStaticActionProperty<O extends ObjectSelector, T extends FormStaticType> extends FormActionProperty<O> {

    protected final T staticType;
    
    private final LCP<?> exportFile;

    private Integer selectTop;

    public FormStaticActionProperty(LocalizedString caption,
                                    FormSelector<O> form,
                                    List<O> objectsToSet,
                                    List<Boolean> nulls,
                                    T staticType,
                                    LCP exportFile,
                                    Property... extraProps) {
        this(caption, form, objectsToSet, nulls, staticType, exportFile, null, extraProps);
    }

    public FormStaticActionProperty(LocalizedString caption,
                                    FormSelector<O> form,
                                    List<O> objectsToSet,
                                    List<Boolean> nulls,
                                    T staticType,
                                    LCP exportFile,
                                    Integer selectTop,
                                    Property... extraProps) {
        super(caption, form, objectsToSet, nulls, false, extraProps);

        this.staticType = staticType;
        this.exportFile = exportFile;
        this.selectTop = selectTop;
    }
    
    protected abstract Map<String, byte[]> exportPlain(ReportGenerationData reportData) throws IOException; // multiple files
    protected abstract byte[] exportHierarchical(ExecutionContext<ClassPropertyInterface> context, ReportGenerationData reportData) throws SQLException, SQLHandledException, JRException, IOException, ClassNotFoundException; // single file

    protected abstract void exportClient(ExecutionContext<ClassPropertyInterface> context, LocalizedString caption, ReportGenerationData reportData, List<ReportPath> reportPathList, String formSID) throws SQLException, SQLHandledException;


    @Override
    protected void executeCustom(FormEntity form, ImMap<ObjectEntity, ? extends ObjectValue> mapObjectValues, ExecutionContext<ClassPropertyInterface> context, ImRevMap<ObjectEntity, O> mapResolvedObjects) throws SQLException, SQLHandledException {

        FormReportManager newFormManager = new StaticFormReportManager(form, BaseUtils.<ImMap<ObjectEntity, ObjectValue>>immutableCast(mapObjectValues), context);

        boolean isExcel = staticType instanceof FormPrintType && ((FormPrintType) staticType).isExcel();
        int top = selectTop == null ? 0 : selectTop;
        ReportGenerationData reportData = newFormManager.getReportData(isExcel, ReportGenerationDataType.get(staticType), top);

        if (exportFile != null) {
            try {
                String extension = staticType.getExtension();
                if (staticType instanceof FormExportType && ((FormExportType) staticType).isPlain()) { // plain = multiple files
                    Map<String, byte[]> files = exportPlain(reportData);
                    for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                        if (exportFile.property.getType() instanceof StaticFormatFileClass) {
                            exportFile.change(entry.getValue(), context, new DataObject(entry.getKey()));
                        } else {
                            exportFile.change(BaseUtils.mergeFileAndExtension(entry.getValue(), extension.getBytes()), context, new DataObject(entry.getKey()));
                        }
                    }
                } else { // hierarchical - single file
                    byte[] singleFile = exportHierarchical(context, reportData);
                    if (exportFile.property.getType() instanceof StaticFormatFileClass) {
                        exportFile.change(singleFile, context);
                    } else {
                        exportFile.change(BaseUtils.mergeFileAndExtension(singleFile, extension.getBytes()), context);
                    }
                }
            } catch (JRException | IOException | ClassNotFoundException e) {
                ServerLoggers.systemLogger.error(e);
            }
        } else { // assert printType != null;
            List<ReportPath> customReportPathList = SystemProperties.inDevMode && staticType != FormPrintType.MESSAGE ? newFormManager.getCustomReportPathList(staticType instanceof FormPrintType && ((FormPrintType) staticType).isExcel(), null, null) : new ArrayList<ReportPath>();
            exportClient(context, form.getCaption(), reportData, customReportPathList, form.getSID());
        }
    }

    @Override
    protected ImMap<CalcProperty, Boolean> aspectChangeExtProps() {
        if(exportFile != null)
            return getChangeProps(exportFile.property);
        return MapFact.EMPTY();
    }
}
