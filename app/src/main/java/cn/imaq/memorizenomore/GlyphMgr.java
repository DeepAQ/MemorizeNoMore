package cn.imaq.memorizenomore;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class GlyphMgr {

    public static MediaProjectionManager mpManager;
    public static MediaProjection mediaProjection;
    private static VirtualDisplay vDisplay;
    private static ImageReader imageReader;
    public static int screenWidth, screenHeight, screenDensity;

    public static int capInterval = 2000;
    private static int glyphStatus = 0; // 0:not running; 1:glyph started; 2:glyph received; 3:user input;
    private static int glyphClock = 0, recognizeCount = 0;
    private static int lastHit = 0;
    public static int glyphAreaTop, glyphAreaBottom;
    public static ArrayList<Bitmap> glyphs = new ArrayList<>();

    public static void mpInit(Intent resultData, DisplayMetrics metrics) {
        mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, resultData);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        vDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReader.getSurface(), null, null);
    }

    public static Bitmap capScreen() {
        if (mediaProjection == null) {
            return null;
        }
        long beginTime = System.currentTimeMillis();
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowPadding = planes[0].getRowStride() - pixelStride * screenWidth;
            Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();
            //System.out.println("capScreen used " + (System.currentTimeMillis() - beginTime) + "ms");
            return bitmap;
        } else {
            return null;
        }
    }

    public static void startRecognize(Bitmap bitmap) {
        // 优化 Complex 模式
        if (glyphStatus == 2) {
            recognizeGlyph(bitmap);
            recognizeCount++;
            if (recognizeCount < 5) return;
            if (recognizeInputStart(bitmap)) {
                // 用户开始画图
                System.out.println("> User input started!");
                System.out.println(">> Recognized " + glyphs.size() + " glyphs!");
                MainActivity.showGlyphs();
                glyphStatus = 3;
                capInterval = 2000;
            }
            if (recognizeCount < 40) return;
            if (recognizeCount > 100)
                capInterval = 2000;
        }

        System.out.println("Start recognize bitmap");
        if (recognizeGlyphWin(bitmap)) {
            // 在画图状态
            switch (glyphStatus) {
                case 0: // 刚刚开始画图
                    System.out.println("> Glyph hack started!");
                    recognizeGlyphArea(bitmap);
                    glyphStatus = 1;
                    capInterval = 200;
                    break;
                case 1: // 命令窗口期
                    if (recognizeGlyphReceived(bitmap)) {
                        // Glyph已接收
                        System.out.println("> Glyph hack received!");
                        glyphs = new ArrayList<>();
                        lastHit = 0;
                        recognizeCount = 0;
                        glyphClock = 0;
                        glyphStatus = 2;
                        capInterval = 0;
                    }
                    break;
            }
        } else {
            if (glyphStatus != 0) {
                // 退出了画图
                System.out.println("> Glyph hack closed!");
                MainActivity.closeFloatWin();
                glyphStatus = 0;
                capInterval = 2000;
            }
        }
    }

    public static boolean recognizeGlyphWin(Bitmap bitmap) {
        long beginTime = System.currentTimeMillis();
        // 检测顶部黄色横条
        boolean result1 = false;
        for (int i = 0; i < screenHeight * 0.1; i++) {
            int miss = 0;
            for (int j = 0; j < screenWidth; j++) {
                if (!comparePixel(bitmap.getPixel(j, i), 100, 69, 22, 10, 6, 10)) {
                    miss++;
                }
            }
            //System.out.println(i + " miss: " + miss);
            if (miss < screenWidth * 0.02) {
                result1 = true;
                //System.out.println("Found yellow line at y=" + i);
                break;
            }
        }
        //System.out.println("recognizeGlyphWin used " + (System.currentTimeMillis() - beginTime) + "ms");
        return result1;
    }

    public static void recognizeGlyphArea(Bitmap bitmap) {
        long beginTime = System.currentTimeMillis();
        glyphAreaTop = 0;
        glyphAreaBottom = screenHeight;
        // 检测上边界
        for (int i = (int) (screenHeight*0.25); i < screenHeight * 0.5; i++) {
            int hit = 0;
            for (int j = (int) (screenWidth*0.4); j < screenWidth * 0.6; j++) {
                if (comparePixel(bitmap.getPixel(j, i), 255, 250, 255, 25, 20, 25)) {
                    hit++;
                }
            }
            //System.out.println(i + " miss: " + miss);
            if (hit >= screenWidth*0.01) {
                glyphAreaTop = i;
                System.out.println("Found top border y=" + i);
                break;
            }
        }
        // 检测下边界
        for (int i = screenHeight-1; i > screenHeight * 0.8; i--) {
            int hit = 0;
            for (int j = (int) (screenWidth*0.4); j < screenWidth * 0.6; j++) {
                if (comparePixel(bitmap.getPixel(j, i), 255, 250, 255, 25, 20, 25)) {
                    hit++;
                }
            }
            //System.out.println(i + " miss: " + miss);
            if (hit >= screenWidth*0.01) {
                glyphAreaBottom = i;
                System.out.println("Found bottom border y=" + i);
                break;
            }
        }
        //baseHit = (glyphAreaBottom - glyphAreaTop) * screenWidth / 200;
        //System.out.println("baseHit = " + baseHit);
        //System.out.println("recognizeGlyphArea used " + (System.currentTimeMillis() - beginTime) + "ms");
    }

    public static boolean recognizeGlyphReceived(Bitmap bitmap) {
        long beginTime = System.currentTimeMillis();
        // 检测顶部黄色六边形
        boolean result1 = false;
        for (int i = (int) (screenHeight*0.1); i < screenHeight * 0.15; i++) {
            int hit = 0;
            for (int j = (int) (screenWidth*0.2); j < screenWidth*0.8; j++) {
                if (comparePixel(bitmap.getPixel(j, i), 168, 109, 19, 15, 10, 10)) {
                    hit++;
                }
            }
            //System.out.println(i + " miss: " + miss);
            if (hit > screenWidth*0.003 && hit < screenWidth*0.03) {
                result1 = true;
                //System.out.println("Found yellow 六边形 at y=" + i);
                break;
            }
        }
        boolean result2 = comparePixel(bitmap.getPixel(0, glyphAreaTop), 0, 0, 0, 10, 10, 10);
        //System.out.println("recognizeGlyphReceived used " + (System.currentTimeMillis() - beginTime) + "ms");
        return result1 && result2;
    }

    public static void recognizeGlyph(Bitmap bitmap) {
        System.out.println("Start recognize glyph");
        long beginTime = System.currentTimeMillis();
        int hit = 0;
        Bitmap tmpBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth()/4, bitmap.getHeight()/4, false);
        for (int i = glyphAreaTop/4; i < glyphAreaBottom/4; i++) {
            for (int j = 0; j < screenWidth/4; j++) {
                if (comparePixel(tmpBitmap.getPixel(j, i), 255, 255, 255, 15, 15, 15)) {
                    hit++;
                }
            }
        }
        System.out.println(">> Hit="+hit);
        if (lastHit == 0) {
            lastHit = hit * 3;
        } else {
            if (hit > lastHit) {
                if (glyphClock == 0) {
                    glyphClock = 1;
                    System.out.println("[[[[[ New glyph added!");
                    glyphs.add(bitmap);
                }
            }
            if (hit < lastHit) {
                if (glyphClock == 1) {
                    glyphClock = 0;
                    System.out.println("]]]]] waiting for next glyph");
                }
            }
            lastHit = hit;
        }
        //System.out.println("recognizeGlyph used " + (System.currentTimeMillis() - beginTime) + "ms");
    }

    public static boolean recognizeInputStart(Bitmap bitmap) {
        long beginTime = System.currentTimeMillis();
        //System.out.println("recognizeInputStart used " + (System.currentTimeMillis() - beginTime) + "ms");
        boolean result = !comparePixel(bitmap.getPixel(0, glyphAreaTop), 0, 0, 0, 10, 10, 10);
        return result;
    }

    public static boolean comparePixel(int pixel, int r, int g, int b, int dR, int dG, int dB) {
        int oR = (pixel >> 16) & 0xff;
        int oG = (pixel >> 8) & 0xff;
        int oB = pixel & 0xff;
        return (Math.abs(oR - r) < dR && Math.abs(oG - g) < dG && Math.abs(oB - b) < dB);
    }

    public static String colorToString(int pixel) {
        int oR = (pixel >> 16) & 0xff;
        int oG = (pixel >> 8) & 0xff;
        int oB = pixel & 0xff;
        return ("R=" + oR + " G=" + oG + " B=" + oB);
    }

}
