package tw.gov.taipei.goodmorning.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 台北通 logo（logo_S）。路徑資料取自官方開源專案 taipei-doit/TownPass 的 assets/svg/logo_S.svg（GPL-3.0），
 * 未修改形狀與配色，與前端 GoodMorning/public/brand/townpass-logo.svg 是同一份。
 * 台北通名稱與標誌屬臺北市政府，使用前請依主辦單位規範確認。
 *
 * <p>只支援這份 SVG 用到的路徑指令（絕對座標的 M、L、C、V、Z）；換檔後若出現其他指令會直接丟例外，
 * 不會默默畫錯。
 */
final class TownPassLogo {

    private static final String RESOURCE = "/brand/townpass-logo.svg";
    private static final double VIEW_BOX = 24;
    private static final Pattern PATH_ELEMENT = Pattern.compile("<path\\b[^>]*>");
    private static final Pattern ATTRIBUTE_D = Pattern.compile("\\sd=\"([^\"]+)\"");
    private static final Pattern ATTRIBUTE_FILL = Pattern.compile("\\sfill=\"(#[0-9A-Fa-f]{6})\"");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private static final List<Piece> PIECES = load();

    private record Piece(Path2D path, Color fill) {}

    private TownPassLogo() {}

    /** 在 (x, y) 為左上角、邊長 size 的正方形內畫出 logo；不會改動傳入的 Graphics2D 的座標轉換。 */
    static void draw(Graphics2D graphics, double x, double y, double size) {
        var original = graphics.getTransform();
        var originalColor = graphics.getColor();
        try {
            graphics.translate(x, y);
            var scale = size / VIEW_BOX;
            graphics.scale(scale, scale);
            for (var piece : PIECES) {
                graphics.setColor(piece.fill());
                graphics.fill(piece.path());
            }
        } finally {
            graphics.setTransform(original);
            graphics.setColor(originalColor);
        }
    }

    private static List<Piece> load() {
        String svg;
        try (InputStream stream = TownPassLogo.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        var pieces = new ArrayList<Piece>();
        var elements = PATH_ELEMENT.matcher(svg);
        while (elements.find()) {
            var element = elements.group();
            var d = ATTRIBUTE_D.matcher(element);
            var fill = ATTRIBUTE_FILL.matcher(element);
            if (!d.find() || !fill.find()) {
                throw new IllegalStateException("TownPass logo path needs both d and a #rrggbb fill: " + element);
            }
            pieces.add(new Piece(parse(d.group(1)), Color.decode(fill.group(1))));
        }
        if (pieces.isEmpty()) {
            throw new IllegalStateException("No <path> elements found in " + RESOURCE);
        }
        return List.copyOf(pieces);
    }

    private static Path2D parse(String d) {
        var tokens = new ArrayList<String>();
        var matcher = TOKEN.matcher(d);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        var path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        var index = 0;
        var command = ' ';
        var currentX = 0.0;
        var currentY = 0.0;
        while (index < tokens.size()) {
            var token = tokens.get(index);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                index++;
                if (command == 'Z') {
                    path.closePath();
                }
                continue;
            }
            switch (command) {
                case 'M' -> {
                    currentX = number(tokens, index++);
                    currentY = number(tokens, index++);
                    path.moveTo(currentX, currentY);
                    command = 'L'; // M 後面接著的座標對視為直線
                }
                case 'L' -> {
                    currentX = number(tokens, index++);
                    currentY = number(tokens, index++);
                    path.lineTo(currentX, currentY);
                }
                case 'C' -> {
                    var x1 = number(tokens, index++);
                    var y1 = number(tokens, index++);
                    var x2 = number(tokens, index++);
                    var y2 = number(tokens, index++);
                    currentX = number(tokens, index++);
                    currentY = number(tokens, index++);
                    path.curveTo(x1, y1, x2, y2, currentX, currentY);
                }
                case 'V' -> {
                    currentY = number(tokens, index++);
                    path.lineTo(currentX, currentY);
                }
                default -> throw new IllegalArgumentException("Unsupported SVG path command '" + command + "'");
            }
        }
        return path;
    }

    private static double number(List<String> tokens, int index) {
        if (index >= tokens.size() || Character.isLetter(tokens.get(index).charAt(0))) {
            throw new IllegalArgumentException("SVG path ended before all coordinates were read");
        }
        return Double.parseDouble(tokens.get(index));
    }
}
