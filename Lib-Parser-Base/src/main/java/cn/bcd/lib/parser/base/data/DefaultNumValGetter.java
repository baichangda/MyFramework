package cn.bcd.lib.parser.base.data;

public class DefaultNumValGetter extends NumValGetter {
    public final static DefaultNumValGetter instance = new DefaultNumValGetter();

    private DefaultNumValGetter() {

    }


    @Override
    public int getType(NumType numType, int val) {
        return switch (numType) {
            case uint8, int8 -> switch (val & 0xff) {
                case 0xFF -> 1;
                case 0xFE -> 2;
                default -> 0;
            };
            case uint16, int16 -> switch (val & 0xFFFF) {
                case 0xFFFF -> 1;
                case 0xFFFE -> 2;
                default -> 0;
            };
            case uint24, int24 -> switch (val & 0xFFFFFF) {
                case 0xFFFFFF -> 1;
                case 0xFFFFFE -> 2;
                default -> 0;
            };
            case int32, uint32 -> switch (val) {
                case 0xFFFFFFFF -> 1;
                case 0xFFFFFFFE -> 2;
                default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public int getType(NumType numType, long val) {
        switch (numType) {
            case uint40, int40 -> {
                if (val == 0xFFFFFFFFFFL) {
                    return 1;
                } else if (val == 0xFFFFFFFFFEL) {
                    return 2;
                } else {
                    return 0;
                }
            }
            case uint48, int48 -> {
                if (val == 0xFFFFFFFFFFFFL) {
                    return 1;
                } else if (val == 0xFFFFFFFFFFFEL) {
                    return 2;
                } else {
                    return 0;
                }
            }
            case uint56, int56 -> {
                if (val == 0xFFFFFFFFFFFFFFL) {
                    return 1;
                } else if (val == 0xFFFFFFFFFFFFFEL) {
                    return 2;
                } else {
                    return 0;
                }
            }
            case uint64, int64 -> {
                if (val == 0xFFFFFFFFFFFFFFFFL) {
                    return 1;
                } else if (val == 0xFFFFFFFFFFFFFFFEL) {
                    return 2;
                } else {
                    return 0;
                }
            }
            default -> {
                return 0;
            }
        }
    }

    @Override
    public int getVal_int(NumType numType, int type) {
        return switch (type) {
            case 1 -> switch (numType) {
                case uint8, int8 -> 0xFF;
                case uint16, int16 -> 0xFFFF;
                case uint24, int24 -> 0xFFFFFF;
                case int32, uint32 -> 0xFFFFFFFF;
                default -> 0;
            };
            case 2 -> switch (numType) {
                case uint8, int8 -> 0xFE;
                case uint16, int16 -> 0xFFFE;
                case uint24, int24 -> 0xFFFFFE;
                case int32, uint32 -> 0xFFFFFFFE;
                default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public long getVal_long(NumType numType, int type) {
        return switch (type) {
            case 1 -> switch (numType) {
                case uint40, int40 -> 0xFFFFFFFFFFL;
                case uint48, int48 -> 0xFFFFFFFFFFFFL;
                case uint56, int56 -> 0xFFFFFFFFFFFFFFL;
                case uint64, int64 -> 0xFFFFFFFFFFFFFFFFL;
                default -> 0;
            };
            case 2 -> switch (numType) {
                case uint40, int40 -> 0xFFFFFFFFFEL;
                case uint48, int48 -> 0xFFFFFFFFFFFEL;
                case uint56, int56 -> 0xFFFFFFFFFFFFFEL;
                case uint64, int64 -> 0xFFFFFFFFFFFFFFFEL;
                default -> 0;
            };
            default -> 0;
        };
    }

    public static void main(String[] args) {
        DefaultNumValGetter defaultNumValGetter = new DefaultNumValGetter();
        System.out.println(defaultNumValGetter.getType(NumType.uint32, 0xFFFFFFFF));
    }
}
