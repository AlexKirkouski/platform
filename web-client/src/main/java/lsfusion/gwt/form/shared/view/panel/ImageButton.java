package lsfusion.gwt.form.shared.view.panel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.*;
import lsfusion.gwt.base.client.GwtClientUtils;
import lsfusion.gwt.base.client.ui.ResizableHorizontalPanel;
import lsfusion.gwt.base.client.ui.ResizableVerticalPanel;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.shared.view.ImageDescription;

public class ImageButton extends Button {
    private final Image image;
    private final Label label;
    private final Widget strut;
    private final CellPanel panel;

    private String imagePath;
    private String text = "";

    private boolean focusable = true;
    private boolean vertical;

    public ImageButton() {
        this(null, null, false);
    }

    public ImageButton(String caption) {
        this(caption, null, false);
    }

    public ImageButton(String caption, String imagePath) {
        this(caption, imagePath, false);
    }

    public ImageButton(String caption, ImageDescription imageDescription) {
        this(caption, null, false);
        setImage(imageDescription);
    }

    public ImageButton(String caption, boolean directionBottom) {
        this(caption, null, directionBottom);
    }

    public ImageButton(String caption, boolean vertical, boolean alignCenter) {
        this(caption, null, vertical, alignCenter);
    }

    public ImageButton(String caption, String imagePath, boolean vertical) {
        this(caption, imagePath, vertical, true);
    }

    public ImageButton(String caption, String imagePath, boolean vertical, boolean alignCenter) {
        this.vertical = vertical;

        panel = vertical ? new ResizableVerticalPanel() : new ResizableHorizontalPanel();

        image = new Image();
        image.setVisible(false);
        image.addStyleName("displayBlock");

        strut = vertical ? null : GwtClientUtils.createHorizontalStrut(2);

        label = new Label();

//        panel.add(image);
        if (!vertical) {
            panel.add(strut);
        }
        panel.add(label);

        if (vertical) {
            panel.setCellHorizontalAlignment(label, HasHorizontalAlignment.ALIGN_CENTER);
        } else {
            panel.setCellVerticalAlignment(label, HasAlignment.ALIGN_MIDDLE);
        }
        if (alignCenter) {
            panel.getElement().getStyle().setProperty("margin", "auto");
        }

        setText(caption);
        setModuleImagePath(imagePath);

        getElement().appendChild(panel.getElement());
    }

    public void setImage(ImageDescription imageDescription) {
        if (imageDescription != null) {
            setAppImagePath(imageDescription.url);
            if (imageDescription.width != -1) {
                image.setWidth(imageDescription.width + "px");
            }
            if (imageDescription.height != -1) {
                image.setHeight(imageDescription.height + "px");
            }
        }
    }

    public void setModuleImagePath(String imagePath) {
        setAbsoluteImagePath(imagePath == null ? null : GWT.getModuleBaseURL() + "images/" + imagePath);
    }

    public void setAppImagePath(String imagePath) {
        setAbsoluteImagePath(imagePath == null ? null : GwtClientUtils.getWebAppBaseURL() + imagePath);
    }

    private void setAbsoluteImagePath(String imagePath) {
        if (!GwtSharedUtils.nullEquals(this.imagePath, imagePath)) {
            image.setUrl(imagePath == null ? "" : imagePath);

            if ((this.imagePath == null && imagePath != null) || (this.imagePath != null && imagePath == null)) {
                image.setVisible(imagePath != null);
                updateStrut();

                if (imagePath == null) {
                    image.removeFromParent();
                } else {
                    ((InsertPanel)panel).insert(image, 0);
                    if (vertical) {
                        panel.setCellHorizontalAlignment(image, HasHorizontalAlignment.ALIGN_CENTER);
                    } else {
                        panel.setCellVerticalAlignment(image, HasAlignment.ALIGN_MIDDLE);
                    }
                }
            }

            this.imagePath = imagePath;
        }
    }

    public void setText(String text) {
        if (!GwtSharedUtils.nullEquals(this.text, text)) {

            label.setText(text);
            if ((this.text == null && text != null) || (this.text != null && text == null)) {
                label.setVisible(text != null && !text.isEmpty());
                updateStrut();
            }
            this.text = text;
        }
    }

    private void updateStrut() {
        if (strut != null) {
            strut.setVisible(image.isVisible() && label.isVisible());
        }
    }

    public Label getLabel() {
        return label;
    }

    // call before attach
    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        if (!focusable) {
            setTabIndex(-1);
        }
    }
}
