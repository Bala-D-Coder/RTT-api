package com.bunny.rtt_api.dto.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsNotificationEvent(
        String type,
        String reminderId,
        String title,
        String noteId,
        Integer missed
) {
    public static WsNotificationEvent reminderDue(String reminderId, String title, String noteId) {
        return new WsNotificationEvent("reminder_due", reminderId, title, noteId, null);
    }

    public static WsNotificationEvent noteUpdated(String noteId) {
        return new WsNotificationEvent("note_updated", null, null, noteId, null);
    }

    public static WsNotificationEvent reconnected(int missed) {
        return new WsNotificationEvent("reconnected", null, null, null, missed);
    }
}
