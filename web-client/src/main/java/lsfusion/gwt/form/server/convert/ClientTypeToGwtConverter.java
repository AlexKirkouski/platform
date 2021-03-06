package lsfusion.gwt.form.server.convert;

import lsfusion.client.logics.classes.*;
import lsfusion.client.logics.classes.link.*;
import lsfusion.gwt.form.server.FileUtils;
import lsfusion.gwt.form.shared.view.GExtInt;
import lsfusion.gwt.form.shared.view.classes.*;
import lsfusion.gwt.form.shared.view.classes.link.*;

import javax.activation.MimetypesFileTypeMap;
import java.io.IOException;
import java.util.ArrayList;

@SuppressWarnings("UnusedDeclaration")
public class ClientTypeToGwtConverter extends ObjectConverter {
    private static final class InstanceHolder {
        private static final ClientTypeToGwtConverter instance = new ClientTypeToGwtConverter();
    }

    public static ClientTypeToGwtConverter getInstance() {
        return InstanceHolder.instance;
    }

    private ClientTypeToGwtConverter() {
    }

    @Converter(from = ClientLongClass.class)
    public GLongType convertLongClass(ClientLongClass clientLongClass) {
        return GLongType.instance;
    }

    @Converter(from = ClientDoubleClass.class)
    public GDoubleType convertDoubleClass(ClientDoubleClass clientDoubleClass) {
        return GDoubleType.instance;
    }

    @Converter(from = ClientNumericClass.class)
    public GNumericType convertNumericClass(ClientNumericClass clientNumericClass) {
        return new GNumericType(clientNumericClass.length, clientNumericClass.precision);
    }

    @Converter(from = ClientLongClass.class)
    public GLongType convertLongClass(ClientActionClass clientActionClass) {
        return GLongType.instance;
    }

    @Converter(from = ClientIntegerClass.class)
    public GIntegerType convertIntegerClass(ClientIntegerClass clientIntegerClass) {
        return GIntegerType.instance;
    }

    @Converter(from = ClientActionClass.class)
    public GActionType convertActionClass(ClientActionClass clientActionClass) {
        return GActionType.instance;
    }

    @Converter(from = ClientLogicalClass.class)
    public GLogicalType convertLogicalClass(ClientLogicalClass clientLogicalClass) {
        return GLogicalType.instance;
    }

    @Converter(from = ClientTimeClass.class)
    public GTimeType convertTimeClass(ClientTimeClass clientTimeClass) {
        return GTimeType.instance;
    }

    @Converter(from = ClientDateTimeClass.class)
    public GDateTimeType convertDateTimeClass(ClientDateTimeClass clientDateTimeClass) {
        return GDateTimeType.instance;
    }

    private <T extends GFileType> T initializeFileClass(ClientFileClass clientFileClass, T fileClass) {
        fileClass.multiple = clientFileClass.multiple;
        fileClass.storeName = clientFileClass.storeName;
        if (clientFileClass.getExtensions() != null) {
            fileClass.extensions = new ArrayList<>();
            MimetypesFileTypeMap mimeMap;
            try {
                mimeMap = new MimetypesFileTypeMap(FileUtils.APP_FOLDER_URL + "/WEB-INF/mimetypes");
            } catch (IOException e) {
                mimeMap = (MimetypesFileTypeMap) MimetypesFileTypeMap.getDefaultFileTypeMap();
            }
            for (int i = 0; i < clientFileClass.getExtensions().length; i++) {
                String ext = clientFileClass.getExtensions()[i];
                if (ext != null && !ext.isEmpty() && !ext.equals("*.*") && !ext.equals("*")) {
                    fileClass.extensions.add(mimeMap.getContentType("idleName." + ext.toLowerCase()));
                } else {
                    fileClass.extensions.add(ext);
                }
            }
        }
        return fileClass;
    }

    @Converter(from = ClientPDFClass.class)
    public GPDFType convertPDFClass(ClientPDFClass pdfClass) {
        return initializeFileClass(pdfClass, new GPDFType());
    }

    @Converter(from = ClientImageClass.class)
    public GImageType convertImageClass(ClientImageClass imageClass) {
        return initializeFileClass(imageClass, new GImageType());
    }

