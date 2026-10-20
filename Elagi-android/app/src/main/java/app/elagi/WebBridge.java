package app.elagi;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Exposed to the page as window.AndroidElagi. */
public class WebBridge {
    private final Context ctx;
    WebBridge(Context c) { ctx = c; }

    @JavascriptInterface
    public void sync(String json) { AlarmScheduler.sync(ctx, json); }

    @JavascriptInterface
    public void saveFile(String name, String mime, String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            if (name == null || name.trim().isEmpty()) name = "elagi-" + System.currentTimeMillis() + ".pdf";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri u = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new Exception("insert");
                OutputStream o = ctx.getContentResolver().openOutputStream(u);
                o.write(data);
                o.close();
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                FileOutputStream o = new FileOutputStream(new File(dir, name));
                o.write(data);
                o.close();
            }
            toast("تم الحفظ في مجلد التنزيلات: " + name);
        } catch (Exception e) {
            toast("تعذّر حفظ الملف");
        }
    }

    private void toast(final String m) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() { @Override public void run() { Toast.makeText(ctx, m, Toast.LENGTH_LONG).show(); } });
    }
}
