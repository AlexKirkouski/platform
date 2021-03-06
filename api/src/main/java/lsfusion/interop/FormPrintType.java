package lsfusion.interop;


public enum FormPrintType implements FormStaticType {
    PRINT, // PRINT PREVIEW тип (потом надо будет переименовать)  
    AUTO, XLSX, XLS, PDF, DOC, DOCX, MESSAGE;
    
    public boolean isExcel() {
        return this == XLS || this == XLSX;
    }

    public boolean isCustom() {
        return false;
    }

    public String getExtension() {
        switch (this) {
            case XLS:
                return "xls";
            case XLSX:
                return "xlsx";
            case DOC:
                return "doc";
            case DOCX:
                return "docx";
            default:
                return "pdf"; // по умолчанию экспортируем в PDF
        }
    }
}
