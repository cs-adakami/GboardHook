package de.robv.android.xposed;

import java.io.File;

public class XSharedPreferences {
    public final File file = new File(".");

    public XSharedPreferences(String packageName, String prefFileName) {
    }

    public String getString(String key, String defValue) {
        return defValue;
    }

    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }
}
