package io.pijun.george;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Identicon {

    static void draw(@NonNull Bitmap bitmap, @NonNull String data) {
        byte[] hash = getHash(data);
        draw(new Canvas(bitmap), hash, new Paint());
    }

    private static void draw(@NonNull Canvas canvas, @NonNull byte[] hash, @NonNull Paint paint) {
        paint.setColor(getHashColor(hash[15]));

        int boxWidth = canvas.getWidth() / 5;
        int boxHeight = canvas.getHeight() / 5;

        canvas.drawColor(Color.WHITE);
        int i=0;
        for (int x=0; x<3; x++) {
            int col = x % 3;
            int symCol = 4 - col;
            for (int y = 0; y<5; y++) {
                if (Math.abs(hash[i]) % 2 == 1) {
                    canvas.drawRect(col*boxWidth, y*boxHeight, (col+1)*boxWidth, (y+1)*boxHeight, paint);
                    if (symCol > 2) {
                        canvas.drawRect(symCol*boxWidth, y*boxHeight, (symCol+1)*boxWidth, (y+1)*boxHeight, paint);
                    }
                }
                i++;
            }
        }
    }

    private static byte[] getHash(@NonNull String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data.getBytes());
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("MD5 algo not found", ex);
        }
    }

    @ColorInt
    private static int getHashColor(byte val) {
        int absVal = Math.abs(val);
        if (absVal < 16) {
            return Color.parseColor("#F44336"); // red
        } else if (absVal < 32) {
            return Color.parseColor("#E91E63"); // pink
        } else if (absVal < 48) {
            return Color.parseColor("#673AB7"); // deep purple
        } else if (absVal < 64) {
            return Color.parseColor("#3F51B5"); // indigo
        } else if (absVal < 80) {
            return Color.parseColor("#00BCD4"); // cyan
        } else if (absVal < 96) {
            return Color.parseColor("#4CAF50"); // green
        } else if (absVal < 112) {
            return Color.parseColor("#FFEB38"); // yellow
        } else {
            return Color.parseColor("#FF9800"); // orange
        }
    }
}
