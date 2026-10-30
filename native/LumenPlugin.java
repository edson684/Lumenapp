package com.lumen.iptv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;

@SuppressLint("UnsafeOptInUsageError")
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

  // ---------------------------------------------------------------------
  // Player nativo (ExoPlayer / Media3): toca MKV, MP4, HLS, TS, HEVC etc.
  // direto no Android, com User-Agent de player e redirecionamentos http/https.
  // ---------------------------------------------------------------------
  private static final String PLAYER_UA = "VLC/3.0.20 LibVLC/3.0.20";
  private Dialog nDlg;
  private ExoPlayer nExo;
  private PluginCall nCall;
  private boolean nStarted;
  private int nRetry;

  // Proporcao da imagem (mesma ordem do player interno do app, em www/index.html)
  private static final String[] AR_NAMES = {"Ajustar", "Preencher", "Esticar", "16:9", "4:3"};
  private static final float[] AR_RATIO = {0f, 0f, 0f, 16f / 9f, 4f / 3f};
  private int nAr;

  /** Aplica a proporcao escolhida ao video. 0=ajustar, 1=preencher (corta), 2=esticar, 3=16:9, 4=4:3. */
  private void applyAspect(PlayerView pv) {
    View surface = pv.getVideoSurfaceView();
    if (surface == null || !(surface.getParent() instanceof View)) return;
    View frame = (View) surface.getParent();
    float r = AR_RATIO[nAr];
    int tw, th;
    if (r <= 0f) {
      if (nAr == 1) pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
      else if (nAr == 2) pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
      else pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
      tw = ViewGroup.LayoutParams.MATCH_PARENT;
      th = ViewGroup.LayoutParams.MATCH_PARENT;
    } else {
      int W = pv.getWidth(), H = pv.getHeight();
      if (W <= 0 || H <= 0) return; // ainda sem tamanho; o listener de layout chama de novo
      int w = W, h = Math.round(W / r);
      if (h > H) { h = H; w = Math.round(H * r); }
      pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
      tw = w;
      th = h;
    }
    ViewGroup.LayoutParams cur = frame.getLayoutParams();
    if (cur == null || cur.width != tw || cur.height != th) {
      frame.setLayoutParams(new FrameLayout.LayoutParams(tw, th, Gravity.CENTER));
    }
  }

  /** Fecha o player nativo (sempre na thread principal) e responde a chamada pendente. */
  private void finishNative(String errMsg) {
    PluginCall c = nCall;
    nCall = null;
    Dialog d = nDlg;
    nDlg = null;
    ExoPlayer e = nExo;
    nExo = null;
    if (e != null) {
      try { e.release(); } catch (Throwable t) { /* ignora */ }
    }
    if (d != null) {
      d.setOnDismissListener(null);
      try { d.dismiss(); } catch (Throwable t) { /* ignora */ }
    }
    if (c != null) {
      if (errMsg == null) {
        JSObject o = new JSObject();
        o.put("closed", true);
        o.put("aspect", nAr);
        c.resolve(o);
      } else {
        c.reject(errMsg, "PLAYER_ERROR");
      }
    }
  }

  @PluginMethod
  public void playNative(final PluginCall call) {
    final String url = call.getString("url");
    final String path = call.getString("path");
    final String title = call.getString("title", "");
    final boolean hasPath = path != null && !path.isEmpty();
    if (!hasPath && (url == null || url.isEmpty())) {
      call.reject("URL ausente", "NO_URL");
      return;
    }
    getActivity().runOnUiThread(() -> {
      try {
        finishNative(null);
        final Activity act = getActivity();
        final float dn = act.getResources().getDisplayMetrics().density;
        nStarted = false;
        nRetry = 0;
        nAr = Math.max(0, Math.min(AR_NAMES.length - 1, call.getInt("aspect", 0)));

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000);
        DefaultDataSource.Factory dsf = new DefaultDataSource.Factory(act, http);
        DefaultRenderersFactory rf = new DefaultRenderersFactory(act).setEnableDecoderFallback(true);
        final ExoPlayer exo = new ExoPlayer.Builder(act, rf)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dsf))
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setHandleAudioBecomingNoisy(true)
            .build();
        exo.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true);

        // Tela: video + controles padrao do Media3 (play/pausa, barra, +-10s, audio/legenda/velocidade)
        final FrameLayout root = new FrameLayout(act);
        root.setBackgroundColor(Color.BLACK);
        final PlayerView pv = new PlayerView(act);
        pv.setPlayer(exo);
        pv.setKeepScreenOn(true);
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        pv.setControllerShowTimeoutMs(3500);
        pv.setControllerHideOnTouch(true);
        root.addView(pv, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView close = new TextView(act);
        close.setText("\u00D7");
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        close.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x80000000);
        close.setBackground(bg);
        int sz = (int) (44 * dn);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(sz, sz, Gravity.TOP | Gravity.START);
        clp.setMargins((int) (28 * dn), (int) (18 * dn), 0, 0);
        root.addView(close, clp);

        final TextView ttl = new TextView(act);
        ttl.setText(title);
        ttl.setTextColor(Color.WHITE);
        ttl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ttl.setSingleLine(true);
        ttl.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tlp.setMargins((int) (84 * dn), (int) (28 * dn), (int) (210 * dn), 0);
        root.addView(ttl, tlp);

        // Botao de proporcao (toque para alternar: Ajustar, Preencher, Esticar, 16:9, 4:3)
        final TextView arb = new TextView(act);
        arb.setText("Propor\u00E7\u00E3o: " + AR_NAMES[nAr]);
        arb.setTextColor(Color.WHITE);
        arb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        arb.setTypeface(arb.getTypeface(), Typeface.BOLD);
        arb.setGravity(Gravity.CENTER);
        arb.setPadding((int) (16 * dn), 0, (int) (16 * dn), 0);
        GradientDrawable abg = new GradientDrawable();
        abg.setCornerRadius(22 * dn);
        abg.setColor(0x80000000);
        arb.setBackground(abg);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, (int) (44 * dn), Gravity.TOP | Gravity.END);
        alp.setMargins(0, (int) (18 * dn), (int) (28 * dn), 0);
        root.addView(arb, alp);
        arb.setOnClickListener(x -> {
          nAr = (nAr + 1) % AR_NAMES.length;
          arb.setText("Propor\u00E7\u00E3o: " + AR_NAMES[nAr]);
          applyAspect(pv);
          pv.showController();
        });
        pv.addOnLayoutChangeListener((vw, l, t, r, b, ol, ot, oright, ob) -> applyAspect(pv));
        applyAspect(pv);

        pv.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> {
          int s = vis == View.VISIBLE ? View.VISIBLE : View.GONE;
          close.setVisibility(s);
          ttl.setVisibility(s);
          arb.setVisibility(s);
        });

        final Dialog d = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.setContentView(root);
        d.setCancelable(true);
        close.setOnClickListener(x -> d.dismiss());
        d.setOnDismissListener(x -> finishNative(null));

        exo.addListener(new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int st) {
            if (st == Player.STATE_READY) nStarted = true;
            if (st == Player.STATE_ENDED) finishNative(null);
          }

          @Override
          public void onTracksChanged(Tracks tracks) {
            if (tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO)) {
              Toast.makeText(act, "O \u00E1udio deste filme est\u00E1 em um formato que o celular n\u00E3o decodifica (ex.: DTS). Troque a faixa de \u00E1udio na engrenagem, se houver outra.", Toast.LENGTH_LONG).show();
            }
          }

          @Override
          public void onPlayerError(PlaybackException e) {
            if (nStarted && nRetry < 2) {
              nRetry++;
              exo.prepare();
              return;
            }
            String m = e.getErrorCodeName();
            Throwable c = e.getCause();
            if (c instanceof HttpDataSource.InvalidResponseCodeException) {
              m = "HTTP " + ((HttpDataSource.InvalidResponseCodeException) c).responseCode;
            } else if (c != null && c.getMessage() != null) {
              m = m + " - " + c.getMessage();
            }
            finishNative(m);
          }
        });

        Uri uri = hasPath ? Uri.fromFile(new File(path)) : Uri.parse(url);
        exo.setMediaItem(MediaItem.fromUri(uri));
        exo.setPlayWhenReady(true);
        exo.prepare();

        nExo = exo;
        nDlg = d;
        nCall = call;

        Window w = d.getWindow();
        if (w != null) {
          w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
          w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
          w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
          }
        }
        d.show();
        if (w != null) {
          WindowCompat.setDecorFitsSystemWindows(w, false);
          WindowInsetsControllerCompat c2 = WindowCompat.getInsetsController(w, w.getDecorView());
          c2.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
          c2.hide(WindowInsetsCompat.Type.systemBars());
        }
        pv.showController();
      } catch (Throwable t) {
        String msg = "Falha ao abrir o player nativo: " + t;
        if (nCall == call) {
          finishNative(msg);
        } else {
          finishNative(null);
          call.reject(msg, "PLAYER_INIT");
        }
      }
    });
  }

  @PluginMethod
  public void stopNative(final PluginCall call) {
    getActivity().runOnUiThread(() -> {
      finishNative(null);
      call.resolve();
    });
  }

  @Override
  protected void handleOnPause() {
    if (nExo != null) nExo.pause();
    super.handleOnPause();
  }

  @Override
  protected void handleOnDestroy() {
    finishNative(null);
    super.handleOnDestroy();
  }
}
