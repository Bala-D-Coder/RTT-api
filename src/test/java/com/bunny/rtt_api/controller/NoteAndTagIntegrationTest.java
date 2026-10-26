package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.bootstrap.SeedAccountsRunner;
import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.request.BlockAppendRequest;
import com.bunny.rtt_api.dto.request.BlockUpdateRequest;
import com.bunny.rtt_api.dto.request.NoteCreateRequest;
import com.bunny.rtt_api.dto.request.NotePatchRequest;
import com.bunny.rtt_api.dto.request.NoteTagsUpdateRequest;
import com.bunny.rtt_api.dto.request.TagCreateRequest;
import com.bunny.rtt_api.dto.response.NoteResponse;
import com.bunny.rtt_api.dto.response.TagResponse;
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.repository.NoteBlockRepository;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.TagRepository;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.service.note.RecyclerService;
import com.bunny.rtt_api.security.TokenService;
import com.bunny.rtt_api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoteAndTagIntegrationTest extends IntegrationTestBase {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private NoteRepository noteRepository;

        @Autowired
        private NoteBlockRepository noteBlockRepository;

        @Autowired
        private TagRepository tagRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private SeedAccountsRunner seedAccountsRunner;

        @Autowired
        private AppProps appProps;

        @Autowired
        private RecyclerService recyclerService;

        @Autowired
        private TokenService tokenService;

        private String adminToken;
        private String demoToken;

        @BeforeEach
        void setUp() throws Exception {
                seedAccountsRunner.seedAccounts();

                noteBlockRepository.deleteAll();
                noteRepository.deleteAll();
                tagRepository.deleteAll();

                com.bunny.rtt_api.entity.User admin = userRepository.findByEmail(appProps.admin().email())
                                .orElseThrow();
                adminToken = tokenService.generateAccessToken(admin.getId().toString(), admin.getEmail(),
                                admin.getRole());

                com.bunny.rtt_api.entity.User demo = userRepository.findByEmail(appProps.demo().email()).orElseThrow();
                demoToken = tokenService.generateAccessToken(demo.getId().toString(), demo.getEmail(), demo.getRole());
        }

        @Test
        void testCreateNoteWithBlocksInSingleTx() throws Exception {
                NoteCreateRequest request = new NoteCreateRequest(
                                "Voice Note Title",
                                "coral",
                                List.of(
                                                new NoteCreateRequest.BlockInput("First typed paragraph", "typed",
                                                                null),
                                                new NoteCreateRequest.BlockInput("Second transcribed block",
                                                                "dictation", null)));

                MvcResult result = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isNotEmpty())
                                .andExpect(jsonPath("$.title").value("Voice Note Title"))
                                .andExpect(jsonPath("$.color").value("coral"))
                                .andExpect(jsonPath("$.pinned").value(false))
                                .andExpect(jsonPath("$.body.length()").value(2))
                                .andExpect(jsonPath("$.body[0].text").value("First typed paragraph"))
                                .andExpect(jsonPath("$.body[0].source").value("typed"))
                                .andExpect(jsonPath("$.body[1].text").value("Second transcribed block"))
                                .andExpect(jsonPath("$.body[1].source").value("dictation"))
                                .andReturn();

                NoteResponse note = objectMapper.readValue(result.getResponse().getContentAsString(),
                                NoteResponse.class);
                assertThat(note.id()).isNotNull();
        }

        @Test
        void testAppendUpdateDeleteBlockQ1Diff() throws Exception {
                // 1. Create note with 1 block
                NoteCreateRequest createReq = new NoteCreateRequest("Block Diff Note", "sand", List.of(
                                new NoteCreateRequest.BlockInput("Original block", "typed", null)));
                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                NoteResponse note = objectMapper.readValue(createRes.getResponse().getContentAsString(),
                                NoteResponse.class);
                UUID noteId = note.id();
                UUID firstBlockId = note.body().get(0).id();

                // 2. Append block: POST /notes/{id}/blocks (max position + 1)
                BlockAppendRequest appendReq = new BlockAppendRequest("Appended block", "dictation", null);
                MvcResult appendRes = mockMvc.perform(post("/api/v1/notes/" + noteId + "/blocks")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(appendReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.body.length()").value(2))
                                .andExpect(jsonPath("$.body[1].text").value("Appended block"))
                                .andExpect(jsonPath("$.body[1].source").value("dictation"))
                                .andReturn();

                NoteResponse noteWithAppend = objectMapper.readValue(appendRes.getResponse().getContentAsString(),
                                NoteResponse.class);
                UUID secondBlockId = noteWithAppend.body().get(1).id();

                // 3. Update block: PUT /notes/{id}/blocks/{blockId} (text only)
                BlockUpdateRequest updateReq = new BlockUpdateRequest("Updated original block text");
                mockMvc.perform(put("/api/v1/notes/" + noteId + "/blocks/" + firstBlockId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.body[0].text").value("Updated original block text"));

                // 4. Delete block: DELETE /notes/{id}/blocks/{blockId}
                mockMvc.perform(delete("/api/v1/notes/" + noteId + "/blocks/" + secondBlockId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.body.length()").value(1))
                                .andExpect(jsonPath("$.body[0].id").value(firstBlockId.toString()));

                // 5. Deleting non-existent block returns 404 block_not_found
                mockMvc.perform(delete("/api/v1/notes/" + noteId + "/blocks/" + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error.code").value("block_not_found"));
        }

        @Test
        void testPatchScalarsOnly_RejectsBodyUnknownProperties() throws Exception {
                NoteCreateRequest createReq = new NoteCreateRequest("Original Title", "sand", null);
                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                NoteResponse note = objectMapper.readValue(createRes.getResponse().getContentAsString(),
                                NoteResponse.class);

                // Valid patch: title, color, pinned
                NotePatchRequest patchReq = new NotePatchRequest("Updated Title", "violet", true, null, null);
                mockMvc.perform(patch("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patchReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Updated Title"))
                                .andExpect(jsonPath("$.color").value("violet"))
                                .andExpect(jsonPath("$.pinned").value(true));

                // Invalid patch: attempting to pass whole 'body' array fails with 400 Bad
                // Request
                String rawBodyWithBodyField = """
                                {
                                  "title": "Hacked Title",
                                  "body": [{ "text": "should fail" }]
                                }
                                """;
                mockMvc.perform(patch("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawBodyWithBodyField))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error.code").value("validation_error"));
        }

        @Test
        void testTogglePin() throws Exception {
                NoteCreateRequest createReq = new NoteCreateRequest("Pin Note", "sand", null);
                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn();

                NoteResponse note = objectMapper.readValue(createRes.getResponse().getContentAsString(),
                                NoteResponse.class);
                assertThat(note.pinned()).isFalse();

                // 1st toggle: false -> true
                mockMvc.perform(post("/api/v1/notes/" + note.id() + "/pin")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.pinned").value(true));

                // 2nd toggle: true -> false
                mockMvc.perform(post("/api/v1/notes/" + note.id() + "/pin")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        void testTagEnsureIdempotencyAndAssociation() throws Exception {
                // 1. POST /api/v1/tags creates new tag
                TagCreateRequest req1 = new TagCreateRequest("Architecture");
                MvcResult res1 = mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Architecture"))
                                .andReturn();

                TagResponse tag1 = objectMapper.readValue(res1.getResponse().getContentAsString(), TagResponse.class);

                // 2. Case-insensitive ensure: "ARCHITECTURE" returns existing tag
                TagCreateRequest req2 = new TagCreateRequest("ARCHITECTURE");
                MvcResult res2 = mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(tag1.id().toString()))
                                .andReturn();

                // 3. GET /api/v1/tags
                mockMvc.perform(get("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].name").value("Architecture"));

                // 4. PUT /api/v1/notes/{id}/tags associates tag with note
                NoteCreateRequest createNote = new NoteCreateRequest("Tagged Note", "sand", null);
                MvcResult noteRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createNote)))
                                .andExpect(status().isCreated())
                                .andReturn();
                NoteResponse note = objectMapper.readValue(noteRes.getResponse().getContentAsString(),
                                NoteResponse.class);

                NoteTagsUpdateRequest tagUpdate = new NoteTagsUpdateRequest(List.of(tag1.id()), null);
                mockMvc.perform(put("/api/v1/notes/" + note.id() + "/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(tagUpdate)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.tags.length()").value(1))
                                .andExpect(jsonPath("$.tags[0].id").value(tag1.id().toString()));
        }

        @Test
        void testTrashRestoreAndPurge409() throws Exception {
                NoteCreateRequest createReq = new NoteCreateRequest("Trash Life Note", "sand", null);
                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn();
                NoteResponse note = objectMapper.readValue(createRes.getResponse().getContentAsString(),
                                NoteResponse.class);

                // 1. Purge live note returns 409 note_not_in_bin
                mockMvc.perform(delete("/api/v1/notes/" + note.id() + "/purge")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error.code").value("note_not_in_bin"));

                // 2. Soft delete: DELETE /notes/{id}
                MvcResult trashRes = mockMvc.perform(delete("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deletedAt").isNotEmpty())
                                .andExpect(jsonPath("$.purgeAt").isNotEmpty())
                                .andReturn();

                NoteResponse trashedNote = objectMapper.readValue(trashRes.getResponse().getContentAsString(),
                                NoteResponse.class);
                Instant firstDeletedAt = trashedNote.deletedAt();
                Instant firstPurgeAt = trashedNote.purgeAt();

                // 3. Double-trash idempotency: purgeAt must not jump forward
                MvcResult trashRes2 = mockMvc.perform(delete("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andReturn();
                NoteResponse trashedNote2 = objectMapper.readValue(trashRes2.getResponse().getContentAsString(),
                                NoteResponse.class);
                assertThat(trashedNote2.deletedAt().truncatedTo(ChronoUnit.MILLIS))
                                .isEqualTo(firstDeletedAt.truncatedTo(ChronoUnit.MILLIS));
                assertThat(trashedNote2.purgeAt().truncatedTo(ChronoUnit.MILLIS))
                                .isEqualTo(firstPurgeAt.truncatedTo(ChronoUnit.MILLIS));

                // 4. Default GET /notes excludes trashed notes
                mockMvc.perform(get("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(0));

                // 5. GET /notes?scope=trashed includes it
                mockMvc.perform(get("/api/v1/notes?scope=trashed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].id").value(note.id().toString()));

                // 6. Restore note: clears deletedAt and purgeAt
                mockMvc.perform(post("/api/v1/notes/" + note.id() + "/restore")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deletedAt").isEmpty())
                                .andExpect(jsonPath("$.purgeAt").isEmpty());

                // 7. Verify it is visible again under active scope
                mockMvc.perform(get("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1));

                // 8. Soft delete then purge permanently
                mockMvc.perform(delete("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk());

                mockMvc.perform(delete("/api/v1/notes/" + note.id() + "/purge")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isNoContent());

                // 9. Verifying purged note is gone
                mockMvc.perform(get("/api/v1/notes/" + note.id())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error.code").value("note_not_found"));
        }

        @Test
        void testEmptyBin() throws Exception {
                // Create 2 notes and trash both
                NoteCreateRequest req1 = new NoteCreateRequest("Trash 1", "sand", null);
                NoteCreateRequest req2 = new NoteCreateRequest("Trash 2", "coral", null);

                MvcResult r1 = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1))).andReturn();
                MvcResult r2 = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2))).andReturn();

                UUID id1 = objectMapper.readValue(r1.getResponse().getContentAsString(), NoteResponse.class).id();
                UUID id2 = objectMapper.readValue(r2.getResponse().getContentAsString(), NoteResponse.class).id();

                mockMvc.perform(delete("/api/v1/notes/" + id1).header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + adminToken));
                mockMvc.perform(delete("/api/v1/notes/" + id2).header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + adminToken));

                // Empty bin: DELETE /api/v1/notes?scope=trashed
                mockMvc.perform(delete("/api/v1/notes?scope=trashed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.purged").value(2));

                // Verify bin is empty
                mockMvc.perform(get("/api/v1/notes?scope=trashed")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(0));
        }

        @Test
        void testExportFormats() throws Exception {
                NoteCreateRequest request = new NoteCreateRequest(
                                "Sprint Retrospective",
                                "aqua",
                                List.of(
                                                new NoteCreateRequest.BlockInput("Discussed Q3 deliverables.", "typed",
                                                                null),
                                                new NoteCreateRequest.BlockInput("Identified audio latency issues.",
                                                                "dictation", null)));

                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                UUID noteId = objectMapper.readValue(createRes.getResponse().getContentAsString(), NoteResponse.class)
                                .id();

                // 1. Export Markdown
                mockMvc.perform(get("/api/v1/notes/" + noteId + "/export?format=md")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                                                org.hamcrest.Matchers.containsString("text/markdown")))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                org.hamcrest.Matchers.containsString("Sprint Retrospective.md")));

                // 2. Export TXT
                mockMvc.perform(get("/api/v1/notes/" + noteId + "/export?format=txt")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                                                org.hamcrest.Matchers.containsString("text/plain")))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                org.hamcrest.Matchers.containsString("Sprint Retrospective.txt")));

                // 3. Export JSON
                mockMvc.perform(get("/api/v1/notes/" + noteId + "/export?format=json")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                org.hamcrest.Matchers.containsString("Sprint Retrospective.json")))
                                .andExpect(jsonPath("$.id").value(noteId.toString()))
                                .andExpect(jsonPath("$.title").value("Sprint Retrospective"));

                // 4. Invalid format returns 400 validation_error
                mockMvc.perform(get("/api/v1/notes/" + noteId + "/export?format=pdf")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error.code").value("validation_error"));
        }

        @Test
        void testSearchAndSourceFilterB18() throws Exception {
                // Note A: typed only
                NoteCreateRequest noteA = new NoteCreateRequest("Grocery List", "sand", List.of(
                                new NoteCreateRequest.BlockInput("Buy almond milk and bread", "typed", null)));
                // Note B: contains dictation
                NoteCreateRequest noteB = new NoteCreateRequest("Voice Memo", "mint", List.of(
                                new NoteCreateRequest.BlockInput("Discussed quarterly roadmap", "dictation", null)));

                mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noteA)));

                mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noteB)));

                // Search q=milk matches Note A
                mockMvc.perform(get("/api/v1/notes?q=milk")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].title").value("Grocery List"));

                // Search q=roadmap matches Note B
                mockMvc.perform(get("/api/v1/notes?q=roadmap")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].title").value("Voice Memo"));

                // B18 Filter source=dictation matches only Note B and returns the whole note
                mockMvc.perform(get("/api/v1/notes?source=dictation")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].title").value("Voice Memo"));

                // Review G-8 / B18 rule: source=manual is not a valid value and must be
                // rejected with 400 validation error
                mockMvc.perform(get("/api/v1/notes?source=manual")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error.code").value("validation_error"));
        }

        @Test
        void testSingleWorkspaceModelB21() throws Exception {
                // Admin creates note
                NoteCreateRequest request = new NoteCreateRequest("Admin Shared Note", "sand", List.of(
                                new NoteCreateRequest.BlockInput("Created by admin", "typed", null)));

                MvcResult createRes = mockMvc.perform(post("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                UUID noteId = objectMapper.readValue(createRes.getResponse().getContentAsString(), NoteResponse.class)
                                .id();

                // Demo user reads note list -> sees the admin note (B21: shared single
                // workspace)
                mockMvc.perform(get("/api/v1/notes")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].title").value("Admin Shared Note"));

                // Demo user updates note created by admin
                NotePatchRequest patchReq = new NotePatchRequest("Demo Updated Admin Note", "lime", null, null, null);
                mockMvc.perform(patch("/api/v1/notes/" + noteId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patchReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Demo Updated Admin Note"))
                                .andExpect(jsonPath("$.color").value("lime"));

                // Admin confirms change
                mockMvc.perform(get("/api/v1/notes/" + noteId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Demo Updated Admin Note"));
        }

        @Test
        void testRecyclerSweep() throws Exception {
                // Create note and soft delete with purgeAt set 1 hour in the past
                Note note = Note.builder()
                                .title("Expired Trashed Note")
                                .createdBy("admin@apex.in")
                                .color("sand")
                                .deletedAt(Instant.now().minus(8, ChronoUnit.DAYS))
                                .purgeAt(Instant.now().minus(1, ChronoUnit.HOURS))
                                .build();
                noteRepository.save(note);

                // Run recycler sweep synchronously
                int purged = recyclerService.recycleOnce();
                assertThat(purged).isGreaterThanOrEqualTo(1);

                // Verify note is gone from DB
                assertThat(noteRepository.findById(note.getId())).isEmpty();
        }
}
