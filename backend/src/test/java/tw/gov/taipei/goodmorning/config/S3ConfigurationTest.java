package tw.gov.taipei.goodmorning.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tw.gov.taipei.goodmorning.repository.s3.S3ObjectStorage;

class S3ConfigurationTest {

    @Test
    void usesTheBrowserReachableEndpointForPresignedUrls() {
        var properties = new S3Properties(
                "good-morning-local",
                "ap-southeast-2",
                "http://minio:9000",
                "http://localhost:9000",
                Duration.ofMinutes(15));
        var configuration = new S3Configuration();

        try (var client = configuration.s3Client(properties);
                var presigner = configuration.s3Presigner(properties)) {
            var storage = new S3ObjectStorage(client, presigner, properties);

            var url = storage.readUrl("artworks/1/test.png");

            assertThat(url.getHost()).isEqualTo("localhost");
            assertThat(url.getPort()).isEqualTo(9000);
            assertThat(url.getPath())
                    .isEqualTo("/good-morning-local/artworks/1/test.png");
        }
    }
}
