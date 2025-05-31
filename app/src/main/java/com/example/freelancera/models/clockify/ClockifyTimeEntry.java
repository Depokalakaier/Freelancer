package com.example.freelancera.models.clockify;

import com.google.gson.annotations.SerializedName;
import java.util.Date;

public class ClockifyTimeEntry {
    @SerializedName("id")
    private String id;

    @SerializedName("description")
    private String description;

    @SerializedName("timeInterval")
    private TimeInterval timeInterval;

    @SerializedName("projectId")
    private String projectId;

    @SerializedName("workspaceId")
    private String workspaceId;

    public static class TimeInterval {
        @SerializedName("start")
        private Date start;

        @SerializedName("end")
        private Date end;

        @SerializedName("duration")
        private String duration;

        public Date getStart() { return start; }
        public void setStart(Date start) { this.start = start; }

        public Date getEnd() { return end; }
        public void setEnd(Date end) { this.end = end; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TimeInterval getTimeInterval() { return timeInterval; }
    public void setTimeInterval(TimeInterval timeInterval) { this.timeInterval = timeInterval; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
} 