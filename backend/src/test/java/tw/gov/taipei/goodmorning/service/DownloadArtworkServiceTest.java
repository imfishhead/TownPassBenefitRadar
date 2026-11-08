package tw.gov.taipei.goodmorning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gov.taipei.goodmorning.entity.Artwork;
import tw.gov.taipei.goodmorning.entity.User;
import tw.gov.taipei.goodmorning.observability.BusinessMetrics;
import tw.gov.taipei.goodmorning.repository.ArtworkRepository;
import tw.gov.taipei.goodmorning.security.CurrentUser;

class DownloadArtworkServiceTest {

    private static final byte[] SOURCE_PNG = {1, 2, 3};
    private static final byte[] COMPOSED_PNG = {4, 5, 6};
    // UTC 還是 11/7 16:30，台北已經是 11/8 00:30：確認印的是台北的「今天」
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T16:30:00Z"), ZoneId.of("Asia/Taipei"));
    private static final LocalDate TODAY_IN_TAIPEI = LocalDate.of(2026, 11, 8);
    private static final CurrentUser AUTHOR =
            new CurrentUser(7L, "author-subject", "author", "作者");
    private static final CurrentUser VIEWER =
            new CurrentUser(8L, "viewer-subject", "viewer", "讀者");

    private ArtworkRepository artworks;
    private ObjectStorage storage;
    private QrCodeComposer composer;
    private BusinessMetrics metrics;
    private DownloadArtworkService service;
    private Artwork artwork;

    @BeforeEach
    void setUp() {
        artworks = mock(ArtworkRepository.class);
        storage = mock(ObjectStorage.class);
        composer = mock(QrCodeComposer.class);
        metrics = mock(BusinessMetrics.class);
        service = new DownloadArtworkService(artworks, storage, composer, metrics, CLOCK);

        var owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        artwork = mock(Artwork.class);
        when(artwork.getOwner()).thenReturn(owner);
        when(artwork.getImageObjectKey()).thenReturn("artworks/7/source.png");
        when(artwork.getPolicySourceUrl()).thenReturn("https://example.gov.tw/snapshotted-policy");
        when(artworks.findById(42L)).thenReturn(Optional.of(artwork));
        when(storage.get("artworks/7/source.png")).thenReturn(SOURCE_PNG);
        when(composer.compose(SOURCE_PNG, "https://example.gov.tw/snapshotted-policy", TODAY_IN_TAIPEI))
                .thenReturn(COMPOSED_PNG);
    }

    @Test
    void authorCanDownloadWithoutIncreasingTheCount() {
        var download = service.download(AUTHOR, 42L);

        assertThat(download.content()).isEqualTo(COMPOSED_PNG);
        assertThat(download.filename()).isEqualTo("artwork-42.png");
        verify(artworks, never()).incrementDownloadCount(42L);
        verify(metrics, never()).artworkDownloaded();
    }

    @Test
    void nonAuthorDownloadAtomicallyIncreasesTheCountAfterComposition() {
        var download = service.download(VIEWER, 42L);

        assertThat(download.content()).isEqualTo(COMPOSED_PNG);
        verify(artworks).incrementDownloadCount(42L);
        verify(metrics).artworkDownloaded();
    }

    @Test
    void failedCompositionDoesNotIncreaseTheCount() {
        when(composer.compose(SOURCE_PNG, "https://example.gov.tw/snapshotted-policy", TODAY_IN_TAIPEI))
                .thenThrow(new IllegalStateException("broken image"));

        assertThatThrownBy(() -> service.download(VIEWER, 42L))
                .isInstanceOf(IllegalStateException.class);

        verify(artworks, never()).incrementDownloadCount(42L);
        verify(metrics, never()).artworkDownloaded();
    }
}
