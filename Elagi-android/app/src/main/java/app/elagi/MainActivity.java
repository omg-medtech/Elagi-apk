package app.elagi;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String START = "https://omg-medtech.github.io/Elagi-app/";
    private static final int REQ_FILE = 51, REQ_PERM = 52;
    static volatile boolean visible = false;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private String inject = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        showOverLock(getIntent());
        try {
            InputStream is = getAssets().open("inject.js");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            inject = new String(buf, "UTF-8");
        } catch (Exception ignored) {}

        web = new WebView(this);
        setContentView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " ElagiAPK/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new WebBridge(getApplicationContext()), "AndroidElagi");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) { v.evaluateJavascript(inject, null); }
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(inject, null); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sc = u.getScheme();
                if ("https".equals(sc) || "http".equals(sc)) {
                    String host = u.getHost() == null ? "" : u.getHost();
                    if (host.endsWith("github.io") || host.endsWith("supabase.co") || host.endsWith("supabase.com")) return false;
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                    return true;
                }
                if ("blob".equals(sc) || "data".equals(sc) || "about".equals(sc)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) { cb.invoke(origin, true, false); }
            @Override public void onPermissionRequest(final PermissionRequest r) { runOnUiThread(new Runnable() { @Override public void run() { r.grant(r.getResources()); } }); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) { return chooseFile(cb); }
        });
        web.setDownloadListener(new android.webkit.DownloadListener() {
            @Override public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                if (url != null && url.startsWith("blob:")) {
                    String name = android.webkit.URLUtil.guessFileName(url, cd, mime);
                    String js = "(function(){fetch('" + url + "').then(function(r){return r.blob()}).then(function(bl){var fr=new FileReader();fr.onloadend=function(){var d=String(fr.result);AndroidElagi.saveFile('" + name.replace("'", "") + "','" + (mime == null ? "" : mime) + "',d.substring(d.indexOf(',')+1))};fr.readAsDataURL(bl)})})()";
                    web.evaluateJavascript(js, null);
                } else if (url != null) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                }
            }
        });

        askPermissions();
        if (b != null) web.restoreState(b); else web.loadUrl(START);
    }

    private void showOverLock(Intent i) {
        if (i != null && i.getBooleanExtra("alarm", false)) {
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
            else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        showOverLock(i);
        if (web != null) web.evaluateJavascript("try{checkDue()}catch(e){}", null);
    }

    private void askPermissions() {
        ArrayList<String> need = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    private boolean chooseFile(ValueCallback<Uri[]> cb) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        Intent chooser = Intent.createChooser(pick, "اختر صورة الدواء");
        try {
            File f = File.createTempFile("cam_", ".jpg", getCacheDir());
            camUri = FileProvider.getUriForFile(this, "app.elagi.files", f);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
        } catch (Exception e) {
            camUri = null;
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            fileCb = null;
            cb.onReceiveValue(null);
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_FILE) { super.onActivityResult(req, res, data); return; }
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (camUri != null) out = new Uri[]{camUri};
        }
        if (fileCb != null) { fileCb.onReceiveValue(out); fileCb = null; }
    }

    @Override
    protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); if (web != null) web.saveState(b); }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else moveTaskToBack(true);
    }

    @Override protected void onResume() { super.onResume(); visible = true; if (web != null) web.onResume(); }
    @Override protected void onPause() { super.onPause(); visible = false; if (web != null) web.onPause(); }
}
