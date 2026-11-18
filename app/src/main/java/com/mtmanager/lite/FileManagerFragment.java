package com.mtmanager.lite;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileManagerFragment extends Fragment {

    private File currentDir;
    private ListView listViewFiles;
    private TextView tvPath;
    private List<File> fileList;
    private ArrayAdapter<String> adapter;
    private boolean isRootMode = false;

    public void setRootMode(boolean rootMode) {
        this.isRootMode = rootMode;
        loadFiles(currentDir);
    }

    public File getCurrentDir() {
        return currentDir;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_manager, container, false);
        listViewFiles = view.findViewById(R.id.listViewFiles);
        tvPath = view.findViewById(R.id.tvPath);

        currentDir = Environment.getExternalStorageDirectory();
        if (currentDir == null || !currentDir.exists()) {
            currentDir = new File("/");
        }

        fileList = new ArrayList<>();
        List<String> names = new ArrayList<>();
        adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, names) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(13.spToPx());
                return v;
            }
        };
        listViewFiles.setAdapter(adapter);

        listViewFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < fileList.size()) {
                    File selected = fileList.get(position);
                    if (selected.isDirectory()) {
                        if (selected.canRead() || isRootMode) {
                            loadFiles(selected);
                        } else {
                            Toast.makeText(getContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Open text editor
                        Intent intent = new Intent(getActivity(), TextEditorActivity.class);
                        intent.putExtra("path", selected.getAbsolutePath());
                        startActivity(intent);
                    }
                }
            }
        });

        loadFiles(currentDir);
        return view;
    }

    public void loadFiles(File dir) {
        if (dir == null) return;
        currentDir = dir;
        tvPath.setText(currentDir.getAbsolutePath());

        fileList.clear();
        List<String> names = new ArrayList<>();

        if (currentDir.getParentFile() != null) {
            fileList.add(currentDir.getParentFile());
            names.add("../");
        }

        File[] files = currentDir.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                fileList.add(file);
                names.add((file.isDirectory() ? "[DIR] " : "") + file.getName());
            }
        }

        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
    }

    public void copyFile(File source, File destDir) {
        try {
            File dest = new File(destDir, source.getName());
            if (source.isDirectory()) {
                dest.mkdir();
                File[] children = source.listFiles();
                if (children != null) {
                    for (File child : children) {
                        copyFile(child, dest);
                    }
                }
            } else {
                FileInputStream in = new FileInputStream(source);
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                in.close();
                out.close();
            }
            Toast.makeText(getContext(), "Copied successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "Copy failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFile(child);
                }
            }
        }
        file.delete();
    }

    public void zipFolder(File sourceFile, File zipFile) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        compressFile(sourceFile, sourceFile.getName(), zos);
        zos.close();
        Toast.makeText(getContext(), "Zipped successfully", Toast.LENGTH_SHORT).show();
    }

    private void compressFile(File file, String fileName, ZipOutputStream zos) throws IOException {
        if (file.isHidden()) return;
        if (file.isDirectory()) {
            if (fileName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(fileName));
            } else {
                zos.putNextEntry(new ZipEntry(fileName + "/"));
            }
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    compressFile(child, fileName + "/" + child.getName(), zos);
                }
            }
            return;
        }
        FileInputStream fis = new FileInputStream(file);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
        fis.close();
    }
}

class UtilsExtensions {
    // helper int extension simulation
}
