/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DefaultValue {
  String value();

  Class<? extends DefaultValueProvider<?>> provider() default None.class;

  int since();

  interface None extends DefaultValueProvider<Object> {
    @Override
    default Object get() {
      return null;
    }
  }
}
