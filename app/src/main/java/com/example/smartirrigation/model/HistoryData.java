package com.example.smartirrigation.model;

import java.util.List;

/**
 * GET /api/app/device/history → {"interval":"raw","points":[{...}]}
 */
public class HistoryData {
    public String interval;
    public List<Point> points;

    public static class Point {
        public String t;
        public Integer soil_pct;
        public Double temp;
        public Double humi;
        public Integer light_pct;
        public Integer water_pct;
        public Integer pump;
    }
}
