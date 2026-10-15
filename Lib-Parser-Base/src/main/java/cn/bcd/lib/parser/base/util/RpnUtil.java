package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.base.exception.BaseException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 算术表达式的逆波兰转换与单变量求逆工具。 */
public final class RpnUtil {
    private static final char NEGATE = '!';

    private RpnUtil() {
    }

    /**
     * 对变量仅出现一次的表达式逐层移项求逆，例如 x*10+3 转为 ((x-3)/10)。
     * 输入变量名可以不同，输出统一以 x 表示原表达式的计算结果；空串仍返回空串。
     * 保留数字原文和运算分组，不替调用方决定数值类型及舍入方式。
     *
     * @param expression 中缀表达式
     * @return 带显式运算括号的逆表达式，单独的变量返回 x
     * @throws IllegalArgumentException 输入为 null
     * @throws BaseException 表达式非法、变量出现次数不为一，或存在零因子、零分母等不可逆运算
     */
    public static String reverseExpr(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression must not be null");
        }
        if (expression.isEmpty()) {
            return "";
        }
        ExpressionNode node = parseExpressionTree(expression);
        if (node.variableCount != 1) {
            throw BaseException.get("reverseExpr requires a single variable occurrence: {}", expression);
        }
        ExpressionNode result = ExpressionNode.variable();
        while (node.left != null) {
            if (node.text.equals("!")) {
                result = ExpressionNode.negate(result);
                node = node.left;
                continue;
            }
            boolean variableOnLeft = node.left.variableCount == 1;
            ExpressionNode constant = variableOnLeft ? node.right : node.left;
            result = switch (node.text) {
                case "+" -> ExpressionNode.binary("-", result, constant);
                case "-" -> variableOnLeft
                        ? ExpressionNode.binary("+", result, constant)
                        : ExpressionNode.binary("-", constant, result);
                case "*" -> ExpressionNode.binary("/", result, constant);
                case "/" -> variableOnLeft
                        ? ExpressionNode.binary("*", result, constant)
                        : ExpressionNode.binary("/", constant, result);
                default -> throw BaseException.get("unsupported operator[{}]", node.text);
            };
            node = variableOnLeft ? node.left : node.right;
        }
        StringBuilder code = new StringBuilder();
        result.appendCode(code);
        return code.toString();
    }

    /**
     * 将中缀表达式转换为逆波兰序列，支持变量、数字、括号及 +、-、*、/。
     * 一元负号输出为 !，连续负号按奇偶抵消；二元运算遵循优先级并按左结合处理。
     * 空白仅用作 token 分隔，不能连接两个操作数；不支持隐式乘法和一元正号。
     *
     * @param expression 非空中缀表达式
     * @return 按求值顺序排列的 token；数字同时保留原文与 double 值
     * @throws IllegalArgumentException 输入为 null 或空串
     * @throws BaseException 操作数缺失、括号不匹配或数字非法
     */
    public static Token[] toRpn(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("expression must not be null or empty");
        }
        List<Token> output = new ArrayList<>();
        Deque<Character> operators = new ArrayDeque<>();
        boolean expectingOperand = true;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            switch (current) {
                case '(' -> {
                    require(expectingOperand, expression, index);
                    operators.push(current);
                }
                case ')' -> {
                    require(!expectingOperand, expression, index);
                    while (!operators.isEmpty() && operators.peek() != '(') {
                        appendOperator(output, operators.pop());
                    }
                    require(!operators.isEmpty(), expression, index);
                    operators.pop();
                }
                case '+', '-', '*', '/' -> {
                    if (current == '-' && expectingOperand) {
                        operators.push(NEGATE);
                        continue;
                    }
                    require(!expectingOperand, expression, index);
                    while (!operators.isEmpty() && operators.peek() != '('
                            && operatorPrecedence(operators.peek()) >= operatorPrecedence(current)) {
                        appendOperator(output, operators.pop());
                    }
                    operators.push(current);
                    expectingOperand = true;
                }
                default -> {
                    require(expectingOperand, expression, index);
                    int start = index;
                    while (index + 1 < expression.length() && !isDelimiter(expression.charAt(index + 1))) {
                        index++;
                    }
                    output.add(parseOperand(expression.substring(start, index + 1)));
                    expectingOperand = false;
                }
            }
        }
        require(!expectingOperand, expression, expression.length());
        while (!operators.isEmpty()) {
            char operator = operators.pop();
            require(operator != '(', expression, expression.length());
            appendOperator(output, operator);
        }
        return output.toArray(Token[]::new);
    }

    /** 在解析入口统一校验语法，异常包含原表达式与出错位置。 */
    private static void require(boolean valid, String expression, int index) {
        if (!valid) {
            throw BaseException.get("invalid expression at index[{}]: {}", index, expression);
        }
    }

    private static boolean isDelimiter(char character) {
        return Character.isWhitespace(character) || "+-*/()".indexOf(character) >= 0;
    }

    /** 数字保留原文；变量采用 Java 标识符命名，避免将非法数字当成变量。 */
    private static Token parseOperand(String text) {
        try {
            double number = Double.parseDouble(text);
            if (!Double.isFinite(number)) {
                throw BaseException.get("non-finite constant: {}", text);
            }
            return new Token(text, number);
        } catch (NumberFormatException ignored) {
            if (!Character.isJavaIdentifierStart(text.charAt(0))
                    || !text.chars().allMatch(Character::isJavaIdentifierPart)) {
                throw BaseException.get("invalid operand: {}", text);
            }
            return new Token(text, null);
        }
    }

    /** 相邻的一元负号作用于同一个操作数，两个负号可直接抵消。 */
    private static void appendOperator(List<Token> output, char operator) {
        if (operator == NEGATE && !output.isEmpty() && output.getLast().text.equals("!")) {
            output.removeLast();
        } else {
            output.add(new Token(String.valueOf(operator), null));
        }
    }

    private static int operatorPrecedence(char operator) {
        return switch (operator) {
            case '+', '-' -> 1;
            case '*', '/' -> 2;
            case NEGATE -> 3;
            default -> throw BaseException.get("unsupported operator[{}]", operator);
        };
    }

    /** 已由 toRpn 校验语法，这里只构建求逆所需的树并检查变量次数。 */
    private static ExpressionNode parseExpressionTree(String expression) {
        Deque<ExpressionNode> nodes = new ArrayDeque<>();
        for (Token token : toRpn(expression)) {
            if (token.isNumber()) {
                nodes.push(new ExpressionNode(token.text, null, null, 0, token.number));
            } else if (token.text.equals("!")) {
                nodes.push(ExpressionNode.negate(nodes.pop()));
            } else if (token.text.length() == 1 && "+-*/".contains(token.text)) {
                ExpressionNode right = nodes.pop();
                ExpressionNode left = nodes.pop();
                if (left.variableCount + right.variableCount > 1) {
                    throw BaseException.get("reverseExpr requires a single variable occurrence: {}", expression);
                }
                nodes.push(ExpressionNode.binary(token.text, left, right));
            } else {
                nodes.push(ExpressionNode.variable());
            }
        }
        return nodes.pop();
    }

    /**
     * 逆波兰序列元素。
     * @param text 数字原文、变量名或操作符；! 代表一元负号
     * @param number 数字值；变量和操作符为 null
     */
    public record Token(String text, Double number) {
        /** 是否为数字常量。 */
        public boolean isNumber() {
            return number != null;
        }
    }

    /** constantValue 仅用于检查零常量和溢出，不参与输出代码的常量折叠。 */
    private record ExpressionNode(String text, ExpressionNode left, ExpressionNode right,
                                  int variableCount, double constantValue) {
        static ExpressionNode variable() {
            return new ExpressionNode("x", null, null, 1, 0);
        }

        static ExpressionNode negate(ExpressionNode operand) {
            return new ExpressionNode("!", operand, null, operand.variableCount, -operand.constantValue);
        }

        /** 检查除零及不可逆运算，同时计算纯常量子树的值。 */
        static ExpressionNode binary(String operator, ExpressionNode left, ExpressionNode right) {
            int variableCount = left.variableCount + right.variableCount;
            if (operator.equals("/") && right.variableCount == 0 && right.constantValue == 0) {
                throw BaseException.get("expression divides by zero");
            }
            if (variableCount == 1) {
                ExpressionNode constant = left.variableCount == 0 ? left : right;
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
            return new ExpressionNode(operator, left, right, variableCount, constantValue);
        }

        /** 每层运算显式加括号，防止生成代码时优先级发生变化。 */
        void appendCode(StringBuilder code) {
            if (left == null) {
                code.append(text);
                return;
            }
            code.append('(');
            if (text.equals("!")) {
                code.append('-');
                left.appendCode(code);
            } else {
                left.appendCode(code);
                code.append(text);
                right.appendCode(code);
            }
            code.append(')');
        }
    }
}
