package tw.gov.taipei.goodmorning.repository.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tw.gov.taipei.goodmorning.config.S3Properties;

class S3ObjectStorageIntegrationTest {

    private static final Region REGION = Region.AP_SOUTHEAST_2;
    private static final URI ENDPOINT = URI.create("http://minio:9000");
    private static final String BUCKET = "storage-test-" + UUID.randomUUID();
    private static S3Client client;
    private static S3Presigner presigner;
    private static S3ObjectStorage storage;

    @BeforeAll
    static void setUp() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("minioadmin", "minioadmin"));
        var s3Configuration = software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        client = S3Client.builder()
                .region(REGION)
                .endpointOverride(ENDPOINT)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        presigner = S3Presigner.builder()
                .region(REGION)
                .endpointOverride(ENDPOINT)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        client.createBucket(CreateBucketRequest.builder()
                .bucket(BUCKET)
                .createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(BucketLocationConstraint.AP_SOUTHEAST_2)
                        .build())
                .build());
        storage = new S3ObjectStorage(
                client,
                presigner,
                new S3Properties(
                        BUCKET,
                        REGION.id(),
                        ENDPOINT.toString(),
                        ENDPOINT.toString(),
                        Duration.ofMinutes(15)));
    }

    @AfterAll
    static void tearDown() {
        client.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET).build());
        presigner.close();
        client.close();
    }

    @Test
    void putsReadsPresignsAndDeletesAnObject() {
        var key = "artworks/1/test.png";
        var bytes = "png-data".getBytes(StandardCharsets.UTF_8);

        storage.put(key, bytes, "image/png");

        assertThat(storage.get(key)).isEqualTo(bytes);
        assertThat(storage.readUrl(key).toString())
                .contains(BUCKET)
                .contains("artworks/1/test.png");

        storage.delete(key);
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(SdkException.class);
    }
}
