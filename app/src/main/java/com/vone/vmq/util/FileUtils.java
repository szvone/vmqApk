package com.vone.vmq.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@SuppressWarnings("ALL")
public class FileUtils {

    public String readFileToString(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            StringBuilder readStringBuilder = new StringBuilder();
            String currentLine;
            while ((currentLine = in.readLine()) != null) {
                readStringBuilder.append(currentLine);
            }
            return readStringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean putStringToFile(String path, String text) {
        File file = new File(path);
        if (file.exists() && (!deleteFileSafely(file)))
            return false;
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file), 2048);
            out.write(text);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param file 要删除的文件
     */
    public boolean deleteFileSafely(File file) {
        if (file != null && file.exists()) {
            File tmp = getTmpFile(file, System.currentTimeMillis(), -1);
            if (file.renameTo(tmp)) { // 将源文件重命名
                return tmp.delete(); // 删除重命名后的文件
            } else {
                return file.delete();
            }
        }
        return false;
    }

    private File getTmpFile(File file, long time, int index) {
        File tmp;
        if (index == -1) {
            tmp = new File(file.getParent() + File.separator + time);
        } else {
            tmp = new File(file.getParent() + File.separator + time + "(" + index + ")");
        }
        if (!tmp.exists()) {
            return tmp;
        } else {
            return getTmpFile(file, time, index >= 1000 ? index : ++index);
        }
    }
}
