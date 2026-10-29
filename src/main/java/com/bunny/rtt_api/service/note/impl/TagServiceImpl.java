package com.bunny.rtt_api.service.note.impl;

import com.bunny.rtt_api.dto.request.TagCreateRequest;
import com.bunny.rtt_api.dto.response.TagResponse;
import com.bunny.rtt_api.entity.Tag;
import com.bunny.rtt_api.mapper.TagMapper;
import com.bunny.rtt_api.repository.TagRepository;
import com.bunny.rtt_api.service.note.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> listTags() {
        List<Tag> tags = tagRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        return tagMapper.toTagResponses(tags);
    }

    @Override
    @Transactional
    public TagResponse ensureTag(TagCreateRequest request) {
        String name = request.name().trim();

        Optional<Tag> existing = tagRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            return tagMapper.toTagResponse(existing.get());
        }

        try {
            Tag newTag = Tag.builder()
                    .name(name)
                    .build();
            Tag saved = tagRepository.saveAndFlush(newTag);
            return tagMapper.toTagResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent tag creation conflict for name: {}, reloading existing tag", name);
            Tag conflicted = tagRepository.findByNameIgnoreCase(name)
                    .orElseThrow(() -> ex);
            return tagMapper.toTagResponse(conflicted);
        }
    }
}
