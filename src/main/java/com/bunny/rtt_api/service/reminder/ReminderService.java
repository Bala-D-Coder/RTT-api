package com.bunny.rtt_api.service.reminder;

import com.bunny.rtt_api.dto.request.ReminderCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderPatchRequest;
import com.bunny.rtt_api.dto.request.ReminderQueryRequest;
import com.bunny.rtt_api.dto.request.ReminderSnoozeRequest;
import com.bunny.rtt_api.dto.response.PageResponse;
import com.bunny.rtt_api.dto.response.ReminderResponse;

import java.util.UUID;

public interface ReminderService {

    PageResponse<ReminderResponse> listReminders(ReminderQueryRequest query);

    ReminderResponse createReminder(ReminderCreateRequest request);

    ReminderResponse getReminder(UUID id);

    ReminderResponse patchReminder(UUID id, ReminderPatchRequest request);

    void deleteReminder(UUID id);

    ReminderResponse snoozeReminder(UUID id, ReminderSnoozeRequest request);

    ReminderResponse completeReminder(UUID id);
}
