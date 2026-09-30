package com.exteragram.messenger.badges.source;

import androidx.annotation.NonNull;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.api.model.ProfileStatus;

import java.util.Objects;

public final class BadgeInfo {

    private final BadgeDTO badge;
    private final ProfileStatus status;
    private final boolean canChangeBadge;

    public BadgeInfo(BadgeDTO badge, ProfileStatus status, boolean canChangeBadge) {
        this.badge = badge;
        this.status = status;
        this.canChangeBadge = canChangeBadge;
    }

    public BadgeDTO getBadge() {
        return badge;
    }

    public ProfileStatus getStatus() {
        return status;
    }

    public boolean getCanChangeBadge() {
        return canChangeBadge;
    }

    public BadgeInfo copy(BadgeDTO badge, ProfileStatus status, boolean canChangeBadge) {
        return new BadgeInfo(badge, status, canChangeBadge);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BadgeInfo)) {
            return false;
        }
        BadgeInfo info = (BadgeInfo) other;
        return canChangeBadge == info.canChangeBadge
                && Objects.equals(badge, info.badge)
                && status == info.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(badge, status, canChangeBadge);
    }

    @NonNull
    @Override
    public String toString() {
        return "BadgeInfo(badge=" + badge + ", status=" + status
                + ", canChangeBadge=" + canChangeBadge + ")";
    }
}
