package cn.bcd.lib.spring.database.mongo.code.data;

public class BeanField {
    public String name;
    /** Java 类型表达式，常用类型使用简写；基本类型转换为包装类型。 */
    public String type;
    public String comment;
    public String commentPre;
    public String queryKind;
    public boolean writable;
    public String beanExpression;
    public String createStatement;

    public BeanField setComment(String comment) {
        this.comment = comment == null ? name : comment;
        this.commentPre = this.comment;
        return this;
    }
}
