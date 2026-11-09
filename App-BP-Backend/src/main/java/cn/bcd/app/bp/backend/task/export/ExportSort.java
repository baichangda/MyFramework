package cn.bcd.app.bp.backend.task.export;

import org.springframework.data.domain.Sort;

import java.util.List;

/** 导出专用的排序参数，由 Jackson 按普通 DTO 处理。 */
public record ExportSort(List<Order> orders) {
    public ExportSort {
        orders = List.copyOf(orders);
    }

    public static ExportSort fromSort(Sort sort) {
        List<Order> orders = sort == null ? List.of() : sort.stream()
                .map(order -> new Order(order.getProperty(), order.getDirection(),
                        order.isIgnoreCase(), order.getNullHandling()))
                .toList();
        return new ExportSort(orders);
    }

    public Sort toSort() {
        return Sort.by(orders.stream().map(order -> {
            Sort.Order value = new Sort.Order(order.direction(), order.property(), order.nullHandling());
            return order.ignoreCase() ? value.ignoreCase() : value;
        }).toList());
    }

    public record Order(String property, Sort.Direction direction,
                        boolean ignoreCase, Sort.NullHandling nullHandling) {
        public Order {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("Sort property must not be blank");
            }
            direction = direction == null ? Sort.Direction.ASC : direction;
            nullHandling = nullHandling == null ? Sort.NullHandling.NATIVE : nullHandling;
        }
    }
}
