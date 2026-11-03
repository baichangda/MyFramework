package cn.bcd.lib.spring.database.mongo.code.data;

public class BeanField {
    public String name;
    /** 可直接用于 Java 源码的完整泛型类型；基本类型转换为包装类型。 */
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
