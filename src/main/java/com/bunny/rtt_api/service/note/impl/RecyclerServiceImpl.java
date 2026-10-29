package com.bunny.rtt_api.service.note.impl;

import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.service.note.RecyclerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecyclerServiceImpl implements RecyclerService {

    private final NoteRepository noteRepository;

    @Override
    @Transactional
    public int recycleOnce() {
        Instant now = Instant.now();
        List<Note> expired = noteRepository.findByPurgeAtLessThanEqual(now, PageRequest.of(0, 100));

        if (expired.isEmpty()) {
            return 0;
        }

        noteRepository.deleteAll(expired);
        log.info("Recycler sweep purged {} expired notes", expired.size());
        return expired.size();
    }

    @Scheduled(fixedDelayString = "${app.recycler.delay-ms:30000}", initialDelayString = "${app.recycler.initial-delay-ms:10000}")
    public void runScheduled() {
        recycleOnce();
    }
}
