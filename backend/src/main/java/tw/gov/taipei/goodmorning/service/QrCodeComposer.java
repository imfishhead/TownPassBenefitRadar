package tw.gov.taipei.goodmorning.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class QrCodeComposer {

    private static final int OUTPUT_SIZE = 1080;
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 只加 QR Code 與台北通 logo，不印日期。 */
    public byte[] compose(byte[] sourcePng, String content) {
        return compose(sourcePng, content, null);
    }

    /**
     * 在右下角加上政策 QR Code，QR 下方一列放台北通 logo 與日期（yyyy.MM.dd）。
     *
     * @param date 印在 logo 旁的日期；null 則只印 logo
     */
    public byte[] compose(byte[] sourcePng, String content, LocalDate date) {
        try {
            var source = ImageIO.read(new ByteArrayInputStream(sourcePng));
            if (source == null || source.getWidth() != OUTPUT_SIZE || source.getHeight() != OUTPUT_SIZE) {
                throw new IllegalArgumentException("Stored artwork must be a 1080 x 1080 PNG");
            }

            var output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_RGB);
            var graphics = output.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            drawQrPanel(output, content, date);

            var bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(output, "png", bytes)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return bytes.toByteArray();
        } catch (IOException | WriterException exception) {
            throw new IllegalStateException("Failed to compose artwork QR code", exception);
        }
    }

    private void drawQrPanel(BufferedImage image, String content, LocalDate date) throws WriterException {
        var shortEdge = Math.min(image.getWidth(), image.getHeight());
        var panelSize = shortEdge / 5;
        var outerMargin = shortEdge / 40;
        var panelPadding = shortEdge / 60;
        var qrSize = panelSize - (panelPadding * 2);
        var panelX = image.getWidth() - outerMargin - panelSize;
        // 面板下方留一列給台北通 logo 與日期，所以面板整個往上移
        var footerHeight = shortEdge / 24;
        var panelY = image.getHeight() - outerMargin - footerHeight - panelSize;

        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(panelX, panelY, panelSize, panelSize);

            var hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN, 0,
                    EncodeHintType.CHARACTER_SET, "UTF-8");
            var matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints);
            graphics.setColor(Color.BLACK);
            for (var y = 0; y < matrix.getHeight(); y++) {
                for (var x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        graphics.fillRect(panelX + panelPadding + x, panelY + panelPadding + y, 1, 1);
                    }
                }
            }

            drawFooter(graphics, panelX + panelSize / 2.0, panelY + panelSize + footerHeight / 2.0, shortEdge, date);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * QR 面板正下方一列：白色圓底的台北通 logo，右邊接日期（白字加半透明黑邊，壓在照片上任何背景都看得清楚）。
     * 整列以 centerX 為中線置中；版面比例與前端 mock 的 editor/qr.ts 相同。
     */
    private void drawFooter(Graphics2D graphics, double centerX, double centerY, int shortEdge, LocalDate date) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        var badge = shortEdge / 30.0;
        var logoInset = shortEdge / 270.0;
        var gap = date == null ? 0.0 : shortEdge / 108.0;

        GlyphVector glyphs = null;
        var textWidth = 0.0;
        if (date != null) {
            var font = new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(shortEdge / 45f);
            glyphs = font.createGlyphVector(graphics.getFontRenderContext(), DATE_LABEL.format(date));
            textWidth = glyphs.getLogicalBounds().getWidth();
        }

        var rowX = centerX - (badge + gap + textWidth) / 2;
        var badgeY = centerY - badge / 2;
        graphics.setColor(Color.WHITE);
        graphics.fill(new Ellipse2D.Double(rowX, badgeY, badge, badge));
        TownPassLogo.draw(graphics, rowX + logoInset, badgeY + logoInset, badge - 2 * logoInset);

        if (glyphs != null) {
            // 以數字的視覺中心對齊整列的垂直中線
            var baseline = centerY - glyphs.getVisualBounds().getCenterY();
            var outline = glyphs.getOutline((float) (rowX + badge + gap), (float) baseline);
            graphics.setStroke(new BasicStroke(shortEdge / 216f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(0, 0, 0, 153));
            graphics.draw(outline);
            graphics.setColor(Color.WHITE);
            graphics.fill(outline);
        }
    }
}
