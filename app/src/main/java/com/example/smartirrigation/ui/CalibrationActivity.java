package com.example.smartirrigation.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.smartirrigation.R;
import com.example.smartirrigation.model.CalibrationRequest;
import com.example.smartirrigation.network.ApiCallback;
import com.example.smartirrigation.network.NetworkModule;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 标定向导：POST /api/app/calibration {device_id,kind,dry_raw,wet_raw}。
 * dry_raw=0% 读数，wet_raw=100% 读数；光照/水位为反向传感器时允许 wet_raw &lt; dry_raw。
 */
public class CalibrationActivity extends BaseActivity {

    private Spinner spKind;
    private TextInputEditText etDryRaw;
    private TextInputEditText etWetRaw;
    private MaterialButton btnSubmit;
    private ProgressBar progress;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
        setupPage("传感器标定", "两点标定 · 提升读数准确性", 0);

        spKind = findViewById(R.id.spKind);
        etDryRaw = findViewById(R.id.etDryRaw);
        etWetRaw = findViewById(R.id.etWetRaw);
        btnSubmit = findViewById(R.id.btnSubmitCalibration);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
    }

    private void submit() {
        if (deviceId() <= 0) {
            toast("请先绑定设备");
            return;
        }
        int dry;
        int wet;
        try {
            dry = Integer.parseInt(text(etDryRaw));
            wet = Integer.parseInt(text(etWetRaw));
        } catch (NumberFormatException e) {
            showError("请填写有效的原始读数（0~1023）");
            return;
        }
        if (dry < 0 || dry > 1023 || wet < 0 || wet > 1023) {
            showError("原始读数应在 0~1023 之间");
            return;
        }
        if (dry == wet) {
            showError("干/湿读数不能相同");
            return;
        }

        String kind = kindValue(spKind.getSelectedItemPosition());
        progress.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);
        tvError.setVisibility(View.GONE);
        NetworkModule.enqueue(NetworkModule.api().calibration(
                new CalibrationRequest(deviceId(), kind, dry, wet)), new ApiCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                progress.setVisibility(View.GONE);
                btnSubmit.setEnabled(true);
                toast("标定已保存到服务器");
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                btnSubmit.setEnabled(true);
                showError("标定失败：" + message);
            }
        });
    }

    private String kindValue(int position) {
        switch (position) {
            case 1:
                return "light";
            case 2:
                return "water";
            default:
                return "soil";
        }
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
    }

    private String text(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
