package com.bunny.rtt_api.mapper;

import com.bunny.rtt_api.dto.response.ReminderResponse;
import com.bunny.rtt_api.entity.Reminder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReminderMapper {

    @Mapping(target = "noteId", source = "note.id")
    @Mapping(target = "weekdays", expression = "java(mapWeekdays(reminder.getWeekdays()))")
    ReminderResponse toReminderResponse(Reminder reminder);

    List<ReminderResponse> toReminderResponses(List<Reminder> reminders);

    default List<Integer> mapWeekdays(Integer[] weekdays) {
        if (weekdays == null) return null;
        return Arrays.asList(weekdays);
    }
}