    @Converter(from = ClientWordClass.class)
    public GWordType convertWordClass(ClientWordClass wordClass) {
        return initializeFileClass(wordClass, new GWordType());
    }

    @Converter(from = ClientExcelClass.class)
    public GExcelType convertExcelClass(ClientExcelClass excelClass) {
        return initializeFileClass(excelClass, new GExcelType());
    }

    @Converter(from = ClientCustomStaticFormatFileClass.class)
    public GCustomStaticFormatFileType convertCustomStaticFormatFileClass(ClientCustomStaticFormatFileClass customClass) {
        GCustomStaticFormatFileType customFormatFileType = initializeFileClass(customClass, new GCustomStaticFormatFileType());
        customFormatFileType.description = customClass.filterDescription;
        return customFormatFileType;
    }

    @Converter(from = ClientDynamicFormatFileClass.class)
    public GCustomDynamicFormatFileType convertCustomDynamicFormatClass(ClientDynamicFormatFileClass customClass) {
        return initializeFileClass(customClass, new GCustomDynamicFormatFileType());
    }

    @Converter(from = ClientPDFLinkClass.class)
    public GPDFLinkType convertPDFClass(ClientPDFLinkClass pdfClass) {
        return initializeLinkClass(pdfClass, new GPDFLinkType());
    }

    @Converter(from = ClientImageLinkClass.class)
    public GImageLinkType convertImageClass(ClientImageLinkClass imageClass) {
        return initializeLinkClass(imageClass, new GImageLinkType());
    }

    @Converter(from = ClientWordLinkClass.class)
    public GWordLinkType convertWordLinkClass(ClientWordLinkClass wordClass) {
        return initializeLinkClass(wordClass, new GWordLinkType());
    }

    @Converter(from = ClientExcelLinkClass.class)
    public GExcelLinkType convertExcelClass(ClientExcelLinkClass excelClass) {
        return initializeLinkClass(excelClass, new GExcelLinkType());
    }

    @Converter(from = ClientCustomStaticFormatLinkClass.class)
    public GCustomStaticFormatLinkType convertCustomStaticFormatLinkClass(ClientCustomStaticFormatLinkClass customClass) {
        GCustomStaticFormatLinkType customFormatLinkType = initializeLinkClass(customClass, new GCustomStaticFormatLinkType());
        customFormatLinkType.description = customClass.filterDescription;
        return customFormatLinkType;
    }

    @Converter(from = ClientDynamicFormatLinkClass.class)
    public GCustomDynamicFormatLinkType convertCustomDynamicFormatClass(ClientDynamicFormatLinkClass customClass) {
        return initializeLinkClass(customClass, new GCustomDynamicFormatLinkType());
    }

    private <T extends GLinkType> T initializeLinkClass(ClientLinkClass clientLinkClass, T linkClass) {
        linkClass.multiple = clientLinkClass.multiple;
        return linkClass;
    }

    @Converter(from = ClientStringClass.class)
    public GStringType convertStringClass(ClientStringClass clientStringClass) {
        return new GStringType(new GExtInt(clientStringClass.length.value), clientStringClass.caseInsensitive, clientStringClass.blankPadded, clientStringClass.rich);
    }

    @Converter(from = ClientDateClass.class)
    public GDateType convertDateClass(ClientDateClass clientDateClass) {
        return GDateType.instance;
    }

    @Converter(from = ClientColorClass.class)
    public GColorType convertColorClass(ClientColorClass clientColorClass) {
        return GColorType.instance;
    }

    @Converter(from = ClientObjectType.class)
    public GObjectType convertObjectType(ClientObjectType clientObjectType) {
        return GObjectType.instance;
    }

    @Converter(from = ClientObjectClass.class)
    public GObjectClass convertObjectClass(ClientObjectClass clientClass) {
        ArrayList<GObjectClass> children = new ArrayList<>();
        for (ClientObjectClass child : clientClass.getChildren()) {
            children.add(convertObjectClass(child));
        }

        return new GObjectClass(clientClass.getID(), clientClass.isConcreate(), clientClass.getCaption(), children);
    }
}
