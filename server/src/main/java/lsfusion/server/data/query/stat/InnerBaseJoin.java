package lsfusion.server.data.query.stat;

public interface InnerBaseJoin<K> extends BaseJoin<K> {

    boolean hasExprFollowsWithoutNotNull(); // для оптимизации
}
