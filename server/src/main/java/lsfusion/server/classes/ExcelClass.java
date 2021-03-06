package lsfusion.server.classes;

import lsfusion.interop.Data;
import org.apache.poi.poifs.filesystem.DocumentFactoryHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class ExcelClass extends StaticFormatFileClass {

    protected String getFileSID() {
        return "EXCELFILE";
    }

    private static Collection<ExcelClass> instances = new ArrayList<>();

    public static ExcelClass get(boolean multiple, boolean storeName) {
        for (ExcelClass instance : instances)
            if (instance.multiple == multiple && instance.storeName == storeName)
                return instance;

        ExcelClass instance = new ExcelClass(multiple, storeName);
        instances.add(instance);
        DataClass.storeClass(instance);
        return instance;
    }

    private ExcelClass(boolean multiple, boolean storeName) {
        super(multiple, storeName);
    }

    public byte getTypeID() {
        return Data.EXCEL;
    }

    public String getOpenExtension(byte[] file) {
        try {
            return DocumentFactoryHelper.hasOOXMLHeader(new ByteArrayInputStream(file)) ? "xlsx" : "xls";
        } catch (IOException e) {
            return "xls";
        }
    }

    @Override
    public String getDefaultCastExtension() {
        return "xls";
    }
}
