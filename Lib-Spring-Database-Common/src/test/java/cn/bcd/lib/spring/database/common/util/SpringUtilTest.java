package cn.bcd.lib.spring.database.common.util;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringUtilTest {
    @Test
    void loadsYamlProfilesInOrder() throws Exception {
        String oldProfiles = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "first, second");
        try {
            JsonNode[] values = SpringUtil.getSpringPropsInYml(
                    "database.host", "database.firstOnly", "database.baseOnly", "database.missing");
            assertEquals("second", values[0].stringValue());
            assertEquals("first", values[1].stringValue());
            assertEquals("base", values[2].stringValue());
            assertNull(values[3]);
        } finally {
            if (oldProfiles == null) {
                System.clearProperty("spring.profiles.active");
            } else {
                System.setProperty("spring.profiles.active", oldProfiles);
            }
        }
    }

    @Test
    void rejectsBlankKeys() {
        assertThrows(IllegalArgumentException.class, () -> SpringUtil.getSpringPropsInYml(" "));
    }
}
