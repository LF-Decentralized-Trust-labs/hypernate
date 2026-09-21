/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One attribute of a {@link PrimaryKey}.
 *
 * <p>Only ever appears nested inside {@code @PrimaryKey}, hence the empty {@code @Target}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AttributeInfo {
  String name();

  Class<? extends AttributeMapper> mapper() default ObjectToString.class;
}
