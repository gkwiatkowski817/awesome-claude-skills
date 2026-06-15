package com.homevault.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin native shell around the HomeVault PWA. The web app is bundled in
 * assets/www and served over a virtual https origin so IndexedDB, the service
 * worker and getUserMedia all behave exactly as they do in Chrome.
 */
public class MainActivity extends AppCompatActivity {

    private static final String DOMAIN = "appassets.androidplatform.net";
    private static final int REQ_FILE = 1001;
    private static final int REQ_PERMS = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(DOMAIN)
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        // Let the service worker resolve bundled assets too, so offline caching
        // and expiry notifications work from the virtual https origin.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                new ServiceWorkerClientCompat() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        return assetLoader.shouldInterceptRequest(request.getUrl());
                    }
                });
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                launchFileChooser();
                return true;
            }
        });

        requestRuntimePermissions();
        webView.loadUrl("https://" + DOMAIN + "/www/index.html");
    }

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMS);
        }
    }

    /** Offer both "take a photo" and "pick from gallery" for <input type=file>. */
    private void launchFileChooser() {
        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("image/*");

        Intent cameraIntent = null;
        cameraOutputUri = null;
        try {
            File dir = new File(getCacheDir(), "captures");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File photo = new File(dir, "cap_" + System.currentTimeMillis() + ".jpg");
            cameraOutputUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photo);
            cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            cameraOutputUri = null;
        }

        Intent chooser = Intent.createChooser(contentIntent, "Add photo");
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (cameraOutputUri != null) {
                results = new Uri[]{cameraOutputUri};
            }
        }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
        cameraOutputUri = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
