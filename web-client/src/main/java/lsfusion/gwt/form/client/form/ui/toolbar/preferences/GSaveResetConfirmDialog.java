package lsfusion.gwt.form.client.form.ui.toolbar.preferences;

import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import lsfusion.gwt.base.client.Callback;
import lsfusion.gwt.base.client.GwtClientUtils;
import lsfusion.gwt.base.client.ui.DialogBoxHelper;
import lsfusion.gwt.base.client.ui.GKeyStroke;
import lsfusion.gwt.form.client.MainFrame;
import lsfusion.gwt.form.client.MainFrameMessages;

@SuppressWarnings("GWTStyleCheck")
public class GSaveResetConfirmDialog {
    private static final MainFrameMessages messages = MainFrameMessages.Instance.get();
    
    public boolean forAll = false;
    public boolean complete = false;
    private final boolean save;

    private DialogBoxHelper.MessageBox mb = null;

    public GSaveResetConfirmDialog(boolean save) {
        this.save = save;
    }
    
    public void show(final Callback callback) {
        DialogBoxHelper.OptionType[] options;
        Widget contents;
        RadioButton currentUserRB = null;
        if (!MainFrame.configurationAccessAllowed) {
            options = new DialogBoxHelper.OptionType[] {DialogBoxHelper.OptionType.YES, DialogBoxHelper.OptionType.NO};

            contents = new HTML(save ? messages.formGridPreferencesSureToSave() : messages.formGridPreferencesSureToReset());
        } else {
            options = new DialogBoxHelper.OptionType[] {DialogBoxHelper.OptionType.OK, DialogBoxHelper.OptionType.CANCEL};

            currentUserRB = new RadioButton("group", messages.formGridPreferencesForCurrentUser());
            currentUserRB.addStyleName("userPreferencesRadioButton");
            currentUserRB.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event) {
                    Boolean value = event.getValue();
                    forAll = value == null || !value;
                    complete = value == null || !value;
                }
            });
            currentUserRB.addKeyPressHandler(new KeyPressHandler() {
                @Override
                public void onKeyPress(KeyPressEvent event) {
                    radioKeyPressed(event, callback);
                }
            });
            currentUserRB.setValue(true);
            
            RadioButton allUsersRB = new RadioButton("group", messages.formGridPreferencesForAllUsers());
            allUsersRB.addStyleName("userPreferencesRadioButton");
            allUsersRB.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event) {
                    Boolean value = event.getValue();
                    forAll = value != null && value;
                    complete = value == null || !value;
                }
            });
            allUsersRB.addKeyPressHandler(new KeyPressHandler() {
                @Override
                public void onKeyPress(KeyPressEvent event) {
                    radioKeyPressed(event, callback);
                }
            });

            VerticalPanel panel = new VerticalPanel();
            panel.add(new HTML((save ? messages.formGridPreferencesSave() : messages.formGridPreferencesReset()) + ":"));
            panel.add(GwtClientUtils.createVerticalStrut(6));
            panel.add(currentUserRB);
            panel.add(allUsersRB);
            if (!save) {
                RadioButton allUsersCompleteRB = new RadioButton("group", messages.formGridPreferencesForAllUsersComplete());
                allUsersCompleteRB.addStyleName("userPreferencesRadioButton");
                allUsersCompleteRB.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
                    @Override
                    public void onValueChange(ValueChangeEvent<Boolean> event) {
                        Boolean value = event.getValue();
                        forAll = value != null && value;
                        complete = value != null && value;
                    }
                });
                allUsersCompleteRB.addKeyPressHandler(new KeyPressHandler() {
                    @Override
                    public void onKeyPress(KeyPressEvent event) {
                        radioKeyPressed(event, callback);
                    }
                });
                panel.add(allUsersCompleteRB);
            }
            contents = panel;
        }

        
        mb = DialogBoxHelper.showConfirmBox(save ? messages.formGridPreferencesSaving() : messages.formGridPreferencesResetting(), contents, options, new DialogBoxHelper.CloseCallback() {
            @Override
            public void closed(DialogBoxHelper.OptionType chosenOption) {
                if (chosenOption.asInteger() == 0) {
                    callback.onSuccess();
                } else {
                    callback.onFailure();
                }
            }
        });
        if (currentUserRB != null) {
            currentUserRB.setFocus(true);
        }
    }
    
    private void radioKeyPressed(KeyPressEvent event, Callback callback) {
        if (GKeyStroke.isEnterKeyEvent(event.getNativeEvent())) {
            if (mb != null) {
                mb.hide();
            }
            callback.onSuccess();
        }
    }
}
