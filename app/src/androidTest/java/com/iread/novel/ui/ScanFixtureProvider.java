package com.iread.novel.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Standalone test-APK provider: deliberately depends only on the Android runtime. */
public class ScanFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String id = DocumentsContract.getDocumentId(uri);
        String[] ids = "children".equals(uri.getLastPathSegment())
            ? ("root".equals(id) ? new String[]{"a", "sub", "skip"} : new String[]{"b"})
            : new String[]{id};
        String[] columns = projection != null ? projection : new String[]{
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        for (String item : ids) {
            String name = "a".equals(item) ? "目录验收甲.txt" :
                "b".equals(item) ? "目录验收乙.TXT" : "c".equals(item) ? "目录验收丙.epub" : "skip".equals(item) ? "ignored.pdf" : item;
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                switch (columns[i]) {
                    case Document.COLUMN_DOCUMENT_ID: row[i] = item; break;
                    case Document.COLUMN_DISPLAY_NAME: row[i] = name; break;
                    case Document.COLUMN_MIME_TYPE:
                        row[i] = ("root".equals(item) || "sub".equals(item)) ? Document.MIME_TYPE_DIR : "text/plain";
                        break;
                    case Document.COLUMN_FLAGS: row[i] = 0; break;
                    default: row[i] = null;
                }
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public String getType(Uri uri) {
        if ("c".equals(DocumentsContract.getDocumentId(uri))) return "application/x-unknown-book";
        return "skip".equals(DocumentsContract.getDocumentId(uri)) ? "application/pdf" : "text/plain";
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String id = DocumentsContract.getDocumentId(uri);
        if ("r".equals(mode) && "c".equals(id)) {
            File epub = new File(getContext().getCacheDir(), "scan-fixture-c.epub");
            String[][] entries = {
                {"mimetype", "application/epub+zip"},
                {"META-INF/container.xml", "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>"},
                {"book.opf", "<package><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>目录验收丙</dc:title></metadata><manifest><item id='one' href='one.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='one'/></spine></package>"},
                {"one.xhtml", "<html><body><h1>第一章</h1><p>用于测试的原创正文。</p></body></html>"}
            };
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new FileOutputStream(epub))) {
                for (String[] entry : entries) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(entry[0]));
                    zip.write(entry[1].getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
            return ParcelFileDescriptor.open(epub, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        if (!"r".equals(mode) || !("a".equals(id) || "b".equals(id))) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), "scan-fixture-" + id + ".txt");
        String text = "a".equals(id) ? "第一章 山风\n文件夹第一本书。" : "第一章 夜雨\n文件夹第二本书。";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new FileNotFoundException(error.toString());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
