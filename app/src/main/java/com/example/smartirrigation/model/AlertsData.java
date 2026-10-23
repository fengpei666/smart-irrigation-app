package com.example.smartirrigation.model;

import java.util.List;

/**
 * GET /api/app/alerts → {"unread_count","total","page","page_size","list":[...]}
 */
public class AlertsData {
    public int unread_count;
    public int total;
    public int page;
    public int page_size;
    public List<AlertItem> list;
}
