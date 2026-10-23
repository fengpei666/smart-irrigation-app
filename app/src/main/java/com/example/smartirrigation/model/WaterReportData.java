package com.example.smartirrigation.model;

import java.util.List;

/**
 * GET /api/app/water-report
 * {"total_pump_seconds":90,"total_times":6,"est_liters":1.5,
 *  "days":[{"date":"2026-10-23","pump_seconds":15,"times":1,"est_liters":0.25}]}
 */
public class WaterReportData {
    public int total_pump_seconds;
    public int total_times;
    public double est_liters;
    public List<Day> days;

    public static class Day {
        public String date;
        public int pump_seconds;
        public int times;
        public double est_liters;
    }
}
