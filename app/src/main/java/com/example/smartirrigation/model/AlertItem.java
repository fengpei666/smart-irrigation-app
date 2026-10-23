package com.example.smartirrigation.model;

/**
 * 报警项：{"id","type","level","title","message","is_read","status","occurrences","created_at"}
 */
public class AlertItem {
    public long id;
    public String type;
    public String level;
    public String title;
    public String message;
    public int is_read;
    public String status;     // open / handled / ignored
    public int occurrences;
    public String created_at;

    public boolean isRead() {
        return is_read == 1;
    }
}
