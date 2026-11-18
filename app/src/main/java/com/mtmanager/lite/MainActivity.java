package com.mtmanager.lite;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private FileManagerFragment paneLeftFrag;
    private FileManagerFragment paneRightFrag;
    private boolean rootMode = false;
    private Button btnRootToggle, btnCopy, btnMove, btnZip, btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRootToggle = findViewById(R.id.btnRootToggle);
        btnCopy = findViewById(R.id.btnCopy);
        btnMove = findViewById(R.id.btnMove);
        btnZip = findViewById(R.id.btnZip);
        btnDelete = findViewById(R.id.btnDelete);

        checkPermissions();

        paneLeftFrag = new FileManagerFragment();
        paneRightFrag = new FileManagerFragment();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.paneLeft, paneLeftFrag);
        transaction.replace(R.id.paneRight, paneRightFrag);
        transaction.commit();

        btnRootToggle.setOnClickListener(v -> {
            rootMode = !rootMode;
            if (rootMode) {
                btnRootToggle.setText("Root: ON");
                btnRootToggle.setBackgroundTintColor(0xFF0066CC);
                Toast.makeText(this, "Root mode enabled via su shell", Toast.LENGTH_SHORT).show();
            } else {
                btnRootToggle.setText("Root: OFF");
                btnRootToggle.setBackgroundTintColor(0xFF333333);
            }
            paneLeftFrag.setRootMode(rootMode);
            paneRightFrag.setRootMode(rootMode);
        });

        btnCopy.setOnClickListener(v -> {
            File src = paneLeftFrag.getCurrentDir();
            File dest = paneRightFrag.getCurrentDir();
            if (src != null && dest != null) {
                paneRightFrag.copyFile(src, dest);
            }
        });

        btnMove.setOnClickListener(v -> {
            File src = paneLeftFrag.getCurrentDir();
            File dest = paneRightFrag.getCurrentDir();
            if (src != null && dest != null) {
                paneRightFrag.copyFile(src, dest);
                paneLeftFrag.deleteFile(src);
                paneLeftFrag.loadFiles(src.getParentFile());
                paneRightFrag.loadFiles(dest);
                Toast.makeText(this, "Moved successfully", Toast.LENGTH_SHORT).show();
            }
        });

        btnZip.setOnClickListener(v -> {
            File src = paneLeftFrag.getCurrentDir();
            if (src != null) {
                try {
                    File zipDest = new File(src.getParentFile(), src.getName() + ".zip");
                    paneLeftFrag.zipFolder(src, zipDest);
                    paneLeftFrag.loadFiles(src.getParentFile());
                } catch (IOException e) {
                    Toast.makeText(this, "Zip failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDelete.setOnClickListener(v -> {
            File src = paneLeftFrag.getCurrentDir();
            if (src != null) {
                paneLeftFrag.deleteFile(src);
                paneLeftFrag.loadFiles(src.getParentFile());
                Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
            }
        }
    }
}
