package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassUtilTest {

    private static class GrandParent {
        public int grandParentPublic;
        private int grandParentPrivate;
    }

    private static class Parent extends GrandParent {
        private static int staticValue;
        public final int parentPublicFinal = 1;
        public int parentPublic;
        private int parentPrivate;
        protected int parentProtected;
    }

    private class Child extends Parent {
        private int childPrivate;
        int childPackage;
    }

    @Test
    void getAllFieldsReturnsDeclaredInstanceFieldsAcrossHierarchy() {
        List<Field> fields = ClassUtil.getAllFields(Child.class, true);
        List<String> fieldNames = fields.stream().map(Field::getName).toList();

        assertTrue(fieldNames.contains("grandParentPublic"));
        assertTrue(fieldNames.contains("parentPublic"));
        assertTrue(fieldNames.contains("childPrivate"));
        assertTrue(fieldNames.contains("childPackage"));
        assertFalse(fieldNames.contains("grandParentPrivate"));
        assertFalse(fieldNames.contains("parentPrivate"));
        assertFalse(fieldNames.contains("parentProtected"));
        assertFalse(fieldNames.contains("staticValue"));
        assertFalse(fieldNames.contains("parentPublicFinal"));
        assertFalse(fields.stream().anyMatch(Field::isSynthetic));

        int grandParentField = -1;
        int parentField = -1;
        int firstChildField = fields.size();
        for (int i = 0; i < fields.size(); i++) {
            Class<?> declaringClass = fields.get(i).getDeclaringClass();
            if (declaringClass == GrandParent.class) {
                grandParentField = i;
            } else if (declaringClass == Parent.class) {
                parentField = i;
            } else if (fields.get(i).getDeclaringClass() == Child.class && firstChildField == fields.size()) {
                firstChildField = i;
            }
        }
        assertTrue(grandParentField < parentField);
        assertTrue(parentField < firstChildField);
    }

    @Test
    void getAllFieldsKeepsFinalFieldsWhenRequested() {
        List<String> fieldNames = ClassUtil.getAllFields(Child.class, false).stream()
                .map(Field::getName)
                .toList();

        assertTrue(fieldNames.contains("parentPublicFinal"));
        assertFalse(fieldNames.contains("staticValue"));
    }
}
