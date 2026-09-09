package com.gamemasterx.server.adventure.service;

import com.gamemasterx.server.adventure.AdventureImportConflictException;
import com.gamemasterx.server.adventure.AdventurePackageImportException;
import com.gamemasterx.server.adventure.AdventurePackageValidator;
import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.AdventureDto;
import com.gamemasterx.server.adventure.model.Adventure.Status;
import com.gamemasterx.server.adventure.repository.AdventureRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import tools.jackson.core.JacksonException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Application service for importing local adventure packages.
 *
 * <p>A local adventure package is a ZIP archive whose manifest is a JSON document
 * (by default the entry {@code adventure.json}) that mirrors the {@link Adventure}
 * aggregate. The package may also carry referenced asset files that are staged
 * alongside the manifest. Import proceeds in stages, each of which can reject the
 * package before any mutation happens:</p>
 * <ol>
 *   <li><b>Size checks</b> — the archive, any single entry, and the total number
 *       of entries are bounded so oversized input is rejected up front.</li>
 *   <li><b>Safe extraction</b> — every entry is resolved against the staging
 *       directory and rejected if it would escape it, defeating path-traversal
 *       ({@code ../}, absolute paths, drive letters, etc.).</li>
 *   <li><b>Structure &amp; identifier validation</b> — the manifest is parsed and
 *       validated by {@link AdventurePackageValidator} (required fields,
 *       identifier format, within-package uniqueness and referential integrity).</li>
 *   <li><b>Collision &amp; overwrite checks</b> — importing an adventure whose
 *       {@code id} already exists is rejected with a {@link
 *       AdventureImportConflictException} unless the caller explicitly opts in to
 *       an overwrite, so an import can never silently clobber existing content.</li>
 * </ol>
 */
@Service
public class AdventureImporter {

    private static final Logger log = LoggerFactory.getLogger(AdventureImporter.class);

    /** Default manifest entry name inside the package archive. */
    public static final String DEFAULT_MANIFEST_NAME = "adventure.json";

    private final AdventureRepository adventureRepository;
    private final ObjectMapper objectMapper;

    private final long maxArchiveBytes;
    private final long maxEntryBytes;
    private final long maxEntries;
    private final Path stagingRoot;

    public AdventureImporter(
            AdventureRepository adventureRepository,
            ObjectMapper objectMapper,
            @Value("${game.master.x.adventure.import.max-archive-bytes:20971520}") long maxArchiveBytes,
            @Value("${game.master.x.adventure.import.max-entry-bytes:5242880}") long maxEntryBytes,
            @Value("${game.master.x.adventure.import.max-entries:2000}") long maxEntries,
            @Value("${game.master.x.adventure.import.staging-dir:./tmp/adventure-imports}") Path stagingRoot) {
        this.adventureRepository = adventureRepository;
        this.objectMapper = objectMapper;
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxEntryBytes = maxEntryBytes;
        this.maxEntries = maxEntries;
        this.stagingRoot = stagingRoot;
    }

    /**
     * Imports a local adventure package from the given archive file.
     *
     * @param packagePath path to the ZIP package archive on disk
     * @param overwrite   when {@code true}, an existing adventure with the same
     *                    {@code id} is replaced instead of being rejected; when
     *                    {@code false} (the default) a collision is a conflict
     * @return the imported adventure, projected into the API representation
     * @throws AdventurePackageImportException         on structural, identifier,
     *                                                 size, path or parse failures
     * @throws AdventureImportConflictException        when {@code overwrite} is
     *                                                 {@code false} and the
     *                                                 identifier already exists
     */
    public AdventureDto importAdventure(Path packagePath, boolean overwrite) {
        if (packagePath == null || packagePath.toString().isBlank()) {
            throw new AdventurePackageImportException("package", "No package file was supplied for import");
        }
        if (!Files.isRegularFile(packagePath)) {
            throw new AdventurePackageImportException(
                    "package", "Import package is not a file: " + packagePath);
        }

        long fileSize;
        try {
            fileSize = Files.size(packagePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to stat import package: " + packagePath, e);
        }

        if (fileSize > maxArchiveBytes) {
            throw new AdventurePackageImportException(
                    "package",
                    "Import package is too large (" + fileSize + " bytes; limit is " + maxArchiveBytes
                            + " bytes)");
        }

        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory(stagingRoot, "import-");
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create staging directory: " + stagingRoot, e);
        }

        AtomicLong totalBytes = new AtomicLong();
        try {
            extractPackage(packagePath, stagingDir, totalBytes);

            if (totalBytes.get() > maxArchiveBytes) {
                throw new AdventurePackageImportException(
                        "package",
                        "Extracted package exceeds the allowed size (" + totalBytes.get()
                                + " bytes; limit is " + maxArchiveBytes + " bytes)");
            }

            Path manifest = resolveManifest(stagingDir);
            Adventure adventure = parseManifest(manifest);
            AdventurePackageValidator.validate(adventure);

            return persist(adventure, overwrite);
        } catch (IOException e) {
            throw new UncheckedIOException("Package extraction failed: " + e.getMessage(), e);
        } finally {
            deleteRecursivelyQuietly(stagingDir);
        }
    }

