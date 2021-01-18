package io.flutter.plugins.webviewflutter;

import android.Manifest;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.content.res.Configuration;
import android.os.Environment;
import android.os.Message;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.os.Parcelable;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import java.io.IOException;
import java.io.File;
import android.content.Context;
import android.app.Activity;
import androidx.core.content.FileProvider;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import android.database.Cursor;
import io.flutter.plugin.common.PluginRegistry;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.widget.FrameLayout;
import android.content.pm.ActivityInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.util.Log;

// 参考 https://github.com/fluttercommunity/flutter_webview_plugin/blob/29c0480c96c1cbefcdff51fe7982cd0c29c5ead1/android/src/main/java/com/flutter_webview_plugin/WebviewManager.java

public class FlutterWebViewChromeClient extends WebChromeClient
    implements PluginRegistry.ActivityResultListener {

  public static Context applicationContext;

  public static Activity activity;

  private static final String fileProviderAuthorityExtension = "flutter_inappwebview.fileprovider";

  private static Uri outputFileUri;

  private String outputFileUri2;

  private Uri fileUri;

  private static final int REQUEST_CODE_FILE_CHOOSER = 1;

  private ValueCallback<Uri[]> filePathCallback;

  private PluginRegistry.Registrar registrar;

  private static FlutterWebViewChromeClient flutterWebViewChromeClient;

  protected static final FrameLayout.LayoutParams FULLSCREEN_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);

  protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY_KITKAT = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_FULLSCREEN |
          View.SYSTEM_UI_FLAG_IMMERSIVE |
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

  protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_FULLSCREEN;

  private int mOriginalOrientation;
  private View mCustomView;
  private CustomViewCallback mCustomViewCallback;
  private int mOriginalSystemUiVisibility;
  // int orientation = getResources().getConfiguration().orientation;

  private FlutterWebViewChromeClient(PluginRegistry.Registrar registrar, Context context) {
    super();
    this.registrar = registrar;
    this.applicationContext = context;
    registrar.addActivityResultListener(this);
    this.activity = registrar.activity();
  }

  public static FlutterWebViewChromeClient getInstance(PluginRegistry.Registrar registrar, Context context) {
    if (flutterWebViewChromeClient == null) {
      flutterWebViewChromeClient = new FlutterWebViewChromeClient(registrar, context);
      return flutterWebViewChromeClient;
    } else {
      return flutterWebViewChromeClient;
    }
  }

  @Override
  public void onProgressChanged(WebView view, int newProgress) {
    super.onProgressChanged(view, newProgress);
  }

  @Override
  public void onReceivedTitle(WebView view, String title) {
    super.onReceivedTitle(view, title);
  }

  @Override
  public void onReceivedIcon(WebView view, Bitmap icon) {
    super.onReceivedIcon(view, icon);
  }

  @Override
  public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
    super.onReceivedTouchIconUrl(view, url, precomposed);
  }

  protected ViewGroup getRootView() {
    return (ViewGroup) activity.findViewById(android.R.id.content);
  }

  @Override
  public void onShowCustomView(View paramView, CustomViewCallback paramCustomViewCallback) {
      if (this.mCustomView != null) {
          onHideCustomView();
          return;
      }

      View decorView = getRootView();

      this.mCustomView = paramView;
      this.mOriginalSystemUiVisibility = decorView.getSystemUiVisibility();
      this.mOriginalOrientation = activity.getRequestedOrientation();
      this.mCustomViewCallback = paramCustomViewCallback;
      this.mCustomView.setBackgroundColor(Color.BLACK);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        decorView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY_KITKAT);
      } else {
        decorView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY);
      }

      activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
      ((FrameLayout) decorView).addView(this.mCustomView, FULLSCREEN_LAYOUT_PARAMS);
  }


  @Override
  public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
    super.onShowCustomView(view, requestedOrientation, callback);
  }

  @Override
  public void onHideCustomView() {
    View decorView = getRootView();
    ((FrameLayout) decorView).removeView(this.mCustomView);
    this.mCustomView = null;
    decorView.setSystemUiVisibility(this.mOriginalSystemUiVisibility);
    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    this.mCustomViewCallback.onCustomViewHidden();
    this.mCustomViewCallback = null;
    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
  }

  @Override
  public boolean onCreateWindow(
      WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
    return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
  }

  @Override
  public void onRequestFocus(WebView view) {
    super.onRequestFocus(view);
  }

  @Override
  public void onCloseWindow(WebView window) {
    super.onCloseWindow(window);
  }

  @Override
  public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
    return super.onJsAlert(view, url, message, result);
  }

  @Override
  public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
    return super.onJsConfirm(view, url, message, result);
  }

  @Override
  public boolean onJsPrompt(
      WebView view, String url, String message, String defaultValue, JsPromptResult result) {
    return super.onJsPrompt(view, url, message, defaultValue, result);
  }

  @Override
  public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
    return super.onJsBeforeUnload(view, url, message, result);
  }

  @Override
  public void onExceededDatabaseQuota(
      String url,
      String databaseIdentifier,
      long quota,
      long estimatedDatabaseSize,
      long totalQuota,
      WebStorage.QuotaUpdater quotaUpdater) {
    super.onExceededDatabaseQuota(
        url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater);
  }

  @Override
  public void onReachedMaxAppCacheSize(
      long requiredStorage, long quota, WebStorage.QuotaUpdater quotaUpdater) {
    super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
  }

  @Override
  public void onGeolocationPermissionsShowPrompt(
      String origin, GeolocationPermissions.Callback callback) {
    super.onGeolocationPermissionsShowPrompt(origin, callback);
  }

  @Override
  public void onGeolocationPermissionsHidePrompt() {
    super.onGeolocationPermissionsHidePrompt();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onPermissionRequest(PermissionRequest request) {
    super.onPermissionRequest(request);
  }

  @Override
  public void onPermissionRequestCanceled(PermissionRequest request) {
    super.onPermissionRequestCanceled(request);
  }

  @Override
  public boolean onJsTimeout() {
    return super.onJsTimeout();
  }

  @Override
  public void onConsoleMessage(String message, int lineNumber, String sourceID) {
    super.onConsoleMessage(message, lineNumber, sourceID);
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    return super.onConsoleMessage(consoleMessage);
  }

  @Nullable
  @Override
  public Bitmap getDefaultVideoPoster() {
    return super.getDefaultVideoPoster();
  }

  @Nullable
  @Override
  public View getVideoLoadingProgressView() {
    return super.getVideoLoadingProgressView();
  }

  @Override
  public void getVisitedHistory(ValueCallback<String[]> callback) {
    super.getVisitedHistory(callback);
  }

  private File createCapturedFile(String prefix, String suffix) throws IOException {
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
      String imageFileName = prefix + "_" + timeStamp;
      File storageDir = applicationContext.getExternalFilesDir(null);
      return File.createTempFile(imageFileName, suffix, storageDir);
  }

  private Uri getOutputFilename(String intentType) {
      String prefix = "";
      String suffix = "";

      if (intentType == MediaStore.ACTION_IMAGE_CAPTURE) {
          prefix = "image-";
          suffix = ".jpg";
      } else if (intentType == MediaStore.ACTION_VIDEO_CAPTURE) {
          prefix = "video-";
          suffix = ".mp4";
      }

      String packageName = applicationContext.getPackageName();
      File capturedFile = null;
      try {
          capturedFile = createCapturedFile(prefix, suffix);
      } catch (IOException e) {
          e.printStackTrace();
      }
      return FileProvider.getUriForFile(applicationContext, packageName + ".fileprovider", capturedFile);
  }


  protected boolean needsCameraPermission() {
    boolean needed = false;

    // Activity activity = inAppBrowserActivity != null ? inAppBrowserActivity : Shared.activity;
    PackageManager packageManager = activity.getPackageManager();
    try {
      String[] requestedPermissions = packageManager.getPackageInfo(applicationContext.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
      if (Arrays.asList(requestedPermissions).contains(Manifest.permission.CAMERA)
              && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        needed = true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      needed = true;
    }

    return needed;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean onShowFileChooser(
      WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
    this.filePathCallback = filePathCallback;

    List<Intent> intentList = new ArrayList<Intent>();

    if (needsCameraPermission()) {
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 513469796);
      // should return false
      return false;
    } else {
      // photo
      Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      fileUri = getOutputFilename(MediaStore.ACTION_IMAGE_CAPTURE);
      takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
      intentList.add(takePhotoIntent);
      

      // file
      final boolean allowMultiple = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
      Intent contentSelectionIntent = fileChooserParams.createIntent();
      // contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
      contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
      // contentSelectionIntent.setType("*/*");

      Intent[] intentArray = intentList.toArray(new Intent[intentList.size()]);
      Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
      chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
      chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

      try {
        activity.startActivityForResult(chooserIntent, REQUEST_CODE_FILE_CHOOSER);
      } catch (ActivityNotFoundException e) {
        e.printStackTrace();
        return false;
      }
    }
    return true;
  }

  private long getFileSize(Uri fileUri) {
      Cursor returnCursor = applicationContext.getContentResolver().query(fileUri, null, null, null, null);
      returnCursor.moveToFirst();
      int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
      return returnCursor.getLong(sizeIndex);
  }


  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE_FILE_CHOOSER
        && (resultCode == RESULT_OK || resultCode == RESULT_CANCELED)) {
      // photo
      if (fileUri != null && getFileSize(fileUri) > 0) {
        filePathCallback.onReceiveValue(new Uri[]{fileUri});
      } else {
        filePathCallback.onReceiveValue(
          WebChromeClient.FileChooserParams.parseResult(resultCode, data));
      } 
    }
    return false;
  }
}