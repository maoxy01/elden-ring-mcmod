package com.dsh.eldencombat;

import com.dsh.eldencombat.server.grace.GraceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GraceSettingsTest {
    @TempDir Path directory;

    @Test void newProfilePersistsFullRestAndAllowsWildTravel() {
        Path file = directory.resolve("grace.json");
        var settings = GraceSettings.loadOrCreate(file);
        var restored = GraceSettings.loadOrCreate(file);
        assertEquals(GraceSettings.Rest.FULL, settings.restHealth);
        assertEquals(GraceSettings.Rest.FULL, restored.restFp);
        assertEquals(GraceSettings.Rest.FULL, restored.restStamina);
        assertEquals(GraceSettings.Rest.FULL, restored.restFlasks);
        assertTrue(restored.restCleanse);
        assertFalse(restored.requireNearby);
    }

    @Test void explicitLegacyKeepIsRespectedRatherThanSilentlyMigrated() throws Exception {
        Path file = directory.resolve("grace.json");
        Files.writeString(file, "{\"restHealth\":\"KEEP\",\"restStamina\":\"KEEP\",\"restFlasks\":\"KEEP\",\"restCleanse\":false}");
        var settings = GraceSettings.loadOrCreate(file);
        assertEquals(GraceSettings.Rest.KEEP, settings.restHealth);
        assertEquals(GraceSettings.Rest.KEEP, settings.restStamina);
        assertEquals(GraceSettings.Rest.KEEP, settings.restFlasks);
        assertFalse(settings.restCleanse);
    }

    @Test void nullRestRulesUseTheNewSafeDefault() throws Exception {
        Path file = directory.resolve("grace.json");
        Files.writeString(file, "{\"restHealth\":null,\"restStamina\":null,\"restFlasks\":null}");
        var settings = GraceSettings.loadOrCreate(file);
        assertEquals(GraceSettings.Rest.FULL, settings.restHealth);
        assertEquals(GraceSettings.Rest.FULL, settings.restStamina);
        assertEquals(GraceSettings.Rest.FULL, settings.restFlasks);
    }
}
