package lsfusion.server.session;

public interface UpdateResult {
    
    boolean dataChanged();
    boolean sourceChanged();    
    UpdateResult or(UpdateResult result);
    
    UpdateResult SOURCE = new UpdateResult() {
        public boolean dataChanged() {
            return false;
        }

        public boolean sourceChanged() {
            return true;
        }

        public UpdateResult or(UpdateResult result) {
            if(result instanceof ModifyResult)
                return result.or(this);
            assert this == SOURCE && result == SOURCE;
            return this;
        }
    };
}
