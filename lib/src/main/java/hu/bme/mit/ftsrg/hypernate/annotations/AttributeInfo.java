/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes an attribute to be used as a key or an index to be used with {@link PrimaryKey} or
 * {@link QueryIndex}.
 *
 * <p>This annotation should be used when passing parameters to {@link PrimaryKey} or {@link
 * QueryIndex}. You must specify a valid class field's name as the {@code name} parameter (using
 * {@link lombok.experimental.FieldNameConstants @FieldNameConstants} from <a
 * href='https://projectlombok.org/lombok'>lombok</a> is recommended). Furthermore, you can specify
 * an {@link AttributeMapper} class to use which is {@link ObjectToString} by default.
 *
 * <p>Usage example:
 *
 * <blockquote>
 *
 * <pre>{@code
 * @PrimaryKey(@AttributeInfo(name = "id", mapper = IntegerZeroPadder.class))
 * public record MyAsset(@Property Integer id, @Property String foo);
 * }</pre>
 *
 * </blockquote>
 *
 * @see PrimaryKey
 * @see QueryIndex
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AttributeInfo {
  /**
   * Which class field contains the value of this attribute.
   *
   * <p>Using {@link lombok.experimental.FieldNameConstants @FieldNameConstants} from <a
   * href='https://projectlombok.org/lombok'>lombok</a> is recommended.
   *
   * @return the name of the class field that contains the value of this attribute
   */
  String name();

  /**
   * The value mapper to use for this attribute.
   *
   * <p>This has an effect on how the attribute is persisted on the ledger level. By default, {@link
   * ObjectToString toString()} is used but a number of useful predefined mappers is available (eg,
   * {@link hu.bme.mit.ftsrg.hypernate.mappers.IntegerZeroPadder IntegerZeroPadder}). You can also
   * roll your own by implementing {@link AttributeMapper}.
   *
   * @return the mapper to use for this attribute
   * @see AttributeMapper
   * @see ObjectToString
   */
  Class<? extends AttributeMapper> mapper() default ObjectToString.class;
}
