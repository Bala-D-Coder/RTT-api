package com.bunny.rtt_api.repository.specification;

import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.NoteBlock;
import com.bunny.rtt_api.entity.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class NoteSpecifications {

    private NoteSpecifications() {}

    public static Specification<Note> buildFilter(
            String q,
            String tag,
            String color,
            String scope,
            Boolean today,
            Instant startOfDay,
            Instant endOfDay,
            String source
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Scope filter (default: active)
            if ("trashed".equalsIgnoreCase(scope)) {
                predicates.add(cb.isNotNull(root.get("deletedAt")));
            } else if (!"all".equalsIgnoreCase(scope)) {
                // "active" or null
                predicates.add(cb.isNull(root.get("deletedAt")));
            }

            // 2. Color filter
            if (color != null && !color.isBlank()) {
                predicates.add(cb.equal(root.get("color"), color.trim()));
            }

            // 3. Today filter
            if (Boolean.TRUE.equals(today) && startOfDay != null && endOfDay != null) {
                predicates.add(cb.between(root.get("createdAt"), startOfDay, endOfDay));
            }

            // 4. Tag filter
            if (tag != null && !tag.isBlank()) {
                if (query != null) {
                    query.distinct(true);
                }
                Join<Note, Tag> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(cb.or(
                        cb.equal(cb.lower(tagJoin.get("name")), tag.trim().toLowerCase()),
                        cb.equal(tagJoin.get("id").as(String.class), tag.trim())
                ));
            }

            // 5. Source filter: source=dictation matches notes containing at least one dictated block
            if ("dictation".equalsIgnoreCase(source)) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<NoteBlock> blockRoot = subquery.from(NoteBlock.class);
                subquery.select(cb.literal(1));
                subquery.where(
                        cb.equal(blockRoot.get("note"), root),
                        cb.equal(blockRoot.get("source"), "dictation")
                );
                predicates.add(cb.exists(subquery));
            }

            // 6. Search query q (ILIKE over title + note_blocks text)
            if (q != null && !q.isBlank()) {
                String sanitized = q.trim()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                        .toLowerCase();
                String pattern = "%" + sanitized + "%";

                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, '\\');

                Subquery<Integer> textSubquery = query.subquery(Integer.class);
                Root<NoteBlock> textBlockRoot = textSubquery.from(NoteBlock.class);
                textSubquery.select(cb.literal(1));
                textSubquery.where(
                        cb.equal(textBlockRoot.get("note"), root),
                        cb.like(cb.lower(textBlockRoot.get("text")), pattern, '\\')
                );
                Predicate blockMatch = cb.exists(textSubquery);

                predicates.add(cb.or(titleMatch, blockMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
