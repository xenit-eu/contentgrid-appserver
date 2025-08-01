package com.contentgrid.appserver.query.engine.api.data;

/**
 * Tells the query engine what portion of the results to return, akin to paging.
 *
 * This doesn't need to be simply offset=200. For instance, in a cursor-based system, it could be something that gets
 * translated to `where id >= '019808ab-...' order by id`.
 */
public sealed interface QueryPageData permits OffsetData {
}
