package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.ParserTestSupport;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BeanBuildersRegressionTest {
    @Test
    public void rejectsDirectBeanArrayAndListCyclesBeforeCompilation() {
        for (Class<?> model : new Class<?>[]{SelfBean.class, SelfArray.class, SelfList.class, SizedSelfBean.class}) {
            assertFailure(model, "circular bean dependency", ".children[", model.getName());
        }
    }

    @Test
    public void rejectsIndirectMixedDependenciesAndReportsEveryField() {
        assertFailure(MixedA.class, "circular bean dependency", ".child[", ".parents[");
        assertFailure(MixedA.class, "circular bean dependency", ".child[", ".parents[");
    }

    @Test
    public void rejectsCyclesThroughInterfacesAndInheritedFields() {
        assertFailure(InterfaceCycle.class, "circular bean dependency", ".item[", ".owner[");
        assertFailure(InheritedCycle.class, "circular bean dependency", ".children[");
    }

    @Test
    public void allowsRepeatedDependenciesInDifferentBranches() {
        var processor = Parser.getProcessor(Diamond.class);
        Diamond bean = ParserTestSupport.process(processor, (byte) 1, (byte) 2, (byte) 3);
        assertEquals(1, bean.left.value);
        assertEquals(2, bean.right.value);
        assertEquals(3, bean.list.getFirst().value);
        assertArrayEquals(new byte[]{1, 2, 3}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void createsMutableListsAndSupportsConcreteListTypes() {
        var processor = Parser.getProcessor(CollectionsBean.class);
        CollectionsBean bean = ParserTestSupport.process(processor, (byte) 1, (byte) 2, (byte) 3);
        assertInstanceOf(ArrayList.class, bean.list);
        assertInstanceOf(ArrayList.class, bean.arrayList);
        assertInstanceOf(LinkedList.class, bean.linkedList);
        for (List<Child> list : List.of(bean.list, bean.arrayList, bean.linkedList)) {
            list.add(new Child());
            assertEquals(2, list.size());
            list.removeLast();
        }
        assertArrayEquals(new byte[]{1, 2, 3}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void rejectsUnsupportedCollectionConstructorsBeforeBuildingSource() {
        assertFailure(NoConstructorContainer.class, "public no-argument constructor");
    }

    @Test
    public void inheritedImplAnnotationDoesNotRegisterSubclassAgain() {
        var processor = Parser.getProcessor(InheritedContainer.class);
        var input = Unpooled.wrappedBuffer(new byte[]{7, 8});
        InheritedContainer bean = processor.process(input);
        assertEquals(ParentImpl.class, bean.item.getClass());
        assertEquals(7, ((ParentImpl) bean.item).value);
        assertEquals(1, input.readerIndex());
        assertArrayEquals(new byte[]{7}, ParserTestSupport.deProcess(processor, bean));
    }

    @Test
    public void rejectsConflictingDispatchValuesAndDefaults() {
        assertFailure(DuplicateContainer.class, "duplicate C_impl value[1]", DuplicateA.class.getName(), DuplicateB.class.getName());
        assertFailure(DefaultContainer.class, "duplicate C_impl default", DefaultA.class.getName(), DefaultB.class.getName());
        assertFailure(RepeatedValueContainer.class, "duplicate C_impl value[1]");
    }

    @Test
    public void explicitSubclassRegistrationUsesDistinctValues() {
        Processor<ExplicitContainer> processor = Parser.getProcessor(ExplicitContainer.class);
        ExplicitContainer bean = ParserTestSupport.process(processor, (byte) 2, (byte) 7, (byte) 8);
        assertInstanceOf(ExplicitChild.class, bean.item);
        assertArrayEquals(new byte[]{2, 7, 8}, ParserTestSupport.deProcess(processor, bean));
    }

    private static void assertFailure(Class<?> model, String... messages) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> Parser.getProcessor(model));
        for (String expected : messages) {
            boolean found = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(expected)) {
                    found = true;
                }
            }
            assertTrue(found, expected);
        }
    }

    public static class SelfBean {
        @F_bean
        public SelfBean children;
    }
    @C_skip(len = 8)
    public static class SizedSelfBean {
        @F_bean
        public SizedSelfBean children;
    }
    public static class SelfArray {
        @F_bean_list(listLen = 1)
        public SelfArray[] children;
    }
    public static class SelfList {
        @F_num(type = NumType.uint8, var = 'a')
        public int count;
        @F_bean_list(listLenExpr = "a")
        public List<SelfList> children;
    }
    public static class MixedA {
        @F_bean
        public MixedB child;
    }
    public static class MixedB {
        @F_bean_list(listLen = 1)
        public MixedA[] parents;
    }
    public interface CyclicItem {}
    @C_impl(1)
    public static class CyclicImpl implements CyclicItem {
        @F_bean
        public InterfaceCycle owner;
    }
    public static class InterfaceCycle {
        @F_bean(implClassExpr = "1")
        public CyclicItem item;
    }
    public static class InheritedBase {
        @F_bean_list(listLen = 1)
        public InheritedCycle[] children;
    }
    public static class InheritedCycle extends InheritedBase {}
    public static class Child {
        @F_num(type = NumType.uint8)
        public int value;
    }
    public static class Diamond {
        @F_bean
        public Child left;
        @F_bean
        public Child right;
        @F_bean_list(listLen = 1)
        public List<Child> list;
    }
    public static class CollectionsBean {
        @F_bean_list(listLen = 1)
        public List<Child> list;
        @F_bean_list(listLen = 1)
        public ArrayList<Child> arrayList;
        @F_bean_list(listLen = 1)
        public LinkedList<Child> linkedList;
    }
    public static class NoDefaultList<T> extends ArrayList<T> {
        public NoDefaultList(int capacity) { super(capacity); }
    }
    public static class NoConstructorContainer {
        @F_bean_list(listLen = 1)
        public NoDefaultList<Child> children;
    }
    public interface InheritedItem {}
    @C_impl(1)
    public static class ParentImpl implements InheritedItem {
        @F_num(type = NumType.uint8)
        public int value;
    }
    public static class ChildImpl extends ParentImpl {
        @F_num(type = NumType.uint8)
        public int extra;
    }
    public static class InheritedContainer {
        @F_bean(implClassExpr = "1")
        public InheritedItem item;
    }
    public interface DuplicateItem {}
    @C_impl(1)
    public static class DuplicateA implements DuplicateItem {}
    @C_impl(1)
    public static class DuplicateB implements DuplicateItem {}
    public static class DuplicateContainer {
        @F_bean(implClassExpr = "1")
        public DuplicateItem item;
    }
    public interface DefaultItem {}
    @C_impl(C_impl.Default)
    public static class DefaultA implements DefaultItem {}
    @C_impl(C_impl.Default)
    public static class DefaultB implements DefaultItem {}
    public static class DefaultContainer {
        @F_bean(implClassExpr = "1")
        public DefaultItem item;
    }
    public interface RepeatedValueItem {}
    @C_impl({1, 1})
    public static class RepeatedValueImpl implements RepeatedValueItem {}
    public static class RepeatedValueContainer {
        @F_bean(implClassExpr = "1")
        public RepeatedValueItem item;
    }
    public interface ExplicitItem {}
    @C_impl(1)
    public static class ExplicitParent implements ExplicitItem {
        @F_num(type = NumType.uint8)
        public int value;
    }
    @C_impl(2)
    public static class ExplicitChild extends ExplicitParent {
        @F_num(type = NumType.uint8)
        public int extra;
    }
    public static class ExplicitContainer {
        @F_num(type = NumType.uint8, var = 'a')
        public int kind;
        @F_bean(implClassExpr = "a")
        public ExplicitItem item;
    }
}
