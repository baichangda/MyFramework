package cn.bcd.lib.parser.base.util;


import cn.bcd.lib.base.exception.BaseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RpnUtil {

    public static void main(String[] args) {
//        Map<String, Double> dataMap = new HashMap<>();
//        dataMap.put("a", 1d);
//        dataMap.put("b", 3.4d);
//        dataMap.put("x", 0.5d);
//        dataMap.put("y", 2d);
//        dataMap.put("hello", 2d);
//        System.out.println(calc("(a+1)*(b+2)*(y+3)/(x-1)", dataMap));
//        System.out.println(toExpr(toRpn("(a+1)*(b+2)*(y+3)/(x-1)")));
//        System.out.println(toExpr(toRpn("--(a/-3.11-4.2)--d")));
//        System.out.println(toExpr(toRpn("(-1+3)*(-10.4-x)/2-1.5")));
//        System.out.println(calc("x", dataMap));
//        System.out.println(calc("x-0", dataMap));
//        System.out.println(calc("2*-x-3", dataMap));
//        System.out.println(calc("-((x+3)/2)", dataMap));
//        System.out.println(calc("--(a/-3.11-4.2)--b", dataMap));
//        System.out.println(calc("-(-x+3)/5", dataMap));
//        System.out.println(calc("-(-hello+3)/5*hello", dataMap));
//        System.out.println(Arrays.toString(toRpn("(a+1)*(b+2)*(y+3)/(x-1)")));
//        System.out.println(Arrays.toString(toRpn("x")));
//        System.out.println(Arrays.toString(toRpn("-y")));
//        System.out.println(Arrays.toString(toRpn("2*-x-3")));
//        System.out.println(Arrays.toString(toRpn("-((x+3)/2)")));
//        System.out.println(Arrays.toString(toRpn("2/(-x*4-3)")));
//        System.out.println(Arrays.toString(toRpn("-(a-(b+(c)))")));
//        System.out.println(Arrays.toString(toRpn("-2*x+3")));
//        System.out.println(Arrays.toString(toRpn("(y-3)/-2")));
//        System.out.println(Arrays.toString(toRpn("1-4.232")));
//        System.out.println(Arrays.toString(toRpn("---4")));
//        System.out.println(Arrays.toString(toRpn("--4")));
//        System.out.println(Arrays.toString(toRpn("--(a/-3.11-4.2)--d")));
//        System.out.println(Arrays.toString(toRpn("(a-(b+(c)))")));
//        System.out.println(Arrays.toString(toRpn("(a-(b+(c)))")));
//        System.out.println(Arrays.toString(toRpn("-3+-x-1")));
//        System.out.println(Arrays.toString(toRpn("-3*-x-1")));
//        System.out.println(Arrays.toString(toRpn("-(-x+3)/5")));
//        System.out.println(Arrays.toString(toRpn("(-x+3)/5")));
//        System.out.println(Arrays.toString(toRpn("--(a/-3-4)--xy")));

//        System.out.println(reverseExpr("@b/10-1000"));
//        System.out.println(reverseExpr("-x"));
//        System.out.println(reverseExpr("(y-3)/-2"));
//        System.out.println(reverseExpr("2/(-x*4-3)"));
//        System.out.println(reverseExpr("-2*x+3"));
//        System.out.println(reverseExpr("--(x/-3-4)"));
//        System.out.println(reverseExpr("(-x+3)/5"));
//        System.out.println(reverseExpr("(-1+3)*(-10.4-(-x))/2-1.5*(2-1.3)"));
    }

    /**
     * 对变量只出现一次的表达式逐层移项求解。
     * 例如输入 x*10+3，输出 ((x-3)/10)，输出中的 x 代表原等式的 y。
     * 保留数字原文和运算括号，不改变调用方的数值类型和舍入规则。
     */
    public static String reverseExpr(String str) {
        if (str == null) {
            throw new IllegalArgumentException("param[str] must not be null");
        }
        if (str.isEmpty()) {
            return "";
        }
        ReverseNode expression = parseReverseExpression(str);
        if (expression.variableCount != 1) {
            throw BaseException.get("reverseExpr requires a single variable occurrence: {}", str);
        }

        ReverseNode result = new ReverseNode("x", null, null, 1, 0);
        while (expression.left != null) {
            if (expression.token.equals("!")) {
                result = ReverseNode.negate(result);
                expression = expression.left;
                continue;
            }
            boolean variableOnLeft = expression.left.variableCount == 1;
            ReverseNode constant = variableOnLeft ? expression.right : expression.left;
            result = switch (expression.token) {
                case "+" -> ReverseNode.binary("-", result, constant);
                case "-" -> variableOnLeft
                        ? ReverseNode.binary("+", result, constant)
                        : ReverseNode.binary("-", constant, result);
                case "*" -> ReverseNode.binary("/", result, constant);
                case "/" -> variableOnLeft
                        ? ReverseNode.binary("*", result, constant)
                        : ReverseNode.binary("/", constant, result);
                default -> throw BaseException.get("unsupported operator[{}]", expression.token);
            };
            expression = variableOnLeft ? expression.left : expression.right;
        }
        StringBuilder resultCode = new StringBuilder();
        result.appendCode(resultCode);
        return resultCode.toString();
    }

    private static ReverseNode parseReverseExpression(String str) {
        int depth = 0;
        char previous = '\0';
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '(') {
                if (previous != '\0' && "+-*/(".indexOf(previous) < 0) {
                    throw BaseException.get("invalid expression, missing operator before parentheses: {}", str);
                }
                depth++;
            } else if (c == ')' && --depth < 0) {
                throw BaseException.get("invalid expression, unbalanced parentheses: {}", str);
            }
            previous = c;
        }
        if (depth != 0) {
            throw BaseException.get("invalid expression, unbalanced parentheses: {}", str);
        }

        Ele[] elements = toRpn(str);
        ReverseNode[] stack = new ReverseNode[elements.length];
        int size = 0;
        for (Ele element : elements) {
            if (element.s == null) {
                if (!Double.isFinite(element.d)) {
                    throw BaseException.get("non-finite constant in expression: {}", str);
                }
                stack[size++] = new ReverseNode(element.ds, null, null, 0, element.d);
            } else if (element.s.equals("!")) {
                if (size < 1) {
                    throw BaseException.get("invalid expression, missing operand: {}", str);
                }
                stack[size - 1] = ReverseNode.negate(stack[size - 1]);
            } else if (element.s.equals("+") || element.s.equals("-")
                    || element.s.equals("*") || element.s.equals("/")) {
                if (size < 2) {
                    throw BaseException.get("invalid expression, missing operand: {}", str);
                }
                ReverseNode right = stack[--size];
                ReverseNode left = stack[size - 1];
                if (left.variableCount + right.variableCount > 1) {
                    throw BaseException.get("reverseExpr requires a single variable occurrence: {}", str);
                }
                stack[size - 1] = ReverseNode.binary(element.s, left, right);
            } else {
                stack[size++] = new ReverseNode("x", null, null, 1, 0);
            }
        }
        if (size != 1) {
            throw BaseException.get("invalid expression: {}", str);
        }
        return stack[0];
    }

    /** 只在处理器构建阶段使用；constantValue 仅用于检测不可逆的零常量，不用于生成代码。 */
    private record ReverseNode(String token, ReverseNode left, ReverseNode right,
                               int variableCount, double constantValue) {
        static ReverseNode negate(ReverseNode operand) {
            return new ReverseNode("!", operand, null, operand.variableCount, -operand.constantValue);
        }

        static ReverseNode binary(String operator, ReverseNode left, ReverseNode right) {
            int variableCount = left.variableCount + right.variableCount;
            if (operator.equals("/") && right.variableCount == 0 && right.constantValue == 0) {
                throw BaseException.get("expression divides by zero");
            }
            if (variableCount == 1) {
                ReverseNode constant = left.variableCount == 0 ? left : right;
                if (constant.constantValue == 0 && (operator.equals("*")
                        || operator.equals("/") && left.variableCount == 0)) {
                    throw BaseException.get("expression is not invertible: zero factor or numerator");
                }
            }
            double constantValue = 0;
            if (variableCount == 0) {
                constantValue = switch (operator) {
                    case "+" -> left.constantValue + right.constantValue;
                    case "-" -> left.constantValue - right.constantValue;
                    case "*" -> left.constantValue * right.constantValue;
                    case "/" -> left.constantValue / right.constantValue;
                    default -> throw BaseException.get("unsupported operator[{}]", operator);
                };
                if (!Double.isFinite(constantValue)) {
                    throw BaseException.get("non-finite constant expression");
                }
            }
            return new ReverseNode(operator, left, right, variableCount, constantValue);
        }

        void appendCode(StringBuilder code) {
            if (left == null) {
                code.append(token);
            } else {
                code.append('(');
                if (token.equals("!")) {
                    code.append('-');
                    left.appendCode(code);
                } else {
                    left.appendCode(code);
                    code.append(token);
                    right.appendCode(code);
                }
                code.append(')');
            }
        }
    }

    public static String toExpr(Ele[] eles) {
        if (eles == null || eles.length == 0) {
            throw new IllegalArgumentException("param[eles] must not be null or empty");
        }
        final String[] stack = new String[eles.length];
        int stackIndex = -1;
        for (Ele ele : eles) {
            if (ele == null) {
                throw new IllegalArgumentException("param[eles] must not contain null elements");
            }
            if (ele.s == null) {
                stack[++stackIndex] = ele.ds != null ? ele.ds : Double.toString(ele.d);
            } else {
                switch (ele.s) {
                    case "!": {
                        if (stackIndex < 0) {
                            throw new IllegalArgumentException("invalid RPN: missing operand for !");
                        }
                        stack[stackIndex] = "(-" + stack[stackIndex] + ")";
                        break;
                    }
                    case "+":
                    case "-":
                    case "*":
                    case "/": {
                        if (stackIndex < 1) {
                            throw new IllegalArgumentException("invalid RPN: missing operands for " + ele.s);
                        }
                        stack[stackIndex - 1] = "(" + stack[stackIndex - 1] + ele.s + stack[stackIndex] + ")";
                        stackIndex--;
                        break;
                    }
                    default: {
                        stack[++stackIndex] = ele.s;
                    }
                }
            }
        }
        if (stackIndex != 0) {
            throw new IllegalArgumentException("invalid RPN: expected exactly one result");
        }
        return stack[0];
    }

    public static double calc(String str, Map<String, Double> dataMap) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("param[str] must not be null or empty");
        }
        return calc(toRpn(str), dataMap);
    }

    public static double calc(Ele[] eles, Map<String, Double> dataMap) {
        if (eles == null || eles.length == 0) {
            throw new IllegalArgumentException("param[eles] must not be null or empty");
        }
        final double[] stack = new double[eles.length];
        int stackIndex = -1;
        for (Ele ele : eles) {
            if (ele.s == null) {
                stack[++stackIndex] = ele.d;
            } else {
                switch (ele.s) {
                    case "!": {
                        stack[stackIndex] = -stack[stackIndex];
                        break;
                    }
                    case "+": {
                        stack[stackIndex - 1] = stack[stackIndex - 1] + stack[stackIndex];
                        stackIndex--;
                        break;
                    }
                    case "-": {
                        stack[stackIndex - 1] = stack[stackIndex - 1] - stack[stackIndex];
                        stackIndex--;
                        break;
                    }
                    case "*": {
                        stack[stackIndex - 1] = stack[stackIndex - 1] * stack[stackIndex];
                        stackIndex--;
                        break;
                    }
                    case "/": {
                        stack[stackIndex - 1] = stack[stackIndex - 1] / stack[stackIndex];
                        stackIndex--;
                        break;
                    }
                    default: {
                        final Double d = dataMap.get(ele.s);
                        if (d == null) {
                            throw BaseException.get("var[{}] not exist", ele.s);
                        }
                        stack[++stackIndex] = d;
                    }
                }
            }
        }
        return stack[0];
    }

    private static Ele[] toRpn(String[] rpn) {
        final Ele[] eles = new Ele[rpn.length];
        for (int i = 0; i < rpn.length; i++) {
            try {
                final double v = Double.parseDouble(rpn[i]);
                eles[i] = new Ele(v, rpn[i]);
            } catch (NumberFormatException ex) {
                eles[i] = new Ele(rpn[i]);
            }
        }
        return eles;
    }

    /**
     * 算术表达式转换为 逆波兰表达式形式
     * 支持 ( + - * / ) 和 负号
     * 支持变量
     * 结果中
     * 负号将会被翻译成!
     * 所有的数字没有负数、因为负号被提出来为!后置
     *
     * @param str
     * @return
     */
    public static Ele[] toRpn(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("param[str] must not be null or empty");
        }
        final char[] chars = str.toCharArray();
        //存储rpn结果
        final List<String> output = new ArrayList<>();
        //存储操作符的栈
        final char[] operatorStack = new char[chars.length];
        int operatorStackIndex = -1;
        //用于收集一个个字符、整理成一个变量
        final StringBuilder temp = new StringBuilder();
        char previous = '\0';

        for (int i = 0; i <= chars.length - 1; i++) {
            final char c = chars[i];
            if (Character.isWhitespace(c)) {
                continue;
            }
            final char previousChar = previous;
            previous = c;
            switch (c) {
                case '+':
                case '*':
                case '/': {
                    if (!temp.isEmpty()) {
                        //当遇到符号、收集变量
                        output.add(temp.toString());
                        temp.delete(0, temp.length());
                    }
                    //开始回溯符号栈、将优先级高于当前符号的符号弹出、视为优先运算
                    if (operatorStackIndex >= 0) {
                        while (operatorStackIndex >= 0 && operatorStack[operatorStackIndex] != '(' &&
                                getSymbolPriority(operatorStack[operatorStackIndex]) >= getSymbolPriority(c)) {
                            output.add(String.valueOf(operatorStack[operatorStackIndex--]));
                        }
                    }
                    //符号入栈
                    operatorStack[++operatorStackIndex] = c;
                    break;
                }
                case '-': {
                    //两种情况下表示不是取负
                    //1、temp里面有数据、代表有变量
                    //2、上一个字符是)、代表刚处理完一个()表达式
                    if (temp.isEmpty() && previousChar != ')') {
                        //此时取负号
                        operatorStack[++operatorStackIndex] = '!';
                    } else {
                        //当遇到符号、收集变量
                        if (!temp.isEmpty()) {
                            output.add(temp.toString());
                            temp.delete(0, temp.length());
                        }
                        //开始回溯符号栈、将优先级高于当前符号的符号弹出、视为优先运算
                        if (operatorStackIndex >= 0) {
                            while (operatorStackIndex >= 0 && operatorStack[operatorStackIndex] != '(' &&
                                    getSymbolPriority(operatorStack[operatorStackIndex]) >= getSymbolPriority(c)) {
                                output.add(String.valueOf(operatorStack[operatorStackIndex--]));
                            }
                        }
                        //符号入栈
                        operatorStack[++operatorStackIndex] = c;
                    }
                    break;
                }
                case '(': {
                    //符号入栈
                    operatorStack[++operatorStackIndex] = c;
                    break;
                }
                case ')': {
                    if (!temp.isEmpty()) {
                        //当遇到)、收集变量
                        output.add(temp.toString());
                        temp.delete(0, temp.length());
                    }
                    //处理括号内
                    //开始回溯符号栈、将优先级高于当前符号的符号弹出、视为优先运算
                    if (operatorStackIndex >= 0) {
                        while (operatorStackIndex >= 0 && operatorStack[operatorStackIndex] != '(') {
                            output.add(String.valueOf(operatorStack[operatorStackIndex--]));
                        }
                        //弹出(
                        if (operatorStackIndex != -1) {
                            operatorStackIndex--;
                        }
                    }
                    break;
                }

                default: {
                    temp.append(c);
                }
            }
        }

        if (!temp.isEmpty()) {
            output.add(temp.toString());
        }

        for (int i = operatorStackIndex; i >= 0; i--) {
            output.add(String.valueOf(operatorStack[i]));
        }

        //去除重复的!、负负得正
        final List<String> newOutput = new ArrayList<>();
        int count = 0;
        for (final String s : output) {
            if ("!".equals(s)) {
                count++;
            } else {
                if (count % 2 == 1) {
                    newOutput.add("!");
                }
                count = 0;
                newOutput.add(s);
            }
        }
        if (count % 2 == 1) {
            newOutput.add("!");
        }

        return toRpn(newOutput.toArray(new String[0]));
    }

    /**
     * 获取字符优先级
     *
     * @param c
     * @return
     */
    private static int getSymbolPriority(char c) {
        switch (c) {
            case '+':
            case '-': {
                return 1;
            }
            case '*':
            case '/': {
                return 2;
            }
            case '!': {
                return 3;
            }
            default: {
                throw BaseException.get("symbol[" + c + "] not support");
            }
        }
    }

    static class Ele {
        //存储变量名称、null代表是数字
        final String s;
        //存储数字值
        final double d;
        //存储浮点数的字符串表达式、因为d会默认转换为科学计数法表示
        final String ds;

        public Ele(String s) {
            this.s = s;
            this.d = 0d;
            this.ds = null;
        }

        public Ele(double d, String ds) {
            this.s = null;
            this.d = d;
            this.ds = ds;
        }

        @Override
        public String toString() {
            return s == null ? ("num[" + d + "]") : "str[" + s + "]";
        }
    }


}
