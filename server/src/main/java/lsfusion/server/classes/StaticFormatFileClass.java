package lsfusion.server.classes;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.Type;

public abstract class StaticFormatFileClass extends FileClass {

    public abstract String getOpenExtension(byte[] file);

    protected StaticFormatFileClass(boolean multiple, boolean storeName) {
        super(multiple, storeName);
    }

    public String getDefaultCastExtension() {
        return null;
    }
    
    @Override
    public String getCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv, Type typeFrom) {
        if (typeFrom instanceof DynamicFormatFileClass) {
            return "castfromcustomfile(" + value + ")";
        }
        return super.getCast(value, syntax, typeEnv, typeFrom);
    }

    @Override
    protected byte[] parseHTTPNotNull(byte[] b) {
        return BaseUtils.getFile(b);
    }

    @Override
    protected byte[] formatHTTPNotNull(byte[] b) {
        return BaseUtils.mergeFileAndExtension(b, getOpenExtension(b).getBytes());
    }
    
    protected ImSet<String> getExtensions() {
        return SetFact.singleton(getDefaultCastExtension());
    } 

    @Override
    public DataClass getCompatible(DataClass compClass, boolean or) {
        if(!(compClass instanceof StaticFormatFileClass))
            return null;

        StaticFormatFileClass staticFileClass = (StaticFormatFileClass)compClass;
        if(!(multiple == staticFileClass.multiple && storeName == staticFileClass.storeName))
            return null;
        
        if(equals(compClass))
            return this;

//        ImSet<String> mergedExtensions = getExtensions().merge(staticFileClass.getExtensions());
        return CustomStaticFormatFileClass.get(multiple, storeName);
    }
}
