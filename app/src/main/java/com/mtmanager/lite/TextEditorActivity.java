package com.mtmanager.lite;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.github.Rosemoe.sora.widget.CodeEditor;
import io.github.Rosemoe.sora.langs.java.JavaLanguage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TextEditorActivity extends AppCompatActivity {

    private CodeEditor codeEditor;
    private File targetFile;
    private TextView tvFileName;
    private Button btnSave, btnSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        codeEditor = findViewById(R.id.codeEditor);
        tvFileName = findViewById(R.id.tvFileName);
        btnSave = findViewById(R.id.btnSave);
        btnSearch = findViewById(R.id.btnSearch);

        // Set Editor Syntax Highlighting (Java / TextMate support)
        codeEditor.setEditorLanguage(new JavaLanguage());

        String path = getIntent().getStringExtra("path");
        if (path != null) {
            targetFile = new File(path);
            tvFileName.setText(targetFile.getName());
            loadFileContent();
        }

        btnSave.setOnClickListener(v -> saveFileContent());

        btnSearch.setOnClickListener(v -> showSearchDialog());
    }

    private void loadFileContent() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(targetFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            codeEditor.setText(sb.toString());
        } catch (IOException e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFileContent() {
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write(codeEditor.getText().toString());
            Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Search and Replace");

        final android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final android.widget.EditText inputSearch = new android.widget.EditText(this);
        inputSearch.setHint("Search text");
        inputSearch.setTextColor(0xFFFFFFFF);
        layout.addView(inputSearch);

        final android.widget.EditText inputReplace = new android.widget.EditText(this);
        inputReplace.setHint("Replace with");
        inputReplace.setTextColor(0xFFFFFFFF);
        layout.addView(inputReplace);

        builder.setView(layout);

        builder.setPositiveButton("Replace All", (dialog, which) -> {
            String searchStr = inputSearch.getText().toString();
            String replaceStr = inputReplace.getText().toString();
            String content = codeEditor.getText().toString();
            if (!searchStr.isEmpty()) {
                String newContent = content.replace(searchStr, replaceStr);
                codeEditor.setText(newContent);
                Toast.makeText(this, "Replaced occurrences", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
