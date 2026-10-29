package com.lumen.iptv;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;

@CapacitorPlugin(name = "Lumen")
public class LumenPlugin extends Plugin {

  @PluginMethod
  public void enter(final PluginCall call) {
    final String orient = call.getString("orientation", "keep");
    getActivity().runOnUiThread(() -> {
      Window w = getActivity().getWindow();
      WindowCompat.setDecorFitsSystemWindows(w, false);
      if (Build.VERSION.SDK_INT >= 28) {
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
      }
      WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
      c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      c.hide(WindowInsetsCompat.Type.systemBars());
      w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      if ("landscape".equals(orient)) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
      } else if ("auto".equals(orient)) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      }
      call.resolve();
    });
  }

  @PluginMethod
  public void exit(final PluginCall call) {
    getActivity().runOnUiThread(() -> {
      Window w = getActivity().getWindow();
      WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
      c.show(WindowInsetsCompat.Type.systemBars());
      WindowCompat.setDecorFitsSystemWindows(w, true);
      w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      call.resolve();
    });
  }

  @PluginMethod
  public void startDownload(PluginCall call) {
    String url = call.getString("url");
    String name = call.getString("name", "video.mp4");
    String title = call.getString("title", "Download");
    if (url == null) {
      call.reject("URL ausente");
      return;
    }
    try {
      DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
      DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
      r.setTitle(title);
      r.setDescription("Lumen");
      r.addRequestHeader("User-Agent", "VLC/3.0.20 LibVLC/3.0.20");
      r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      r.setAllowedOverMetered(true);
      r.setAllowedOverRoaming(true);
      r.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_MOVIES, name);
      long id = dm.enqueue(r);
      JSObject o = new JSObject();
      o.put("id", String.valueOf(id));
      o.put("path", new File(getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES), name).getAbsolutePath());
      call.resolve(o);
    } catch (Exception e) {
      call.reject("Falha ao iniciar: " + e.getMessage());
    }
  }

  @PluginMethod
  public void downloadStatus(PluginCall call) {
    try {
      long id = Long.parseLong(call.getString("id", "-1"));
      DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
      Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
      JSObject o = new JSObject();
      if (c != null && c.moveToFirst()) {
        o.put("status", c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)));
        o.put("bytes", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
        o.put("total", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
        o.put("reason", c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        String uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
        o.put("uri", uri == null ? "" : uri);
      } else {
        o.put("status", -1);
      }
      if (c != null) c.close();
      call.resolve(o);
    } catch (Exception e) {
      call.reject("Falha ao consultar: " + e.getMessage());
    }
  }

  @PluginMethod
  public void removeDownload(PluginCall call) {
    try {
      long id = Long.parseLong(call.getString("id", "-1"));
      DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
      dm.remove(id);
      call.resolve();
    } catch (Exception e) {
      call.reject("Falha ao remover: " + e.getMessage());
    }
  }

  @PluginMethod
  public void fileInfo(PluginCall call) {
    String path = call.getString("path", "");
    File f = new File(path);
    JSObject o = new JSObject();
    boolean ok = f.exists();
    o.put("exists", ok);
    o.put("size", ok ? f.length() : 0L);
    call.resolve(o);
  }

  private String mimeFor(String p) {
    String s = p.toLowerCase();
    if (s.endsWith(".mkv")) return "video/x-matroska";
    if (s.endsWith(".avi")) return "video/x-msvideo";
    if (s.endsWith(".webm")) return "video/webm";
    if (s.endsWith(".ts")) return "video/mp2t";
    return "video/mp4";
  }

  @PluginMethod
  public void openExternal(PluginCall call) {
    try {
      String path = call.getString("path");
      String url = call.getString("url");
      Intent i = new Intent(Intent.ACTION_VIEW);
      if (path != null && !path.isEmpty()) {
        File f = new File(path);
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
        i.setDataAndType(uri, mimeFor(path));
        i.setClipData(ClipData.newRawUri("", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } else if (url != null) {
        i.setDataAndType(Uri.parse(url), "video/*");
      } else {
        call.reject("Nada para abrir");
        return;
      }
      Intent ch = Intent.createChooser(i, "Abrir com");
      ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(ch);
      call.resolve();
    } catch (Exception e) {
      call.reject("Nenhum player encontrado: " + e.getMessage());
    }
  }
}
