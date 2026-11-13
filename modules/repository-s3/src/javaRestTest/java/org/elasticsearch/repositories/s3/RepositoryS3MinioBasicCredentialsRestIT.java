/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.repositories.s3;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

import org.elasticsearch.core.Booleans;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.fixtures.minio.AwsEmulatorS3Fixture;
import org.elasticsearch.test.fixtures.minio.MinioTestContainer;
import org.elasticsearch.test.fixtures.testcontainers.TestContainersThreadFilter;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.function.Supplier;

import static fixture.aws.DynamicIdentifierSupplier.testClassIdentifierSupplier;

@ThreadLeakFilters(filters = { TestContainersThreadFilter.class })
public class RepositoryS3MinioBasicCredentialsRestIT extends AbstractRepositoryS3RestTestCase {

    private static final String PREFIX = getIdentifierPrefix("RepositoryS3MinioBasicCredentialsRestIT");
    private static final String ACCESS_KEY = PREFIX + "access-key";
    private static final String SECRET_KEY = PREFIX + "secret-key";
    private static final String CLIENT = "minio_client";

    private static final Supplier<String> bucketSupplier = testClassIdentifierSupplier("bucket");
    private static final Supplier<String> basePathSupplier = testClassIdentifierSupplier("base_path");

    private static final boolean USE_AWS_EMULATOR = Booleans.parseBoolean(System.getProperty("tests.use.aws.emulator", "false"));

    private static final LazyMinioFixture minioFixture = new LazyMinioFixture();

    public static ElasticsearchCluster cluster = buildCluster();

    private static ElasticsearchCluster buildCluster() {
        var builder = ElasticsearchCluster.local()
            .module("repository-s3")
            .setting("s3.client." + CLIENT + ".endpoint", minioFixture::getAddress);
        if (USE_AWS_EMULATOR) {
            // The endpoint is an IP, so the client cannot guess a region from it. Leaving the region unset logs a critical deprecation.
            builder.keystore("s3.client." + CLIENT + ".access_key", minioFixture::getAccessKey)
                .keystore("s3.client." + CLIENT + ".secret_key", minioFixture::getSecretKey)
                .setting("s3.client." + CLIENT + ".region", "us-east-1");
        } else {
            builder.keystore("s3.client." + CLIENT + ".access_key", ACCESS_KEY)
                .keystore("s3.client." + CLIENT + ".secret_key", SECRET_KEY);
        }
        return builder.build();
    }

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(minioFixture).around(cluster);

    private static class LazyMinioFixture extends ExternalResource {
        private MinioTestContainer minioTestContainer;
        private AwsEmulatorS3Fixture emulator;

        @Override
        protected void before() throws Throwable {
            if (USE_AWS_EMULATOR) {
                emulator = new AwsEmulatorS3Fixture(bucketSupplier.get());
                emulator.before();
                return;
            }
            minioTestContainer = new MinioTestContainer(true, ACCESS_KEY, SECRET_KEY, bucketSupplier.get());
            minioTestContainer.start();
        }

        @Override
        protected void after() {
            if (USE_AWS_EMULATOR) {
                emulator.after();
                return;
            }
            minioTestContainer.stop();
        }

        String getAddress() {
            if (USE_AWS_EMULATOR) {
                return emulator.getAddress();
            }
            return minioTestContainer.getAddress();
        }

        String getAccessKey() {
            if (USE_AWS_EMULATOR) {
                return emulator.getAccessKey();
            }
            return ACCESS_KEY;
        }

        String getSecretKey() {
            if (USE_AWS_EMULATOR) {
                return emulator.getSecretKey();
            }
            return SECRET_KEY;
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
