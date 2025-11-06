package com.contentgrid.appserver.query.engine.jooq;

import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class DslContextUtils {

    public interface ExecuteCallback<T, X extends Throwable> {
        T execute() throws X;
    }

    /**
     * Runs a callback isolated in a SQL savepoint.
     * <p>
     * Normally, SQL queries that result in an error make the transaction rollback-only,
     * which makes it impossible to execute any further queries.
     * <p>
     * By wrapping code in a savepoint, you enter some kind of nested transaction.
     * If the callback succeeds, the savepoint is released.
     * If the callback throws an exception, the savepoint is rolled back, which undoes all SQL changes made inside (including the transaction being flagged as failed)
     *
     * @param dslContext The DSL Context to create the savepoint in
     * @param callback The callback to run covered by the savepoint
     * @return Return value of the callback.
     * @param <T> Type returned by the callback
     * @param <X> Exception thrown by the callback
     * @throws X T
     */
    public static <T, X extends Throwable> T executeInSavepoint(DSLContext dslContext, ExecuteCallback<T, X> callback)
            throws X {
        var savepointName = DSL.name("savepoint_"+ UUID.randomUUID());
        dslContext.savepoint(savepointName).execute();
        boolean hasThrown = true;
        try {
            var ret = callback.execute();
            hasThrown = false;
            return ret;
        } finally {
            if(hasThrown) {
                dslContext.rollback().toSavepoint(savepointName).execute();
            } else {
                dslContext.releaseSavepoint(savepointName).execute();
            }
        }
    }

}
