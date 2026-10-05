package com.btc.claimservice.client;

/**
 * A notification for one user (audience USER, recipientId) or for all administrators (audience ADMINS).
 * Contains claim identifiers and amounts only: never tokens, passwords or other secrets.
 */
public record NotificationMessage(String audience, Long recipientId, Long actorId, String type, String title,
                                  String message, String link) {

    public static NotificationMessage toUser(Long recipientId, Long actorId, String type, String title, String message,
                                             String link) {
        return new NotificationMessage("USER", recipientId, actorId, type, title, truncate(message), link);
    }

    public static NotificationMessage toAdmins(Long actorId, String type, String title, String message, String link) {
        return new NotificationMessage("ADMINS", null, actorId, type, title, truncate(message), link);
    }

    private static String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 499) + "…";
    }
}
