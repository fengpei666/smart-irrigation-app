package com.example.smartirrigation.util;

import android.widget.AdapterView;

/**
 * Spinner 选中监听简化版：只关心 onItemSelected。
 */
public abstract class SimpleOnItemSelectedListener implements AdapterView.OnItemSelectedListener {
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // no-op
    }
}
