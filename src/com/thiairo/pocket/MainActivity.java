package com.thiairo.pocket;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Pocket - navegador local para o conteudo empacotado em assets.
 *
 * Tudo roda de file:///android_asset, entao funciona sem rede.
 * Nenhuma biblioteca externa: so framework Android.
 */
public class MainActivity extends Activity {

    private static final String TAG = "Pocket";
    private WebView web;
    private LinearLayout root;
    private TextView statusBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        buildUi();
        setContentView(root);
        loadContent();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        statusBar = new TextView(this);
        statusBar.setText("Pocket - offline");
        statusBar.setPadding(12, 8, 12, 8);
        statusBar.setTextSize(11);
        statusBar.setBackgroundColor(0xFF11161D);
        statusBar.setTextColor(0xFF8B95A7);
        statusBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(statusBar);

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        web = new WebView(this);
        configureWeb(web);
        web.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        holder.addView(web);
        root.addView(holder);
    }

    private void configureWeb(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDefaultTextEncodingName("utf-8");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowUniversalAccessFromFileURLs(true);
        }

        // ponte JS -> nativo
        w.addJavascriptInterface(new Bridge(), "Android");

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && (url.startsWith("file://") || url.startsWith("about:"))) {
                    return false; // carrega local
                }
                // link externo: nao sai do app sem avisar
                toast("Link externo bloqueado: " + url);
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                atualizarStatus();
            }

            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                statusBar.setText("erro ao carregar: " + desc);
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int progress) {
                if (progress < 100) statusBar.setText("carregando " + progress + "%");
                else atualizarStatus();
            }
        });
    }

    private void loadContent() {
        web.loadUrl("file:///android_asset/index.html");
    }

    private void atualizarStatus() {
        String modelo = Build.MODEL;
        String api = "API " + Build.VERSION.SDK_INT;
        boolean online = estaOnline();
        statusBar.setText(String.format(Locale.getDefault(),
                "%s · %s · %s", modelo, api, online ? "online" : "offline"));
    }

    private boolean estaOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Ponte exposta ao JavaScript como window.Android */
    public final class Bridge {

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> MainActivity.this.toast(msg));
        }

        @JavascriptInterface
        public String deviceInfo() {
            return Build.MANUFACTURER + " " + Build.MODEL
                    + " (API " + Build.VERSION.SDK_INT + ")";
        }

        @JavascriptInterface
        public boolean isOnline() {
            return estaOnline();
        }

        @JavascriptInterface
        public void reload() {
            runOnUiThread(() -> web.loadUrl("file:///android_asset/index.html"));
        }

        @JavascriptInterface
        public void clearCache() {
            runOnUiThread(() -> {
                web.clearCache(true);
                toast("cache limpo");
            });
        }
    }

    /** Botao voltar: volta na historia do WebView em vez de fechar */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        atualizarStatus();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ViewGroup p = (ViewGroup) web.getParent();
            if (p != null) p.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
