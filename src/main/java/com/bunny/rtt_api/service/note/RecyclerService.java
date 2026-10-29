package com.bunny.rtt_api.service.note;

public interface RecyclerService {

    /**
     * Purges notes where purge_at <= now() in a batch.
     * Synchronous and safe to run directly in tests.
     *
     * @return the number of notes purged
     */
    int recycleOnce();
}
