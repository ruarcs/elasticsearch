/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.test.fixtures.minio;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates one bucket on the AWS emulator that is already running on this machine.
 * The suite supplies the bucket name. This rule does not start or stop the emulator.
 * A bucket that is already there is reused, so a later run keeps the objects from the earlier one.
 */
public final class AwsEmulatorS3Fixture extends ExternalResource {

    private static final String ENDPOINT = "https://127.0.0.1:9297";
    private static final String ALREADY_OWNED = "BucketAlreadyOwnedByYou";
    private static final String ALREADY_EXISTS = "BucketAlreadyExists";

    private final String bucket;
    private String accessKey;
    private String secretKey;
    private S3Client client;

    /**
     * @param bucket name of the bucket this suite will use
     */
    public AwsEmulatorS3Fixture(String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("bucket must not be null or empty");
        }
        this.bucket = bucket;
    }

    /**
     * The address the repository client should use for this emulator.
     * The host is an IP, which makes the AWS SDK use path style access.
     */
    public String getAddress() {
        return ENDPOINT;
    }

    /**
     * The access key read from this run's emulator credentials.
     * Valid only after the rule has started.
     */
    public String getAccessKey() {
        if (accessKey == null) {
            throw new IllegalStateException("access key is not available until the fixture has started");
        }
        return accessKey;
    }

    /**
     * The secret key read from this run's emulator credentials.
     * Valid only after the rule has started.
     */
    public String getSecretKey() {
        if (secretKey == null) {
            throw new IllegalStateException("secret key is not available until the fixture has started");
        }
        return secretKey;
    }

    /**
     * Reads this run's emulator credentials and creates the bucket.
     * {@code BucketAlreadyOwnedByYou} and {@code BucketAlreadyExists} count as success.
     */
    @Override
    public void before() throws Throwable {
        EmulatorCredentials credentials = readDefaultCredentials();
        accessKey = credentials.accessKey();
        secretKey = credentials.secretKey();
        // Certificate checks stay on. The JVM trust store is already pointed at this run's emulator CA.
        S3Client s3 = S3Client.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(ENDPOINT))
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
        try {
            createBucketOrAcceptExisting(s3);
        } catch (RuntimeException e) {
            s3.close();
            throw e;
        }
        client = s3;
    }

    /**
     * Closes the S3 client opened by {@link #before()}.
     * Does not stop the emulator.
     */
    @Override
    public void after() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void createBucketOrAcceptExisting(S3Client s3) {
        try {
            // This region rejects a location constraint, so the request leaves it unset.
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            String code = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            // The emulator keeps buckets until it is restarted. A rerun finds this bucket already owned by the same account.
            if (ALREADY_OWNED.equals(code) || ALREADY_EXISTS.equals(code)) {
                return;
            }
            throw e;
        }
    }

    private static EmulatorCredentials readDefaultCredentials() throws IOException {
        // Credentials change every time the emulator starts, so they are read here rather than baked into the test.
        Path path = Path.of(System.getProperty("user.home"), ".aws", "emulator", "9297", "emulator_credentials");
        String accessKey = null;
        String secretKey = null;
        boolean inDefault = false;
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                inDefault = "default".equals(line.substring(1, line.length() - 1).trim());
                continue;
            }
            int split = line.indexOf('=');
            if (inDefault == false || split < 0) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            if ("aws_access_key_id".equals(key)) {
                accessKey = value;
            } else if ("aws_secret_access_key".equals(key)) {
                secretKey = value;
            }
        }
        if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("emulator credentials file is missing the default access key or secret key");
        }
        return new EmulatorCredentials(accessKey, secretKey);
    }

    private record EmulatorCredentials(String accessKey, String secretKey) {}
}
