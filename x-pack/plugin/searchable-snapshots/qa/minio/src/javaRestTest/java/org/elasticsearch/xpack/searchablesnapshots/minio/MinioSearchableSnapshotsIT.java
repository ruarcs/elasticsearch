/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.searchablesnapshots.minio;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.elasticsearch.xpack.searchablesnapshots.AbstractSearchableSnapshotsRestTestCase;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class MinioSearchableSnapshotsIT extends AbstractSearchableSnapshotsRestTestCase {

    private static final String EMULATOR_BUCKET = "searchable-snapshots";

    private static final AwsEmulatorS3Fixture emulator = new AwsEmulatorS3Fixture(EMULATOR_BUCKET);

    public static ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .distribution(DistributionType.DEFAULT)
        .setting("xpack.license.self_generated.type", "trial")
        .setting("xpack.searchable.snapshot.shared_cache.size", "16MB")
        .setting("xpack.searchable.snapshot.shared_cache.region_size", "256KB")
        .setting("xpack.searchable_snapshots.cache_fetch_async_thread_pool.keep_alive", "0ms")
        .setting("xpack.security.enabled", "false")
        .setting("xpack.ml.enabled", "false")
        // The endpoint is an IP, so the client cannot guess a region from it. Leaving the region unset logs a critical deprecation.
        // getAddress() already includes the https scheme. protocol applies only when the endpoint has no scheme.
        .keystore("s3.client.searchable_snapshots.access_key", emulator::getAccessKey)
        .keystore("s3.client.searchable_snapshots.secret_key", emulator::getSecretKey)
        .setting("s3.client.searchable_snapshots.endpoint", emulator::getAddress)
        .setting("s3.client.searchable_snapshots.region", "us-east-1")
        .build();

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(emulator).around(cluster);

    @Override
    protected String writeRepositoryType() {
        return "s3";
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Override
    protected Settings writeRepositorySettings() {
        return Settings.builder()
            .put("client", "searchable_snapshots")
            .put("bucket", EMULATOR_BUCKET)
            .put("base_path", "searchable_snapshots_tests")
            .build();
    }
}
