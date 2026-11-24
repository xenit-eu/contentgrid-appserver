package com.contentgrid.appserver.query.engine.api;

import com.contentgrid.appserver.query.engine.api.data.EntityData;
import lombok.NonNull;

/**
 * Check that has to pass on the current state of an entity before it is updated.
 * <p>
 * The query engine calls {@link #check(EntityData)} with the current entity data while the entity row is locked for
 * the rest of the update transaction, so no concurrent write can change the entity between the check and the update.
 * <p>
 * Implementations run while that row lock is held: they must be cheap and must not perform I/O (no content store
 * access, no remote calls, no other queries). Every concurrent writer of the same entity waits for the check to finish.
 */
@FunctionalInterface
public interface UpdatePrecondition {

    /**
     * Precondition that always passes
     */
    UpdatePrecondition NONE = current -> {};

    /**
     * Checks the precondition against the current entity data.
     *
     * @param current the current data of the entity, read while the entity row is locked
     * @throws RuntimeException to reject the update; the exception is propagated to the caller of the update and
     * nothing is written
     */
    void check(@NonNull EntityData current);
}
