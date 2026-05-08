package com.Yubai.TouchDX;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        
        final SharedPreferences prefs = getSharedPreferences("TouchDX_Prefs", Context.MODE_PRIVATE);
        String lastIp = prefs.getString("lastIp", "127.0.0.1");

        final EditText editText = new EditText(this);
        editText.setText(lastIp);
        new AlertDialog.Builder(this)
            .setTitle("连接到电脑端服务器")
            .setMessage("如果是USB连线并执行了 adb reverse，请保持 127.0.0.1；如果是WiFi直连，请输入电脑的内网IP (如 192.168.x.x)")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("连接并启动悬浮窗", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    String ip = editText.getText().toString();
                    prefs.edit().putString("lastIp", ip).apply();
                    Intent serviceIntent = new Intent(MainActivity.this, OverlayService.class);
                    serviceIntent.putExtra("ip", ip);
                    startService(serviceIntent);
                    finish();
                }
            }).show();
    }
}
