package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.TestAuth;
import com.twomemory.app.entry.*;
import com.twomemory.app.export.ExportService;
import com.twomemory.app.media.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Two real PostgreSQL members, actual HTTP principals and actor-specific restore feeds. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters=false)
@Transactional
class PrivateKeepsakesTest {
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired JdbcTemplate db;
    @Autowired EntryService entries;
    @Autowired ChangeFeedService feed;
    @Autowired CommentService comments;
    @Autowired MediaService media;
    @Autowired ExportService export;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UUID author, partner, space;
    @BeforeEach void seed() {
        author=UUID.randomUUID(); partner=UUID.randomUUID(); space=UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        for (UUID user : List.of(author,partner)) {
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", user);
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space,user);
        }
    }
    EntryView create(EntryVisibility visibility) {
        return entries.createDraft(author,new CreateEntryCommand(author,space,EntryMode.PERSONAL,"secret-title",
                Instant.now().minusSeconds(5),"Asia/Shanghai", List.of(new BlockMutation(UUID.randomUUID(),
                BlockType.TEXT,0,author,"{\"text\":\"secret-body\"}",null,false)),UUID.randomUUID(),visibility));
    }
    @Test void privatePublishRestoresAuthorAndNeverAppearsToPartner() throws Exception {
        var draft=create(EntryVisibility.PRIVATE);
        var published=entries.publish(draft.id(),author,0).entry();
        assertThat(published.visibility()).isEqualTo(EntryVisibility.PRIVATE);
        assertThat(published.privateDraft()).isFalse();
        assertThat(feed.readChanges(author,space,0,200).changes()).singleElement().satisfies(row ->
                assertThat(row.payload()).contains("secret-body", "PRIVATE", "PUBLISHED"));
        var partnerPage=feed.readChanges(partner,space,0,1);
        assertThat(partnerPage.changes()).isEmpty();
        assertThat(partnerPage.nextSequence()).isEqualTo(feed.lastSequence(space));
        assertThat(partnerPage.hasMore()).isFalse();
        assertThatThrownBy(() -> entries.readEntry(draft.id(),partner)).isInstanceOf(EntryAccessDeniedException.class);
        assertThatThrownBy(() -> entries.applyChanges(draft.id(),partner,1,List.of())).isInstanceOf(EntryAccessDeniedException.class);
        assertThatThrownBy(() -> comments.addComment(draft.id(),partner,"guess",null)).isInstanceOf(EntryAccessDeniedException.class);
        assertThat(json.valueToTree(entries.readTimeline(partner,space,null,200)).path("entries").size()).isZero();
        assertThat(export.exportCouple(partner,space,null,null).toString()).doesNotContain("secret-body",draft.id().toString());
        assertThat(export.exportCouple(author,space,null,null).toString()).contains("secret-body");
        mvc.perform(get("/api/v1/entries/{id}",draft.id()).with(TestAuth.deviceSession(partner,space))).andExpect(status().isForbidden());
        // An older client's edit has no visibility field and must retain PRIVATE.
        entries.applyChanges(draft.id(),author,1,List.of());
        assertThat(entries.readEntry(draft.id(),author).visibility()).isEqualTo(EntryVisibility.PRIVATE);
        assertThat(feed.readChanges(author,space,0,200).changes()).hasSize(2);
    }
    @Test void hideSanitizesHistoricalFeedAndWithdrawsAlreadySyncedProjectionThenUnhideRestoresCurrent() throws Exception {
        var draft=create(EntryVisibility.SHARED);
        entries.publish(draft.id(),author,0);
        comments.addComment(draft.id(),partner,"old-shared-comment",null);
        long previous=feed.lastSequence(space);
        mvc.perform(put("/api/v1/entries/{id}/visibility",draft.id()).with(TestAuth.deviceSession(author,space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\",\"baseVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.rowVersion").value(2));
        var old=feed.readChanges(partner,space,0,200);
        String hiddenExport=export.exportCouple(partner,space,null,null).toString();
        assertThat(hiddenExport).doesNotContain("secret-body","secret-title","old-shared-comment",draft.id().toString());
        assertThat(export.exportCouple(author,space,null,null).toString()).contains("secret-body","old-shared-comment");
        assertThat(db.queryForObject("SELECT count(*) FROM entry_revision WHERE entry_id=?",Integer.class,draft.id())).isEqualTo(1);
        assertThat(old.changes()).allSatisfy(row -> {
            assertThat(row.operation()).isEqualTo("DELETE");
            assertThat(row.entityType()).isEqualTo("ENTRY");
            assertThat(row.payload()).contains("deleted").doesNotContain("secret", "old-shared-comment", "authorId", "PRIVATE");
        });
        assertThat(feed.readChanges(partner,space,previous,1).changes()).singleElement().satisfies(row ->
                assertThat(row.operation()).isEqualTo("DELETE"));
        assertThat(feed.readChanges(author,space,previous,200).changes()).singleElement().satisfies(row ->
                assertThat(row.payload()).contains("secret-body"));
        mvc.perform(put("/api/v1/entries/{id}/visibility",draft.id()).with(TestAuth.deviceSession(author,space))
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"SHARED\",\"baseVersion\":1}"))
                .andExpect(status().isConflict());
        long hidden=feed.lastSequence(space);
        entries.setVisibility(draft.id(),author,EntryVisibility.SHARED,2L);
        assertThat(feed.readChanges(partner,space,hidden,200).changes()).filteredOn(row -> row.entityType().equals("ENTRY")).singleElement().satisfies(row ->
                assertThat(row.payload()).contains("secret-body","SHARED"));
        assertThat(feed.readChanges(partner,space,hidden,200).changes()).filteredOn(row -> row.entityType().equals("COMMENT")).singleElement().satisfies(row ->
                assertThat(row.payload()).contains("old-shared-comment"));
        // Private history remains author-only even after sharing again.
        assertThat(feed.readChanges(partner,space,0,200).changes()).noneMatch(row -> row.payload().contains("PRIVATE"));
    }
    @Test void mediaCannotBeGuessedOrLaunderedAndVisibilityIsAuthorOnly() throws Exception {
        UUID asset=UUID.randomUUID();
        db.update("INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256) VALUES (?,?,?,'IMAGE','READY',?,'image/png',10,?)",
                asset,space,author,"private/"+asset,"a".repeat(64));
        var draft=create(EntryVisibility.PRIVATE);
        entries.applyChanges(draft.id(),author,0,List.of(new BlockMutation(UUID.randomUUID(),BlockType.IMAGE,1,author,"{}",asset,false)));
        entries.publish(draft.id(),author,1);
        assertThatThrownBy(() -> media.readAsset(partner,asset)).hasMessage("media is not shared");
        assertThatCode(() -> media.readAsset(author,asset)).doesNotThrowAnyException();
        mvc.perform(get("/api/v1/media/{id}/data",asset).with(TestAuth.deviceSession(partner,space))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/media/{id}/thumbnail",asset).with(TestAuth.deviceSession(partner,space))).andExpect(status().isForbidden());
        assertThatThrownBy(() -> entries.createDraft(partner,new CreateEntryCommand(partner,space,EntryMode.PERSONAL,"launder",
                Instant.now(),"UTC",List.of(new BlockMutation(UUID.randomUUID(),BlockType.IMAGE,0,partner,"{}",asset,false)),UUID.randomUUID())))
                .hasMessage("media is not shared");
        assertThatThrownBy(() -> entries.setVisibility(draft.id(),partner,EntryVisibility.SHARED,null)).isInstanceOf(EntryAccessDeniedException.class);
        entries.setVisibility(draft.id(),author,EntryVisibility.SHARED,null);
        assertThatCode(() -> media.readAsset(partner,asset)).doesNotThrowAnyException();
        entries.setVisibility(draft.id(),author,EntryVisibility.PRIVATE,null);
        assertThatThrownBy(() -> media.readAsset(partner,asset)).hasMessage("media is not shared");
    }
    @Test void legacyConstructorsDefaultSharedAndCollaborativeCannotHide() {
        var command=new CreateEntryCommand(author,space,EntryMode.COLLABORATIVE,"shared",Instant.now(),"UTC",List.of(),UUID.randomUUID());
        assertThat(command.visibility()).isEqualTo(EntryVisibility.SHARED);
        var view=entries.createDraft(author,command);
        assertThatThrownBy(() -> entries.setVisibility(view.id(),author,EntryVisibility.PRIVATE,null)).isInstanceOf(EntryValidationException.class);
        assertThat(new EntryView(view.id(),space,view.mode(),view.state(),author,0,0,"",0,"UTC",List.of()).visibility()).isEqualTo(EntryVisibility.SHARED);
    }

    @Test void filteredPagesAdvancePastInvisibleGapsWithoutSkippingVisibleRows() {
        var hidden=create(EntryVisibility.PRIVATE);
        entries.publish(hidden.id(),author,0); // sequence 1, author-only
        var first=create(EntryVisibility.SHARED);
        entries.publish(first.id(),author,0); // sequence 2
        entries.applyChanges(hidden.id(),author,1,List.of()); // sequence 3, author-only
        var second=create(EntryVisibility.SHARED);
        entries.publish(second.id(),author,0); // sequence 4
        entries.applyChanges(hidden.id(),author,2,List.of()); // sequence 5, author-only
        var page1=feed.readChanges(partner,space,0,1);
        assertThat(page1.changes()).singleElement().satisfies(row -> assertThat(row.entityId()).isEqualTo(first.id()));
        assertThat(page1.nextSequence()).isEqualTo(2);
        assertThat(page1.hasMore()).isTrue();
        var page2=feed.readChanges(partner,space,page1.nextSequence(),1);
        assertThat(page2.changes()).singleElement().satisfies(row -> assertThat(row.entityId()).isEqualTo(second.id()));
        assertThat(page2.nextSequence()).isEqualTo(5);
        assertThat(page2.hasMore()).isFalse();
        assertThat(feed.readChanges(author,space,0,200).changes()).hasSize(5);
        assertThat(feed.readChanges(partner,space,5,1).changes()).isEmpty();
    }

    @Test void typedCreatePayloadCarriesPrivateVisibilityThroughPublishAndRestore() throws Exception {
        UUID id=UUID.randomUUID();
        String payload=json.writeValueAsString(java.util.Map.of("entryId",id,"authorId",author,
                "occurredAt",Instant.now().toString(),"occurredTimezone","UTC","visibility","PRIVATE",
                "blocks",List.of(java.util.Map.of("blockId",UUID.randomUUID(),"type","TEXT","orderKey",0,"text","typed-secret"))));
        sync("CREATE_PERSONAL_ENTRY",payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("PRIVATE")));
        sync("PUBLISH_ENTRY",json.writeValueAsString(java.util.Map.of("entryId",id,"baseVersion",0)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sync/changes").param("coupleId",space.toString()).with(TestAuth.deviceSession(partner,space)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes.length()").value(0));
        mvc.perform(get("/api/v1/sync/changes").param("coupleId",space.toString()).with(TestAuth.deviceSession(author,space)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes[0].payload").value(org.hamcrest.Matchers.containsString("typed-secret")));
        sync("CREATE_PERSONAL_ENTRY",payload.replace("PRIVATE","INVALID")).andExpect(status().isBadRequest());
        sync("CREATE_SHARED_ENTRY",payload).andExpect(status().isBadRequest());
    }
    org.springframework.test.web.servlet.ResultActions sync(String type,String payload) throws Exception {
        var request=new SyncOperationRequest(UUID.randomUUID(),space,SyncPayloadHasher.hash(type,payload),type,payload);
        return mvc.perform(post("/api/v1/sync/operations").with(TestAuth.deviceSession(author,space))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }
}
