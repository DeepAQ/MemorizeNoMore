package cn.imaq.memorizenomore;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static TextView textView;
    private static ImageView imageView;

    private static WindowManager wm;
    private static WindowManager.LayoutParams wmParams;
    private static View floatView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GlyphMgr.mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (GlyphMgr.mediaProjection == null) {
            startActivityForResult(GlyphMgr.mpManager.createScreenCaptureIntent(), 1);
        }

        textView = (TextView) findViewById(R.id.textView);

        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = GlyphMgr.capScreen();
                if (bitmap != null) {
                    //((ImageView) findViewById(R.id.imageView)).setImageBitmap(bitmap);
                    //int pixel = bitmap.getPixel(1, 1);
                    //System.out.println("A" + (pixel>>24) + " R" + ((pixel>>16)&0xff) + " G" + ((pixel>>8)&0xff) + " B" + (pixel&0xff));
                    GlyphMgr.startRecognize(bitmap);
                } else {
                    System.out.println("Bitmap null, no changes on screen.");
                }
                System.out.println("Waiting " + GlyphMgr.capInterval + "ms");
                handler.postDelayed(this, GlyphMgr.capInterval);
            }
        };
        handler.postDelayed(runnable, GlyphMgr.capInterval);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                GlyphMgr.mpInit(data, metrics);

                wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wmParams = new WindowManager.LayoutParams();
                wmParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                wmParams.format = PixelFormat.RGBA_8888;
                wmParams.gravity = Gravity.LEFT | Gravity.TOP;
                wmParams.x = 0;
                wmParams.y = (int) (GlyphMgr.screenHeight * 0.1);
                wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
                wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                floatView = getLayoutInflater().inflate(R.layout.floatwin, null);
                imageView = (ImageView) floatView.findViewById(R.id.imageViewFloat);
            } else {
                startActivityForResult(GlyphMgr.mpManager.createScreenCaptureIntent(), 1);
            }
        }
    }

    public static void showGlyphs() {
        try {
            wm.removeView(floatView);
        } catch (Exception e) {
            // do nothing
        }
        int n = GlyphMgr.glyphs.size();
        //textView.setText("Recogized " + n +" glyphs!");
        if (n > 0) {
            Bitmap bitmap = Bitmap.createBitmap(GlyphMgr.screenWidth * n, GlyphMgr.glyphAreaBottom - GlyphMgr.glyphAreaTop, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            for (int i = 0; i < n; i++) {
                canvas.drawBitmap(GlyphMgr.glyphs.get(i), GlyphMgr.screenWidth * i, -GlyphMgr.glyphAreaTop, null);
            }
            bitmap = Bitmap.createScaledBitmap(bitmap, (int) (GlyphMgr.screenWidth * n / 5), (int) ((GlyphMgr.glyphAreaBottom - GlyphMgr.glyphAreaTop) / 5), false);
            imageView.setImageBitmap(bitmap);
            wm.addView(floatView, wmParams);
            System.out.println("Float window opened");
        }
    }

    public static void closeFloatWin() {
        try {
            wm.removeView(floatView);
        } catch (Exception e) {
            // do nothing
        }
    }
}
