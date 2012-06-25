package platform.gwt.main.shared.actions.form;

public class ThrowInInvocationAction extends FormBoundAction<ServerResponseResult> {
    public Exception exception;

    public ThrowInInvocationAction() {
    }

    public ThrowInInvocationAction(Exception exception) {
        this.exception = exception;
    }
}
