package com.exteragram.messenger.backup;

import java.io.Serializable;

public final class PreferencesUtils {

    private PreferencesUtils() {
    }

    public static class BackupItem implements Serializable {

        public Class<?> clazz;
        public String key;

        public BackupItem(String key, Class<?> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            return key.equals(((BackupItem) other).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
