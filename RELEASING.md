# Releasing JMemoryBuddy

The version is derived from the latest reachable git tag (`X.Y.Z`, no prefix):

- HEAD exactly on tag `X.Y.Z`, clean tree → version `X.Y.Z`
- commits after the tag, or a dirty tree → version `X.Y.(Z+1)-SNAPSHOT`
- no tag → `0.5.7-SNAPSHOT` (fallback)

The derived version is printed at the start of every build.

## Releasing a version

```bash
./tagRelease.sh jmemorybuddy X.Y.Z
```

The first argument is the project name, guarding against running this in the wrong repo.
The script only tags: it verifies a clean tree on an up-to-date `master` and a dated
`### X.Y.Z` CHANGELOG entry, then tags and pushes `X.Y.Z`. The tag push triggers
`.github/workflows/release.yml`, which builds and publishes via the scripts:

| Script | Registry | Versions |
|---|---|---|
| `publishSandecArtifactory.sh` | Sandec Artifactory | snapshots (every `master` push, via CI) and releases |
| `publishMavenCentral.sh` | Maven Central (Sonatype Central Portal) | releases only (refuses snapshots) |

Maven Central publishing is `AUTOMATIC` (see `build.gradle`): the tag-triggered deployment
is uploaded and released to Maven Central without a manual portal step — the tag is the gate,
so don't tag until the CHANGELOG and version are right (releases can't be unpublished). There
is no version bump and no bump-back — after the release, builds become `X.Y.(Z+1)-SNAPSHOT`.

## Required repository secrets

- `SANDEC_ARTIFACTORY_USERNAME` / `SANDEC_ARTIFACTORY_PASSWORD`
- `SANDEC_SIGNING_KEY_ID` / `SANDEC_SIGNING_SECRET_KEY` / `SANDEC_SIGNING_PASSWORD` — GPG
- `SANDEC_SONATYPE_AUTH_TOKEN` — Sonatype Central Portal token

## Note on the first tag-derived release

The latest existing tag is `0.5.5`, while `0.5.6` was published manually (untagged) — so until
a new tag exists, builds derive `0.5.6-SNAPSHOT`. The next release should be `0.5.7`
(`./tagRelease.sh jmemorybuddy 0.5.7`), which realigns the tags with the published versions.
