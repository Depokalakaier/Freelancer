package com.example.freelancera.api;

import com.example.freelancera.models.clockify.ClockifyTimeEntry;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;

public interface ClockifyApi {
    @GET("workspaces/{workspaceId}/time-entries")
    Call<List<ClockifyTimeEntry>> getTimeEntries(
        @Path("workspaceId") String workspaceId,
        @Query("description") String description
    );

    @POST("workspaces/{workspaceId}/time-entries")
    Call<ClockifyTimeEntry> createTimeEntry(
        @Path("workspaceId") String workspaceId,
        @Body ClockifyTimeEntry timeEntry
    );

    @PUT("workspaces/{workspaceId}/time-entries/{timeEntryId}")
    Call<ClockifyTimeEntry> updateTimeEntry(
        @Path("workspaceId") String workspaceId,
        @Path("timeEntryId") String timeEntryId,
        @Body ClockifyTimeEntry timeEntry
    );

    @DELETE("workspaces/{workspaceId}/time-entries/{timeEntryId}")
    Call<Void> deleteTimeEntry(
        @Path("workspaceId") String workspaceId,
        @Path("timeEntryId") String timeEntryId
    );
} 