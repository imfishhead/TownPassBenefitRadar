package tw.gov.taipei.goodmorning.service;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import tw.gov.taipei.goodmorning.dto.ArtworkDownload;
import tw.gov.taipei.goodmorning.exception.ApiException;
import tw.gov.taipei.goodmorning.observability.BusinessMetrics;
import tw.gov.taipei.goodmorning.repository.ArtworkRepository;
import tw.gov.taipei.goodmorning.security.CurrentUser;

@Service
public class DownloadArtworkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadArtworkService.class);

    private final ArtworkRepository artworks;
    private final ObjectStorage storage;
    private final QrCodeComposer composer;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public DownloadArtworkService(
            ArtworkRepository artworks,
            ObjectStorage storage,
            QrCodeComposer composer,
            BusinessMetrics metrics,
            Clock clock) {
        this.artworks = artworks;
        this.storage = storage;
        this.composer = composer;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public ArtworkDownload download(CurrentUser currentUser, long artworkId) {
        var artwork = artworks.findById(artworkId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ARTWORK_NOT_FOUND",
                        "Artwork was not found"));

        var source = getSource(artwork.getImageObjectKey());
        // QR 下方印「下載當天」的日期（應用程式時鐘為 Asia/Taipei）
        var composed = composer.compose(source, artwork.getPolicySourceUrl(), LocalDate.now(clock));
        var counted = !artwork.getOwner().getId().equals(currentUser.id());
        if (counted) {
            artworks.incrementDownloadCount(artworkId);
            metrics.artworkDownloaded();
        }
        LOGGER.info(
                "artwork_downloaded artworkId={} userId={} counted={}",
                artworkId,
                currentUser.id(),
                counted);
        return new ArtworkDownload(composed, "artwork-%d.png".formatted(artworkId));
    }

    private byte[] getSource(String objectKey) {
        try {
            return storage.get(objectKey);
        } catch (SdkException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_UNAVAILABLE",
                    "Object storage is temporarily unavailable",
                    exception);
        }
    }
}
