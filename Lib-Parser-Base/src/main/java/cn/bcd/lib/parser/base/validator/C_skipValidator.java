package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.C_skip;
import cn.bcd.lib.parser.base.util.ParseUtil;

public final class C_skipValidator {
    private C_skipValidator() {
    }

    public static void validate(Class<?> clazz, C_skip annotation) {
        ValidatorUtil.validateLengthPair(clazz.getName(), "@C_skip", annotation.len(), annotation.lenExpr());
        if (annotation.len() > 0) {
            int modelLength = ParseUtil.getClassByteLenIfPossible(clazz);
            if (modelLength > annotation.len()) {
                ValidatorUtil.fail("class[{}] @C_skip length[{}] is smaller than model length[{}]",
                        clazz.getName(), annotation.len(), modelLength);
            }
        }
    }
}
