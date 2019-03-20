package lsfusion.client.form.filter.user;

import lsfusion.client.form.filter.user.controller.QueryController;
import lsfusion.client.form.filter.user.view.QueryView;
import lsfusion.interop.form.event.KeyStrokes;

import javax.swing.*;

public class FilterView extends QueryView {

    private static final ImageIcon applyIcon = new ImageIcon(FilterView.class.getResource("/images/filt.png"));

    private static final ImageIcon addConditionIcon = new ImageIcon(FilterView.class.getResource("/images/filtadd.png"));

    public FilterView(QueryController controller) {
        super(controller);
    }

    public Icon getApplyIcon() {
        return applyIcon;
    }

    public Icon getAddConditionIcon() {
        return addConditionIcon;
    }

    protected KeyStroke getKeyStroke(int modifier) {
        return KeyStrokes.getFilterKeyStroke(modifier);
    }
}
