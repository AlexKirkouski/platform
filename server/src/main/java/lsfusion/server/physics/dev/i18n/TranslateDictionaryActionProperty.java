package lsfusion.server.physics.dev.i18n;

import lsfusion.server.logics.classes.StringClass;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.language.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.StringTokenizer;

public class TranslateDictionaryActionProperty extends ScriptingActionProperty {
    public final ClassPropertyInterface dictionaryInterface;
    public final ClassPropertyInterface termInterface;

    public TranslateDictionaryActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dictionaryInterface = i.next();
        termInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataObject dictionaryObject = context.getDataKeyValue(dictionaryInterface);
            DataObject termObject = context.getDataKeyValue(termInterface);

            if (dictionaryObject != null && termObject != null) {

                Boolean insensitive = findProperty("insensitive[Dictionary]").read(context.getSession(), dictionaryObject) != null;
                LCP insensitiveLP = findProperty("insensitiveTranslationDictionaryEntry[Dictionary,VARSTRING[50]]");
                LCP sensitiveLP = findProperty("translationDictionaryEntry[Dictionary,VARSTRING[50]]");

                String source = (String) termObject.object;
                if (insensitive)
                    source = source.toUpperCase();
                if (source != null) {
                    String delim = ", .:;%#$@/\\|<>=+-_)(*&?^!~{}[]\"1234567890'";
                    String result = "";
                    String fullLineTranslation = (String) (insensitive ? insensitiveLP : sensitiveLP).read(context, dictionaryObject, new DataObject(source, StringClass.text));
                    if (fullLineTranslation != null) {
                        result = fullLineTranslation.trim();
                    } else {
                        StringTokenizer st = new StringTokenizer(source, delim, true);
                        String[] phrase = new String[st.countTokens()];
                        int count = 0;
                        while (st.hasMoreTokens()) {
                            String token = st.nextToken();
                            phrase[count] = token;
                            count++;
                        }

                        int start = 0;
                        int finish = phrase.length;
                        while (start < finish) {
                            int tStart = start;
                            int tFinish = finish;

                            while (tStart < tFinish) {

                                String token = getToken(phrase, tStart, tFinish);

                                String translation = (String) (insensitive ? insensitiveLP : sensitiveLP).read(context, dictionaryObject, new DataObject(token, StringClass.text));
                                if (translation != null) {
                                    result += translation.trim();
                                    start = tFinish;
                                    tStart = tFinish;
                                } else {
                                    tFinish--;
                                    if(tStart == tFinish) { //не нашли перевод
                                        result += token;
                                        start++;
                                    }
                                }
                            }
                        }
                    }
                    findProperty("translationResult[]").change(result, context);
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException ignored) {
        }
    }

    private String getToken(String[] source, int start, int finish) {
        String token = "";
        while (start < finish) {
            token += source[start];
            start++;
        }
        return token;
    }
}
