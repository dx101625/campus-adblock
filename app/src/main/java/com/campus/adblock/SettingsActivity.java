package com.campus.adblock;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import com.campus.adblock.ui.CampusUi;
import com.campus.adblock.ui.StatusController;
import com.campus.adblock.ui.UiKit;

/** Own-process Miuix host. The hook entry points have no dependency on this class. */
public class SettingsActivity extends ComponentActivity {
    private CampusUi campusUi;
    private UiKit ui;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new UiKit(this);
        getWindow().setStatusBarColor(ui.color("bg"));
        getWindow().setNavigationBarColor(ui.color("bg"));
        int flags = ui.night() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26 && !ui.night()) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        campusUi = new CampusUi(this, state);
        setContentView(campusUi.createView());
    }

    @Override protected void onResume() {
        super.onResume();
        ProfileShortcuts.publish(this);
        if (campusUi != null) campusUi.refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (campusUi != null) campusUi.save(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (campusUi != null) campusUi.close();
        super.onDestroy();
    }

    public void openQiekj() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(StatusController.Q);
        if (intent == null) { Toast.makeText(this, ui.text("not_installed"), Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, ui.text("launch_failed"), Toast.LENGTH_SHORT).show(); }
    }

    public void openLsposed() {
        String[][] candidates = {
            {"org.lsposed.manager", "org.lsposed.manager.ui.activity.MainActivity"},
            {"com.solohsu.android.edxp.manager", "com.solohsu.android.edxp.manager.activity.MainActivity"},
            {"me.weishu.kernelsu", "me.weishu.kernelsu.ui.MainActivity"},
            {"me.weishu.kernelsu", "me.weishu.kernelsu.MainActivity"},
            {"com.lsposed.manager", "com.lsposed.manager.ui.activity.MainActivity"},
            {"com.topjohnwu.magisk", "com.topjohnwu.magisk.ui.MainActivity"}
        };
        for (String[] candidate : candidates) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(candidate[0], candidate[1])));
                return;
            } catch (Exception ignored) { }
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(candidate[0]);
                if (intent != null) { startActivity(intent); return; }
            } catch (Exception ignored) { }
        }
        try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:" + getPackageName()))); }
        catch (Exception ignored) { }
        Toast.makeText(this, ui.text("lsposed_missing"), Toast.LENGTH_LONG).show();
    }
}