    private void extractPackage(Path packagePath, Path stagingDir, AtomicLong totalBytes)
            throws IOException {
        long entryCount = 0;
        long cumulativeBytes = 0;
        try (ZipFile zipFile = new ZipFile(packagePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new AdventurePackageImportException(
                            "package",
                            "Package contains too many entries (" + entryCount
                                    + "; limit is " + maxEntries + ")");
                }
                Path target = resolveStagedEntry(stagingDir, entry.getName());
                Files.createDirectories(target.getParent());

                long written = copyEntry(zipFile, entry, target);
                cumulativeBytes += written;
                totalBytes.addAndGet(written);
            }
        }
    }

    /**
     * Resolves a package entry name to a path inside {@code root}, rejecting any
     * entry that would escape the staging directory.
     */
    private Path resolveStagedEntry(Path root, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new AdventurePackageImportException("package", "Package contains an empty entry name");
        }
        Path candidate = root.resolve(entryName).normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.equals(normalizedRoot)
                && !normalizedCandidate.startsWith(normalizedRoot)) {
            throw new AdventurePackageImportException(
                    entryName,
                    "Unsafe path in package entry that escapes the import root: " + entryName);
        }
        return candidate;
    }

    private long copyEntry(ZipFile zipFile, ZipEntry entry, Path target)
            throws IOException {
        byte[] bytes;
        try (InputStream in = zipFile.getInputStream(entry)) {
            bytes = StreamUtils.copyToByteArray(in);
        }
        long written = bytes.length;
        if (written > maxEntryBytes) {
            throw new AdventurePackageImportException(
                    entry.getName(),
                    "Package entry exceeds the allowed size (" + written
                            + " bytes; limit is " + maxEntryBytes + " bytes): " + entry.getName());
        }
        Files.write(target, bytes);
        return written;
    }

    private Path resolveManifest(Path stagingDir) {
        Path manifest = stagingDir.resolve(DEFAULT_MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) {
            throw new AdventurePackageImportException(
                    DEFAULT_MANIFEST_NAME,
                    "Package manifest '" + DEFAULT_MANIFEST_NAME + "' was not found at the package root");
        }
        return manifest;
    }

    private Adventure parseManifest(Path manifest) {
        try {
            Adventure adventure = objectMapper.readValue(manifest.toFile(), Adventure.class);
            if (adventure.getStatus() == null) {
                adventure.setStatus(Status.DRAFT);
            }
            return adventure;
        } catch (JacksonException e) {
            throw new AdventurePackageImportException(
                    DEFAULT_MANIFEST_NAME,
                    "Package manifest is not valid JSON: " + e.getMessage());
        }
    }

    private AdventureDto persist(Adventure adventure, boolean overwrite) {
        String id = adventure.getId();

        // Import is a create-by-default operation. A collision with an already
        // persisted adventure is only allowed when the caller explicitly opts in
        // to an overwrite, so an import can never silently clobber content.
        if (!overwrite && adventureRepository.existsById(id)) {
            throw new AdventureImportConflictException(id);
        }

        Adventure target;
        if (overwrite && adventureRepository.findById(id).isPresent()) {
            // Non-silent, audited update: reuse the existing document so the
            // revision counter and timestamps advance.
            target = adventureRepository.findById(id).orElseThrow();
            copyContent(adventure, target);
            target.touch(Instant.now());
        } else {
            Adventure document = new Adventure();
            document.setId(id);
            document.setSchemaVersion(Adventure.SCHEMA_VERSION);
            document.setRevision(1);
            Instant now = Instant.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            copyContent(adventure, document);
            target = document;
        }

        return AdventureDto.from(adventureRepository.save(target));
    }

    private static void copyContent(Adventure source, Adventure target) {
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setGameSystem(source.getGameSystem());
        target.setRecommendedPlayerCount(source.getRecommendedPlayerCount());
        target.setStatus(source.getStatus() != null ? source.getStatus() : Status.DRAFT);
        target.setChapters(source.getChapters());
        target.setLocations(source.getLocations());
        target.setNpcs(source.getNpcs());
        target.setCreatures(source.getCreatures());
        target.setObjectives(source.getObjectives());
        target.setBranches(source.getBranches());
        target.setRewards(source.getRewards());
        target.setSecretNotes(source.getSecretNotes());
        target.setWorldFacts(source.getWorldFacts());
        target.setTags(source.getTags());
    }

    private void deleteRecursivelyQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted((x, y) -> Integer.compare(y.getNameCount(), x.getNameCount()))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("Failed to delete staged import path {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to clean up staging directory {}", dir, e);
        }
    }

    @PreDestroy
    void cleanupStaging() {
        if (stagingRoot != null && Files.exists(stagingRoot)) {
            deleteRecursivelyQuietly(stagingRoot);
        }
    }
}
