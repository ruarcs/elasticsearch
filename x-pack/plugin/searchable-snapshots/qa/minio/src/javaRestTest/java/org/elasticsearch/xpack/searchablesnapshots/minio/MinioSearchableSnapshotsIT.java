/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.searchablesnapshots.minio;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Booleans;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.elasticsearch.test.fixtures.minio.MinioTestContainer;
import org.elasticsearch.test.fixtures.testcontainers.TestContainersThreadFilter;
import org.elasticsearch.xpack.searchablesnapshots.AbstractSearchableSnapshotsRestTestCase;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@ThreadLeakFilters(filters = { TestContainersThreadFilter.class })
public class MinioSearchableSnapshotsIT extends AbstractSearchableSnapshotsRestTestCase {

    private static final boolean USE_AWS_EMULATOR = Booleans.parseBoolean(System.getProperty("tests.use.aws.emulator", "false"));

    private static final String EMULATOR_BUCKET = "searchable-snapshots";

    private static final TestRule s3Fixture = USE_AWS_EMULATOR
        ? new AwsEmulatorS3Fixture(EMULATOR_BUCKET)
        : new MinioTestContainer(true, "s3_test_access_key", "s3_test_secret_key", "bucket");

    public static ElasticsearchCluster cluster = buildCluster();

    private static ElasticsearchCluster buildCluster() {
        var builder = ElasticsearchCluster.local()
            .distribution(DistributionType.DEFAULT)
            .setting("xpack.license.self_generated.type", "trial")
            .setting("xpack.searchable.snapshot.shared_cache.size", "16MB")
            .setting("xpack.searchable.snapshot.shared_cache.region_size", "256KB")
            .setting("xpack.searchable_snapshots.cache_fetch_async_thread_pool.keep_alive", "0ms")
            .setting("xpack.security.enabled", "false")
            .setting("xpack.ml.enabled", "false");
        if (USE_AWS_EMULATOR) {
            AwsEmulatorS3Fixture emulator = (AwsEmulatorS3Fixture) s3Fixture;
            // The endpoint is an IP, so the client cannot guess a region from it. Leaving the region unset logs a critical deprecation.
            // getAddress() already includes the https scheme. protocol applies only when the endpoint has no scheme.
            builder.keystore("s3.client.searchable_snapshots.access_key", emulator::getAccessKey)
                .keystore("s3.client.searchable_snapshots.secret_key", emulator::getSecretKey)
                .setting("s3.client.searchable_snapshots.endpoint", emulator::getAddress)
                .setting("s3.client.searchable_snapshots.region", "us-east-1");
        } else {
            MinioTestContainer minio = (MinioTestContainer) s3Fixture;
            builder.keystore("s3.client.searchable_snapshots.access_key", "s3_test_access_key")
                .keystore("s3.client.searchable_snapshots.secret_key", "s3_test_secret_key")
                .setting("s3.client.searchable_snapshots.protocol", () -> "http")
                .setting("s3.client.searchable_snapshots.endpoint", minio::getAddress);
        }
        return builder.build();
    }

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(s3Fixture).around(cluster);

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
        final String bucket = USE_AWS_EMULATOR ? EMULATOR_BUCKET : "bucket";
        final String basePath = "searchable_snapshots_tests";
        return Settings.builder().put("client", "searchable_snapshots").put("bucket", bucket).put("base_path", basePath).build();
    }
}
