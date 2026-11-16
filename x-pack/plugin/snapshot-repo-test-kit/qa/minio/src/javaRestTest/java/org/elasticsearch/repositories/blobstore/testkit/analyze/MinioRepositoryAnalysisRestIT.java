/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.repositories.blobstore.testkit.analyze;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.local.distribution.DistributionType;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class MinioRepositoryAnalysisRestIT extends AbstractRepositoryAnalysisRestTestCase {

    private static final String EMULATOR_BUCKET = "analysis-10mb";

    private static final AwsEmulatorS3Fixture emulator = new AwsEmulatorS3Fixture(EMULATOR_BUCKET);

    public static ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .distribution(DistributionType.DEFAULT)
        .setting("thread_pool.snapshot.max", "10")
        .setting("s3.client.repository_test_kit.region", "us-east-1")
        .setting("repositories.blobstore.testkit.analyze.copy_during_write_contention", "false")
        .setting("xpack.security.enabled", "false")
        .setting("xpack.ml.enabled", "false")
        // getAddress() already includes the https scheme. The protocol setting applies only when the endpoint has no scheme.
        .keystore("s3.client.repository_test_kit.access_key", emulator::getAccessKey)
        .keystore("s3.client.repository_test_kit.secret_key", emulator::getSecretKey)
        .setting("s3.client.repository_test_kit.endpoint", emulator::getAddress)
        .build();

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(emulator).around(cluster);

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
        return Settings.builder()
            .put("client", "repository_test_kit")
            .put("bucket", EMULATOR_BUCKET)
            .put("base_path", "repository_test_kit_tests")
            .build();
    }
}
