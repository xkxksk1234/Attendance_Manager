package com.maemong.attendance.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 단일 리소스(/icons/attendance_favicon.png)를 여러 해상도로 리사이즈해서
 * 프레임/트레이/탭 아이콘에 재사용하는 유틸.
 */
public final class IconUtil {
    private IconUtil() {}

    // 단일 원본 아이콘 경로 (리소스)
    private static final String BASE_ICON_PATH = "/icons/attendance_favicon.png";

    /** 원본 아이콘을 로드 (없으면 null) */
    private static BufferedImage loadBasePng() {
        var url = IconUtil.class.getResource(BASE_ICON_PATH);
        if (url == null) return null;
        try {
            return ImageIO.read(url);
        } catch (IOException e) {
            return null;
        }
    }

    /** 프레임/작업표시줄 아이콘: 16/32/48/128 px 세트 반환 */
    public static List<Image> loadAppIcons() {
        BufferedImage base = loadBasePng();
        List<Image> list = new ArrayList<>();
        if (base == null) return list;

        int[] sizes = {16, 32, 48, 128};
        for (int s : sizes) {
            list.add(resize(base, s, s));
        }
        return list;
    }

    /** 트레이용 작은 아이콘(16px) */
    public static Image loadSmallIcon() {
        BufferedImage base = loadBasePng();
        if (base == null) return null;
        return resize(base, 16, 16);
    }

    /** 임의 리소스를 ImageIcon으로 (탭 아이콘 등) */
    public static ImageIcon imageIcon(String path) {
        var url = IconUtil.class.getResource(path);
        return (url == null) ? null : new ImageIcon(url);
    }

    /** Java 9+ 멀티해상도 아이콘 (선택) */
    public static Image multiResAppIcon() {
        var icons = loadAppIcons();
        return icons.isEmpty()
                ? null
                : new java.awt.image.BaseMultiResolutionImage(icons.toArray(new Image[0]));
    }

    /* -------- 리사이즈 유틸(고품질) -------- */

    private static Image resize(Image src, int w, int h) {
        // 버퍼를 만들어 고품질로 리사이즈
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(src, 0, 0, w, h, null);
        } finally {
            g2.dispose();
        }
        return dst;
    }
}
