package com.llmgateway.ratelimit;

/** Centralised Redis key scheme for Bucket4j buckets, so the layout is in one place. */
public final class BucketKeys {

    private BucketKeys() {}

    public static String rpm(long teamId)        { return "rl:team:" + teamId + ":rpm"; }
    public static String lowPriorityRpm(long id) { return "rl:team:" + id + ":rpm:low"; }
    public static String tpm(long teamId)        { return "rl:team:" + teamId + ":tpm"; }
}
