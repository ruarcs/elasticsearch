/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.repositories.blobstore.testkit.analyze;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Booleans;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.elasticsearch.test.fixtures.minio.MinioTestContainer;
import org.elasticsearch.test.fixtures.testcontainers.TestContainersThreadFilter;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@ThreadLeakFilters(filters = { TestContainersThreadFilter.class })
public class MinioRepositoryAnalysisRestIT extends AbstractRepositoryAnalysisRestTestCase {

    private static final boolean USE_AWS_EMULATOR = Booleans.parseBoolean(System.getProperty("tests.use.aws.emulator", "false"));

    private static final String EMULATOR_BUCKET = "analysis-1mb";

    private static final TestRule s3Fixture = USE_AWS_EMULATOR
        ? new AwsEmulatorS3Fixture(EMULATOR_BUCKET)
        : new MinioTestContainer(true, "s3_test_access_key", "s3_test_secret_key", "bucket");

    public static ElasticsearchCluster cluster = buildCluster();

    private static ElasticsearchCluster buildCluster() {
        var builder = ElasticsearchCluster.local()
            .distribution(DistributionType.DEFAULT)
            .setting("thread_pool.snapshot.max", "10")
            .setting("s3.client.repository_test_kit.region", "us-east-1")
            .setting("repositories.blobstore.testkit.analyze.copy_during_write_contention", "false")
            .setting("xpack.security.enabled", "false")
            .setting("xpack.ml.enabled", "false");
        if (USE_AWS_EMULATOR) {
            AwsEmulatorS3Fixture emulator = (AwsEmulatorS3Fixture) s3Fixture;
            // getAddress() already includes the https scheme. The protocol setting applies only when the endpoint has no scheme.
            builder.keystore("s3.client.repository_test_kit.access_key", emulator::getAccessKey)
                .keystore("s3.client.repository_test_kit.secret_key", emulator::getSecretKey)
                .setting("s3.client.repository_test_kit.endpoint", emulator::getAddress);
        } else {
            MinioTestContainer minio = (MinioTestContainer) s3Fixture;
            builder.keystore("s3.client.repository_test_kit.access_key", "s3_test_access_key")
                .keystore("s3.client.repository_test_kit.secret_key", "s3_test_secret_key")
                .setting("s3.client.repository_test_kit.endpoint", minio::getAddress);
        }
        return builder.build();
    }

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(s3Fixture).around(cluster);

    @Override
    protected String repositoryType() {
        return "s3";
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Override
    protected Settings repositorySettings() {
        final String bucket = USE_AWS_EMULATOR ? EMULATOR_BUCKET : "bucket";
        final String basePath = "repository_test_kit_tests";
        return Settings.builder().put("client", "repository_test_kit").put("bucket", bucket).put("base_path", basePath).build();
    }
}
