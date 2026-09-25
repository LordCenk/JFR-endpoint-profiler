package dev.shashank.profiler.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint that is slow because of naive, allocation-heavy manual JSON
 * building - the profiler should show most self time inside
 * {@link #toJson} rather than in building the order list itself.
 */
@RestController
public class SlowSerializationController {

    private record Order(long id, String customer, String item, double price, int quantity) {
    }

    @GetMapping("/serialize/orders")
    public String ordersEndpoint(@RequestParam(value = "count", defaultValue = "3000") int count) {
        List<Order> orders = buildOrders(count);
        return toJson(orders);
    }

    private List<Order> buildOrders(int count) {
        List<Order> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            orders.add(new Order(i, "customer-" + (i % 500), "item-" + (i % 200), 9.99 + (i % 100), 1 + (i % 5)));
        }
        return orders;
    }

    // Deliberately naive: string concatenation with "+" in a loop instead of a
    // StringBuilder or a real JSON library, so every append reallocates and
    // copies the whole buffer built so far.
    private String toJson(List<Order> orders) {
        String json = "[";
        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            json = json + "{\"id\":" + o.id()
                    + ",\"customer\":\"" + escapeSlowly(o.customer())
                    + "\",\"item\":\"" + escapeSlowly(o.item())
                    + "\",\"price\":" + o.price()
                    + ",\"quantity\":" + o.quantity() + "}";
            if (i < orders.size() - 1) {
                json = json + ",";
            }
        }
        json = json + "]";
        return json;
    }

    private String escapeSlowly(String value) {
        String escaped = "";
        for (char c : value.toCharArray()) {
            escaped = escaped + (c == '"' ? "\\\"" : String.valueOf(c));
        }
        return escaped;
    }
}
