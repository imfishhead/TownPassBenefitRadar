package tw.gov.taipei.goodmorning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.function.IntPredicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrCodeComposerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 8);
    // 1080 方圖：面板 216、外距 27、下方一列 45 → 面板 x 837..1053、y 792..1008，下方一列 y 1008..1053
    private static final Rectangle FOOTER = new Rectangle(837, 1008, 216, 45);

    private final QrCodeComposer composer = new QrCodeComposer();

    @Test
    void generatedQrCodeDecodesToTheSnapshottedPolicyUrl() throws Exception {
        var policyUrl = "https://www.gov.taipei/policy/snapshot?id=42";

        var output = composer.compose(solidPng(Color.ORANGE), policyUrl);
        var image = ImageIO.read(new ByteArrayInputStream(output));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(policyUrl);
    }

    @Test
    void keepsThe1080SquareCanvasAndPlacesThePanelInTheBottomRight() throws Exception {
        var output = composer.compose(solidPng(Color.BLACK), "https://example.gov.tw/policy");
        var image = ImageIO.read(new ByteArrayInputStream(output));

        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1080);

        var changedPixels = 0;
        var minimumChangedX = image.getWidth();
        var minimumChangedY = image.getHeight();
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0) {
                    changedPixels++;
                    minimumChangedX = Math.min(minimumChangedX, x);
                    minimumChangedY = Math.min(minimumChangedY, y);
                }
            }
        }

        assertThat(changedPixels).isGreaterThan(1_000);
        assertThat(minimumChangedX).isGreaterThanOrEqualTo(800);
        // 面板為了讓出下方一列，頂端在 y=792
        assertThat(minimumChangedY).isGreaterThanOrEqualTo(780);
        assertThat(image.getRGB(100, 100) & 0x00ffffff).isZero();
    }

    @Test
    void longestPolicyUrlStillDecodesWithTheLogoAndDateFooter() throws Exception {
        var policyUrl = "https://tpctax.gov.taipei/News_Content.aspx?n=F59FF484C69D07BF&s=EDE336CA31C04FAF&sms=72544237BBE4C5F6";

        var output = composer.compose(solidPng(Color.ORANGE), policyUrl, DATE);
        var image = ImageIO.read(new ByteArrayInputStream(output));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(policyUrl);
    }

    @Test
    void drawsTheTownPassLogoAndTheDateUnderTheQrPanel() throws Exception {
        var image = compose(DATE);

        assertThat(count(image, FOOTER, QrCodeComposerTest::isTownPassTeal))
                .as("logo teal pixels")
                .isGreaterThan(40);
        assertThat(count(image, FOOTER, QrCodeComposerTest::isWhite))
                .as("badge and date text")
                .isGreaterThan(300);
        // 日期在 logo 右邊：面板中線 x=945 右側要有白字
        assertThat(count(image, new Rectangle(945, 1008, 108, 45), QrCodeComposerTest::isWhite))
                .as("date text right of the logo")
                .isGreaterThan(80);
    }

    @Test
    void withoutADateOnlyTheLogoIsDrawnAndCenteredUnderThePanel() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(
                composer.compose(solidPng(Color.BLACK), "https://example.gov.tw/policy")));

        assertThat(count(image, FOOTER, QrCodeComposerTest::isTownPassTeal)).isGreaterThan(40);
        // logo 圓底直徑 36、置中於 x=945（927..963）；右邊不該有任何東西
        assertThat(count(image, new Rectangle(966, 1008, 87, 45), rgb -> rgb != 0)).isZero();
    }

    @Test
    void differentDatesProduceDifferentImages() throws Exception {
        var source = solidPng(Color.BLACK);

        assertThat(composer.compose(source, "https://example.gov.tw/policy", DATE))
                .isNotEqualTo(composer.compose(source, "https://example.gov.tw/policy", DATE.plusDays(1)));
    }

    @Test
    void footerStaysInsideTheOuterMargin() throws Exception {
        var image = compose(DATE);

        var lowestInkY = 0;
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0) {
                    lowestInkY = y;
                }
            }
        }
        // 下外距 27 → 內容最低到 y=1052
        assertThat(lowestInkY).isLessThanOrEqualTo(1080 - 27);
    }

    private BufferedImage compose(LocalDate date) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(
                composer.compose(solidPng(Color.BLACK), "https://example.gov.tw/policy", date)));
    }

    private static long count(BufferedImage image, Rectangle area, IntPredicate matches) {
        var total = 0L;
        for (var y = area.y; y < area.y + area.height; y++) {
            for (var x = area.x; x < area.x + area.width; x++) {
                if (matches.test(image.getRGB(x, y) & 0x00ffffff)) {
                    total++;
                }
            }
        }
        return total;
    }

    private static boolean isWhite(int rgb) {
        return channel(rgb, 16) >= 235 && channel(rgb, 8) >= 235 && channel(rgb, 0) >= 235;
    }

    /** 台北通 logo 的青色 #2EB6C7（容許反鋸齒造成的色差） */
    private static boolean isTownPassTeal(int rgb) {
        return Math.abs(channel(rgb, 16) - 0x2E) <= 24
                && Math.abs(channel(rgb, 8) - 0xB6) <= 24
                && Math.abs(channel(rgb, 0) - 0xC7) <= 24;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >> shift) & 0xff;
    }

    private byte[] solidPng(Color color) throws Exception {
        var image = new BufferedImage(1080, 1080, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
