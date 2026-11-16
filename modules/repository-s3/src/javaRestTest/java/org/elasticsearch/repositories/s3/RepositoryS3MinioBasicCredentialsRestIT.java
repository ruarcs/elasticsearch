/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.repositories.s3;

import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.function.Supplier;

import static fixture.aws.DynamicIdentifierSupplier.testClassIdentifierSupplier;

public class RepositoryS3MinioBasicCredentialsRestIT extends AbstractRepositoryS3RestTestCase {

    private static final String CLIENT = "minio_client";

    private static final Supplier<String> bucketSupplier = testClassIdentifierSupplier("bucket");
    private static final Supplier<String> basePathSupplier = testClassIdentifierSupplier("base_path");

    private static final LazyEmulatorFixture emulator = new LazyEmulatorFixture();

    public static ElasticsearchCluster cluster = ElasticsearchCluster.local()
        .module("repository-s3")
        .setting("s3.client." + CLIENT + ".endpoint", emulator::getAddress)
        // The endpoint is an IP, so the client cannot guess a region from it. Leaving the region unset logs a critical deprecation.
        .keystore("s3.client." + CLIENT + ".access_key", emulator::getAccessKey)
        .keystore("s3.client." + CLIENT + ".secret_key", emulator::getSecretKey)
        .setting("s3.client." + CLIENT + ".region", "us-east-1")
        .build();

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(emulator).around(cluster);

    /**
     * The bucket name comes from the test class, which is not available while this class is loading.
     * The emulator client is opened in {@link #before()}.
     */
    private static class LazyEmulatorFixture extends ExternalResource {
        private AwsEmulatorS3Fixture emulator;

        @Override
        protected void before() throws Throwable {
            emulator = new AwsEmulatorS3Fixture(bucketSupplier.get());
            emulator.before();
        }

        @Override
        protected void after() {
            emulator.after();
        }

        String getAddress() {
            return emulator.getAddress();
        }

        String getAccessKey() {
            return emulator.getAccessKey();
        }

        String getSecretKey() {
            return emulator.getSecretKey();
        }
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }

    @Override
    protected String getBucketName() {
        return bucketSupplier.get();
    }

    @Override
    protected String getBasePath() {
        return basePathSupplier.get();
    }

    @Override
    protected String getClientName() {
        return CLIENT;
    }
}
