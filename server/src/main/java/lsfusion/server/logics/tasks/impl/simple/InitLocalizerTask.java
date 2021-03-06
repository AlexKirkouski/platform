package lsfusion.server.logics.tasks.impl.simple;

import lsfusion.server.logics.tasks.SimpleBLTask;
import org.apache.log4j.Logger;

public class InitLocalizerTask extends SimpleBLTask {

    @Override
    public String getCaption() {
        return "Initializing localizer";
    }

    @Override
    public void run(Logger logger) {
        getBL().initLocalizer();
    }
}
