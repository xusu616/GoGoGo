package com.suku616miao.meow.miaowuwu;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.suku616miao.meow.miaowuwu.ui.UiPreferences.applyTheme(this);
        super.onCreate(savedInstanceState);
    }
}
