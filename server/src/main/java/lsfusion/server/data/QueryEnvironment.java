package lsfusion.server.data;

import lsfusion.server.data.type.ParseInterface;

import java.util.Locale;

public interface QueryEnvironment {

    ParseInterface getSQLUser();
    
    OperationOwner getOpOwner();
    
    int getTransactTimeout();

    ParseInterface getIsFullClient();
    ParseInterface getSQLComputer();
    ParseInterface getSQLForm();
    ParseInterface getSQLConnection();
    ParseInterface getIsServerRestarting();

    Locale getLocale();
}
