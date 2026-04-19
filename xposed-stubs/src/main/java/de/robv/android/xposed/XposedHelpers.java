package de.robv.android.xposed;

public final class XposedHelpers {
    private XposedHelpers() {}

    public static Object findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static Object findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static int getIntField(Object obj, String fieldName) {
        return 0;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }

    public static void setIntField(Object obj, String fieldName, int value) {
    }

    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError() {
            super();
        }

        public ClassNotFoundError(String message) {
            super(message);
        }
    }
}
