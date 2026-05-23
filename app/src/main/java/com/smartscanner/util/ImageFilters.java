package com.smartscanner.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public final class ImageFilters {
    public enum FilterType {
        GRAYSCALE,
        BLACK_AND_WHITE,
        SHADOW_REMOVED
    }

    private ImageFilters() {
    }

    public static Bitmap apply(Bitmap source, FilterType type) {
        switch (type) {
            case GRAYSCALE:
                return toGrayscale(source);
            case BLACK_AND_WHITE:
                return toBlackAndWhite(source);
            case SHADOW_REMOVED:
                return removeShadow(source);
            default:
                return source;
        }
    }

    public static Bitmap toGrayscale(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    public static Bitmap toBlackAndWhite(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] luminance = new int[pixels.length];
        int[] histogram = new int[256];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 0xff;
            int g = (color >> 8) & 0xff;
            int b = color & 0xff;
            int luma = (r * 299 + g * 587 + b * 114) / 1000;
            luminance[i] = luma;
            histogram[luma]++;
        }

        int threshold = computeOtsuThreshold(histogram, pixels.length);
        for (int i = 0; i < pixels.length; i++) {
            int alpha = pixels[i] & 0xff000000;
            int v = luminance[i] >= threshold ? 0x00ffffff : 0x00000000;
            pixels[i] = alpha | v;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }

    public static Bitmap removeShadow(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] luminance = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 0xff;
            int g = (color >> 8) & 0xff;
            int b = color & 0xff;
            luminance[i] = (r * 299 + g * 587 + b * 114) / 1000;
        }

        // Estimate the lighting/background by blurring at a large radius via downsample-blur-upsample,
        // then normalize each pixel against the local background to remove shadows.
        int[] background = downsampleBlurUpsample(luminance, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int bg = Math.max(1, background[i]);
            int normalized = Math.min(255, (luminance[i] * 255) / bg);
            int alpha = pixels[i] & 0xff000000;
            pixels[i] = alpha | (normalized << 16) | (normalized << 8) | normalized;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }

    private static int computeOtsuThreshold(int[] histogram, int totalPixels) {
        long sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (long) i * histogram[i];
        }

        long sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) {
                continue;
            }
            int wF = totalPixels - wB;
            if (wF == 0) {
                break;
            }
            sumB += (long) t * histogram[t];
            double mB = sumB / (double) wB;
            double mF = (sum - sumB) / (double) wF;
            double variance = (double) wB * wF * (mB - mF) * (mB - mF);
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }
        return threshold;
    }

    private static int[] downsampleBlurUpsample(int[] src, int width, int height) {
        int scale = 8;
        int dw = Math.max(1, width / scale);
        int dh = Math.max(1, height / scale);
        int[] down = new int[dw * dh];

        for (int y = 0; y < dh; y++) {
            int sy = y * scale;
            int sy2 = Math.min(height, sy + scale);
            for (int x = 0; x < dw; x++) {
                int sx = x * scale;
                int sx2 = Math.min(width, sx + scale);
                int sum = 0;
                int count = 0;
                for (int yy = sy; yy < sy2; yy++) {
                    int rowOffset = yy * width;
                    for (int xx = sx; xx < sx2; xx++) {
                        sum += src[rowOffset + xx];
                        count++;
                    }
                }
                down[y * dw + x] = sum / Math.max(1, count);
            }
        }

        int[] blurred = boxBlur(down, dw, dh, 4);

        int[] up = new int[width * height];
        for (int y = 0; y < height; y++) {
            int sy = Math.min(dh - 1, y / scale);
            int rowOffset = sy * dw;
            int upOffset = y * width;
            for (int x = 0; x < width; x++) {
                int sx = Math.min(dw - 1, x / scale);
                up[upOffset + x] = blurred[rowOffset + sx];
            }
        }
        return up;
    }

    private static int[] boxBlur(int[] src, int w, int h, int radius) {
        int[] temp = new int[src.length];
        int[] out = new int[src.length];
        int diameter = radius * 2 + 1;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sum = 0;
            for (int i = -radius; i <= radius; i++) {
                int idx = Math.max(0, Math.min(w - 1, i));
                sum += src[row + idx];
            }
            for (int x = 0; x < w; x++) {
                temp[row + x] = sum / diameter;
                int outIdx = Math.max(0, x - radius);
                int inIdx = Math.min(w - 1, x + radius + 1);
                sum += src[row + inIdx] - src[row + outIdx];
            }
        }

        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int i = -radius; i <= radius; i++) {
                int idx = Math.max(0, Math.min(h - 1, i));
                sum += temp[idx * w + x];
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / diameter;
                int outIdx = Math.max(0, y - radius);
                int inIdx = Math.min(h - 1, y + radius + 1);
                sum += temp[inIdx * w + x] - temp[outIdx * w + x];
            }
        }
        return out;
    }
}
