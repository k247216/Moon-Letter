# Cloud activation and deletion boundaries

User confirms successful real OSS probe. Existing backend data is test-only and does not need migration. This permits omitting test-data migration, not silently dropping active databases/volumes; do not reset unless needed for the explicitly scoped workflow. Current cloud staging remains localhost-only pending domain/HTTPS readiness.

## HTTPS continuation

compose.https.yaml overlays compose.private.yaml, retaining project name and database volume. Explicitly passes the domain into Caddy; old generic compose.yaml did not pass that variable and should not be used for this staging transition. Caddy uses the same Docker network to proxy app:8080, persists certificate state, and does not expose Actuator routes publicly. Domain resolution, filing requirements, security group TCP80/443 and actual certificate issuance remain unverified. Do not start this overlay without a real domain.

## Application deletion (required, not implemented)

Read-only inspection found no complete entry/album/photo delete UI and server flow. Include deletion in the next data-sync implementation rather than claiming existing support.

- Entry deletion: authorize on server, confirm effect on both devices, use tombstone/change feed rather than only removing a Room row. Shared entries require explicit handling of both authors' contributions; creator deletion of the whole shared page must warn clearly, while each author may remove only their own perspective. Test unauthorized cross-space access and partner contribution ownership.
- Albums remain independent from diary entries. Deleting an album must state whether photos are removed from that album or deleted from the cloud. Deleting a diary entry must not delete independently saved album photographs.
- Photos: distinguish removing an album reference from deleting an unreferenced media object; media with remaining authorized references must not be removed. Asset garbage collection cannot blindly delete objects by filename/prefix.
- Cache clearing: device-only, never interpreted as cloud deletion; downloaded user copies in MediaStore are not silently removed.
- Records pending upload: deletion must also cancel or supersede outgoing operations so an offline queue cannot resurrect deleted data.
- Backups/version history have retention distinct from user-visible deletion. Do not claim immediate erasure of all historical copies. App RAM identity currently lacks DeleteObject; retain that restriction until authorized cleanup with reference checks is implemented.

Next deliverables: real HTTPS mobile endpoint, independent album API/change feed/upload/download, deletion/tombstone propagation and regression tests, scheduled backups and restore rehearsal. Background push responsiveness is separate from foreground polling and cloud hosting.
