package com.android.test.shortcut;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "TestShortCut";

    private static final int ADD_SHORT_CUT_CODE = 0x02;
    private static final String SHORT_CUT_ID_PARAM = "id";

    private Button mBtnOne, mBtnMore;
    private Handler mHandler;
    private Looper mLooper;
    private static BlockingQueue sBlockingQueue = new LinkedBlockingQueue(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HandlerThread thread = new HandlerThread("shortcut");
        thread.start();
        mLooper = thread.getLooper();
        mHandler = new Handler(mLooper);

        mBtnOne = (Button) findViewById(R.id.id_btn_one);
        mBtnOne.setOnClickListener(this);


        mBtnMore = (Button) findViewById(R.id.id_btn_more);
        mBtnMore.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLooper != null) {
            mLooper.quit();
            mLooper = null;
        }
        mHandler = null;
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnOne) {
            List<Bookmak> bookmaks = new ArrayList<>();
            bookmaks.add(new Bookmak("魅族游戏", "http://gamebbs.meizu.com/"));
            createShortCut(bookmaks);
        } else if (v == mBtnMore) {
            List<Bookmak> bookmaks = new ArrayList<>();
            bookmaks.add(new Bookmak("魅族游戏", "http://gamebbs.meizu.com/"));
            bookmaks.add(new Bookmak("Flyme官网", "http://www.flyme.cn/"));
            bookmaks.add(new Bookmak("魅族社区", "http://bbs.meizu.cn/"));
            bookmaks.add(new Bookmak("魅族官网", "http://m.meizu.com/"));
            createShortCut(bookmaks);
        }
    }

    private void createShortCut(List<Bookmak> bookmaks) {
        final Context context = getApplicationContext();
        Map<String, ShortcutInfo> shortInfos = new HashMap<>();

        /**
         * 构造多个ShortcutInfo
         */
        for (int i = 0; i < bookmaks.size(); i++) {
            Bookmak bookmak = bookmaks.get(i);
            if (!isShortCutExists(bookmak.name, bookmak.url)) {
                Intent launcherIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(bookmak.url));
                launcherIntent.setClassName("com.android.test.shortcut", "com.android.test.shortcut.MainActivity");
                launcherIntent.addCategory(Intent.CATEGORY_DEFAULT);
                ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, formatShortCutId(bookmak.name, bookmak.url))
                    .setShortLabel(bookmak.name)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(launcherIntent);

                shortInfos.put(bookmak.name, builder.build());
            } else {
                Log.i(TAG, "lable: " + bookmak.name + " & url: " + bookmak.url + " exists!!!");
            }
        }

        /**
         * 创建多个快捷方式
         */
        final ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);

        final Collection<ShortcutInfo> shortInfoEx = new ArrayList<>(shortInfos.values());

        shortInfos.clear();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ShortcutInfo info : shortInfoEx) {
                    if (info != null) {
                        try {
                            sBlockingQueue.offer(new Object(), 2, TimeUnit.SECONDS);
                        } catch (Exception e) {
                        }

                        final String id = info.getId();
                        Intent intent = new Intent(context, MyReceiver.class);
                        intent.putExtra(SHORT_CUT_ID_PARAM, id);

                        PendingIntent shortcutCallbackIntent = PendingIntent.getBroadcast(context, ADD_SHORT_CUT_CODE,
                            intent, PendingIntent.FLAG_UPDATE_CURRENT);

                        boolean success = shortcutManager.requestPinShortcut(info, shortcutCallbackIntent.getIntentSender());

                        Log.i(TAG, "requestPinShortcut() id: " + id + ", lable: " + info.getShortLabel() + ", success: " + success);
                    }
                }
            }
        });
    }

    /**
     * 回调广播
     */
    public static class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String id = intent.getStringExtra(SHORT_CUT_ID_PARAM);
            Log.i(TAG, "onReceive short cut: " + intent.toString() + ", id: " + id);
            try {
                sBlockingQueue.clear();
            } catch (Exception e){

            }
        }
    }

    public boolean isShortCutExists(String bookmarkTitle, String bookmarkUrl) {
        final Context context = getApplicationContext();
        ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        List<ShortcutInfo> shortcutInfos = shortcutManager.getPinnedShortcuts();
        final String id = formatShortCutId(bookmarkTitle, bookmarkUrl);
        if (shortcutInfos != null && shortcutInfos.size() > 0) {
            for (ShortcutInfo shortcutInfo : shortcutInfos) {
                if (shortcutInfo != null && id.equals(shortcutInfo.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatShortCutId(String bookmarkTitle, String bookmarkUrl) {
        return Integer.toString((bookmarkTitle + ":" + bookmarkUrl).hashCode());
    }

    private class Bookmak {
        public String name;
        public String url;

        public Bookmak(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }
}
