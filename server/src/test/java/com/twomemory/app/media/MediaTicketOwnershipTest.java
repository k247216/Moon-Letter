package com.twomemory.app.media;

import com.twomemory.app.auth.SpaceAccessPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class MediaTicketOwnershipTest {
    @Test void anotherOwnerCannotReuseSharedOperationToGetUploadAuthority() {
        var jdbc=mock(JdbcTemplate.class); var store=mock(ObjectStorage.class);
        var space=UUID.randomUUID(); var owner=UUID.randomUUID(); var other=UUID.randomUUID();
        var asset=new MediaAssetView(UUID.randomUUID(),space,owner,MediaKind.IMAGE,MediaStatus.READY,
            "original", "image/png", 3, 1, 1, null, "a".repeat(64), null);
        when(jdbc.query(anyString(),any(RowMapper.class),any(UUID.class))).thenReturn(List.of(asset));
        var service=new MediaService(jdbc,mock(SpaceAccessPolicy.class),store);
        var command=new CreateMediaCommand(other,space,MediaKind.IMAGE,"image/png",3,1,1,null,"a".repeat(64),UUID.randomUUID());
        assertThatThrownBy(()->service.createUpload(command)).isInstanceOf(MediaAccessDeniedException.class);
        verifyNoInteractions(store);
    }
}
