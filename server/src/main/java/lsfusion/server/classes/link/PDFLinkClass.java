package lsfusion.server.classes.link;

import lsfusion.interop.Data;
import lsfusion.server.classes.DataClass;

import java.util.ArrayList;
import java.util.Collection;

public class PDFLinkClass extends StaticFormatLinkClass {

    protected String getFileSID() {
        return "PDFLINK";
    }

    private static Collection<PDFLinkClass> instances = new ArrayList<>();

    public static PDFLinkClass get(boolean multiple) {
        for (PDFLinkClass instance : instances)
            if (instance.multiple == multiple)
                return instance;

        PDFLinkClass instance = new PDFLinkClass(multiple);
        instances.add(instance);
        DataClass.storeClass(instance);
        return instance;
    }

    private PDFLinkClass(boolean multiple) {
        super(multiple);
    }

    public byte getTypeID() {
        return Data.PDFLINK;
    }

    @Override
    public String getDefaultCastExtension() {
        return "pdf";
    }
}