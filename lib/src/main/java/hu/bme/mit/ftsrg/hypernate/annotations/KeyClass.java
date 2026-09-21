/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a key provider for another class.
 *
 * <p>If you have an existing asset class <code>Foo</code> whose code you do not want to touch, but
 * you still want to use it as a Hypernate entity class, you can create a <i>key class</i> for it
 * (eg <code>FooKey</code>), where you repeat those fields of <code>Foo</code> that you want to be
 * treated as primary keys for <code>Foo</code>. You must use the {@link KeyOrder} annotation on all
 * fields with a number to ensure the key fields will be used in a defined order.
 *
 * <p>For example:
 *
 * <pre>{@code
 * public class Warehouse {
 *   String locationCode;
 *   int localId;
 *   int capacity;
 *   // ...
 * }
 *
 * @KeyClass(Warehouse.class)
 * public record WarehouseKey(@KeyOrder(1) String locationCode, @KeyOrder(2) int localId) {}
 * }</pre>
 *
 * <p>Every field of the key class must name a field that exists on the target entity, and must
 * carry a {@link KeyOrder} whose value is distinct within the key class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface KeyClass {
  Class<?> value();
}
