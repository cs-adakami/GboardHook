package de.robv.android.xposed;

import android.content.SharedPreferences;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class XSharedPreferences implements SharedPreferences {
    public final File file = new File(".");

    public XSharedPreferences(String packageName, String prefFileName) {
    }

    @Override
    public Map<String, ?> getAll() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public String getString(String key, String defValue) {
        return defValue;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        return defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        return defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        return defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }

    @Override
    public boolean contains(String key) {
        return false;
    }

    @Override
    public Editor edit() {
        return new EmptyEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    private static final class EmptyEditor implements Editor {
        @Override
        public Editor putString(String key, String value) { return this; }
        @Override
        public Editor putStringSet(String key, Set<String> values) { return this; }
        @Override
        public Editor putInt(String key, int value) { return this; }
        @Override
        public Editor putLong(String key, long value) { return this; }
        @Override
        public Editor putFloat(String key, float value) { return this; }
        @Override
        public Editor putBoolean(String key, boolean value) { return this; }
        @Override
        public Editor remove(String key) { return this; }
        @Override
        public Editor clear() { return this; }
        @Override
        public boolean commit() { return true; }
        @Override
        public void apply() { }
    }
}
